"""Small reader/writer for Android's binary XML container.

Only the pieces a manifest needs are implemented: string pools, the resource
map, namespaces, elements and their typed attributes.  Anything unusual is
rejected instead of being silently dropped, so a build can never produce a
manifest that differs from the one we thought we wrote.
"""

import struct
from dataclasses import dataclass, field
from typing import Iterable


class AxmlError(ValueError):
    pass


# ResChunk_header.type values used by binary XML.
_CHUNK_XML = 0x0003
_CHUNK_STRING_POOL = 0x0001
_CHUNK_RESOURCE_MAP = 0x0180
_CHUNK_START_NAMESPACE = 0x0100
_CHUNK_END_NAMESPACE = 0x0101
_CHUNK_START_ELEMENT = 0x0102
_CHUNK_END_ELEMENT = 0x0103
_CHUNK_CDATA = 0x0104

# Res_value.dataType values.
_TYPE_REFERENCE = 0x01
_TYPE_STRING = 0x03
_TYPE_INT_DEC = 0x10
_TYPE_INT_HEX = 0x11
_TYPE_INT_BOOLEAN = 0x12

_NO_INDEX = 0xFFFFFFFF

# Android attribute resource ids that OpenCloud needs to add to a manifest.
_NAME_ID = 0x01010003
_LABEL_ID = 0x01010001
_EXPORTED_ID = 0x01010010
_ATTR_RESOURCE_IDS = {"name": _NAME_ID, "label": _LABEL_ID, "exported": _EXPORTED_ID}

_ANDROID_NS = "http://schemas.android.com/apk/res/android"


@dataclass
class XmlAttribute:
    namespace: int
    name: int
    raw: int
    value_type: int
    data: int

    def as_string(self, strings: list[str]) -> str | None:
        if self.value_type == _TYPE_STRING and self.data < len(strings):
            return strings[self.data]
        if self.raw != _NO_INDEX and self.raw < len(strings):
            return strings[self.raw]
        return None


@dataclass
class Element:
    name: int
    attributes: list[XmlAttribute] = field(default_factory=list)
    children: list["Element"] = field(default_factory=list)
    line: int = 0

    def attr_string(self, strings: list[str], name: str,
                    resource_ids: dict[int, int]) -> str | None:
        wanted = _ATTR_RESOURCE_IDS.get(name)
        for field in self.attributes:
            if wanted is not None and resource_ids.get(field.name) == wanted:
                return field.as_string(strings)
            if field.name < len(strings) and strings[field.name] == name:
                return field.as_string(strings)
        return None


@dataclass
class Document:
    strings: list[str]
    resource_ids: dict[int, int]
    namespace_starts: list[tuple[int, int, int]]
    namespace_ends: list[tuple[int, int, int]]
    root: Element
    utf8: bool = True
    styles: bytes = b""
    style_count: int = 0


# ---------------------------------------------------------------- reading


def _u16(data: bytes, at: int) -> int:
    return struct.unpack_from("<H", data, at)[0]


def _u32(data: bytes, at: int) -> int:
    return struct.unpack_from("<I", data, at)[0]


def _read_len8(data: bytes, at: int) -> tuple[int, int]:
    first = data[at]
    if first & 0x80:
        return ((first & 0x7F) | (data[at + 1] << 7), at + 2)
    return first, at + 1


def _read_len16(data: bytes, at: int) -> tuple[int, int]:
    first = _u16(data, at)
    if first & 0x8000:
        return (((first & 0x7FFF) << 16) | _u16(data, at + 2), at + 4)
    return first, at + 2


def _parse_string_pool(data: bytes, start: int, size: int) -> tuple[list[str], bool, int, bytes, int]:
    if size < 28:
        raise AxmlError("string pool chunk is too short")
    count = _u32(data, start + 8)
    style_count = _u32(data, start + 12)
    flags = _u32(data, start + 16)
    strings_at = _u32(data, start + 20)
    styles_at = _u32(data, start + 24)
    if start + strings_at > len(data):
        raise AxmlError("string data lies outside the file")
    utf8 = bool(flags & 0x0100)
    limits = start + size
    offsets_at = start + 28
    if offsets_at + count * 4 > limits:
        raise AxmlError("string offset table is truncated")
    offsets = [_u32(data, offsets_at + i * 4) for i in range(count)]
    out: list[str] = []
    for off in offsets:
        pos = start + strings_at + off
        if pos >= limits:
            raise AxmlError("string offset points outside the pool")
        if utf8:
            _, pos = _read_len8(data, pos)
            byte_len, pos = _read_len8(data, pos)
            raw = data[pos:pos + byte_len]
            if len(raw) != byte_len:
                raise AxmlError("utf-8 string is truncated")
            out.append(raw.decode("utf-8", "strict"))
        else:
            char_len, pos = _read_len16(data, pos)
            raw = data[pos:pos + char_len * 2]
            if len(raw) != char_len * 2:
                raise AxmlError("utf-16 string is truncated")
            out.append(raw.decode("utf-16-le", "strict"))
    styles = b""
    if style_count:
        styles_start = start + styles_at
        if styles_start > limits:
            raise AxmlError("style data lies outside the pool")
        styles = data[styles_start:limits]
    return out, utf8, style_count, styles, flags


def _parse_attributes(data: bytes, at: int, count: int) -> list[XmlAttribute]:
    attrs: list[XmlAttribute] = []
    for i in range(count):
        base = at + i * 20
        ns, name, raw = struct.unpack_from("<III", data, base)
        ts, res0, dtype, value = struct.unpack_from("<HBBI", data, base + 12)
        if ts != 8 or res0 != 0:
            raise AxmlError("unexpected Res_value size in attribute")
        attrs.append(XmlAttribute(ns, name, raw, dtype, value))
    return attrs


def parse(data: bytes) -> Document:
    if len(data) < 8:
        raise AxmlError("file is too small to be binary XML")
    ctype, header_size, total = struct.unpack_from("<HHI", data, 0)
    if ctype != _CHUNK_XML or header_size != 8:
        raise AxmlError("not an Android binary XML document")
    if total != len(data):
        raise AxmlError("document size does not match the file size")

    strings: list[str] = []
    utf8 = True
    style_count = 0
    styles = b""
    flags = 0
    resource_ids: dict[int, int] = {}
    ns_starts: list[tuple[int, int, int]] = []
    ns_ends: list[tuple[int, int, int]] = []
    root: Element | None = None
    stack: list[Element] = []
    pos = header_size

    while pos < total:
        if pos + 8 > total:
            raise AxmlError("truncated chunk header")
        ctype, csize, cdata = struct.unpack_from("<HHI", data, pos)
        if csize < 8 or pos + csize > total:
            raise AxmlError("chunk size is invalid")

        if ctype == _CHUNK_STRING_POOL:
            strings, utf8, style_count, styles, flags = _parse_string_pool(data, pos, cdata)
        elif ctype == _CHUNK_RESOURCE_MAP:
            count = (cdata - csize) // 4
            for i in range(count):
                value = _u32(data, pos + csize + i * 4)
                if value:
                    resource_ids[i] = value
        elif ctype == _CHUNK_START_NAMESPACE:
            if cdata < 24:
                raise AxmlError("namespace chunk is too short")
            line, _comment, prefix, uri = struct.unpack_from("<IIII", data, pos + 8)
            ns_starts.append((prefix, uri, line))
        elif ctype == _CHUNK_END_NAMESPACE:
            if cdata < 24:
                raise AxmlError("namespace chunk is too short")
            line, _comment, prefix, uri = struct.unpack_from("<IIII", data, pos + 8)
            ns_ends.append((prefix, uri, line))
        elif ctype == _CHUNK_START_ELEMENT:
            if cdata < 36:
                raise AxmlError("element chunk is too short")
            line, comment, ns, name = struct.unpack_from("<IIII", data, pos + 8)
            attr_start, attr_size, attr_count = struct.unpack_from("<HHH", data, pos + 24)
            if attr_size != 20:
                raise AxmlError("unexpected attribute size")
            attrs_at = pos + 16 + attr_start
            if attrs_at + attr_count * attr_size > pos + cdata:
                raise AxmlError("attribute table is truncated")
            element = Element(name=name, attributes=_parse_attributes(data, attrs_at, attr_count), line=line)
            if stack:
                stack[-1].children.append(element)
            elif root is None:
                root = element
            else:
                raise AxmlError("more than one root element")
            stack.append(element)
        elif ctype == _CHUNK_END_ELEMENT:
            if not stack:
                raise AxmlError("end element without a start element")
            stack.pop()
        elif ctype == _CHUNK_CDATA:
            raise AxmlError("manifest contains character data, which is not supported")
        else:
            raise AxmlError("unsupported binary XML chunk 0x%04x" % ctype)

        pos += cdata

    if root is None or stack:
        raise AxmlError("element tree is incomplete")
    if style_count:
        # Style spans are only meaningful for text.  Manifests do not use them.
        raise AxmlError("styled string pools are not supported")
    return Document(strings, resource_ids, ns_starts, ns_ends, root, utf8, styles, style_count)


# --------------------------------------------------------------- writing


def _put_len8(out: bytearray, value: int) -> None:
    if value > 0x7F:
        out.append((value & 0x7F) | 0x80)
        out.append(value >> 7)
    else:
        out.append(value)


def _put_len16(out: bytearray, value: int) -> None:
    if value > 0x7FFF:
        out.extend(struct.pack("<HH", 0x8000 | (value >> 16), value & 0xFFFF))
    else:
        out.extend(struct.pack("<H", value))


def _pool_bytes(strings: Iterable[str], utf8: bool) -> bytes:
    encoded: list[bytes] = []
    for text in strings:
        raw = text.encode("utf-8" if utf8 else "utf-16-le", "strict")
        units = len(text.encode("utf-16-le", "strict")) // 2
        entry = bytearray()
        if utf8:
            _put_len8(entry, units)
            _put_len8(entry, len(raw))
        else:
            _put_len16(entry, units)
        entry.extend(raw)
        entry.extend(b"\x00" if utf8 else b"\x00\x00")
        encoded.append(bytes(entry))

    offsets: list[int] = []
    blob = bytearray()
    for entry in encoded:
        offsets.append(len(blob))
        blob.extend(entry)
    while len(blob) % 4:
        blob.append(0)

    header = 28 + len(offsets) * 4
    strings_at = header
    size = header + len(blob)
    out = bytearray()
    out.extend(struct.pack("<HHI", _CHUNK_STRING_POOL, 28, size))
    out.extend(struct.pack("<IIIII", len(offsets), 0, 0x0100 if utf8 else 0, strings_at, 0))
    for offset in offsets:
        out.extend(struct.pack("<I", offset))
    out.extend(blob)
    return bytes(out)


def _chunk(ctype: int, header_size: int, body: bytes) -> bytes:
    return struct.pack("<HHI", ctype, header_size, 8 + len(body)) + body


def _namespace_chunk(ctype: int, prefix: int, uri: int, line: int) -> bytes:
    body = struct.pack("<IIII", line, _NO_INDEX, prefix, uri)
    return struct.pack("<HHI", ctype, 16, 8 + len(body)) + body


def _element_bytes(element: Element) -> bytes:
    attrs = bytearray()
    for attr in element.attributes:
        attrs.extend(struct.pack("<III", attr.namespace, attr.name, attr.raw))
        attrs.extend(struct.pack("<HBBI", 8, 0, attr.value_type, attr.data))
    body = struct.pack(
        "<IIIIHHHHHH",
        element.line,
        _NO_INDEX,
        _NO_INDEX,
        element.name,
        20,
        20,
        len(element.attributes),
        0,
        0,
        0,
    )
    start = struct.pack("<HHI", _CHUNK_START_ELEMENT, 16, 8 + len(body) + len(attrs)) + body + attrs
    end_body = struct.pack("<IIII", element.line, _NO_INDEX, _NO_INDEX, element.name)
    end = struct.pack("<HHI", _CHUNK_END_ELEMENT, 16, 8 + len(end_body)) + end_body
    return start + b"".join(_element_bytes(child) for child in element.children) + end


def serialize(doc: Document) -> bytes:
    # Existing string indexes must stay valid, so new strings are appended.
    strings = list(doc.strings)

    for element in _walk(doc.root):
        if element.name >= len(strings):
            raise AxmlError("element name index is out of range")
        for attr in element.attributes:
            if attr.name >= len(strings):
                raise AxmlError("attribute name index is out of range")
            if attr.namespace != _NO_INDEX and attr.namespace >= len(strings):
                raise AxmlError("attribute namespace index is out of range")
            if attr.value_type == _TYPE_STRING:
                if attr.data >= len(strings):
                    raise AxmlError("string value index is out of range")
            if attr.raw != _NO_INDEX and attr.raw >= len(strings):
                raise AxmlError("raw value index is out of range")

    # The caller may have appended elements with string indexes already in the
    # pool.  Nothing else needs rewriting.
    resource_map = dict(doc.resource_ids)
    highest = max(resource_map) if resource_map else -1
    map_values = [0] * (highest + 1)
    for idx, value in resource_map.items():
        map_values[idx] = value
    body = bytearray()
    for value in map_values:
        body.extend(struct.pack("<I", value))
    resource_chunk = struct.pack("<HHI", _CHUNK_RESOURCE_MAP, 8, 8 + len(body)) + body

    payload = bytearray()
    payload.extend(_pool_bytes(strings, doc.utf8))
    if map_values:
        payload.extend(resource_chunk)
    for prefix, uri, line in doc.namespace_starts:
        payload.extend(_namespace_chunk(_CHUNK_START_NAMESPACE, prefix, uri, line))
    payload.extend(_element_bytes(doc.root))
    for prefix, uri, line in doc.namespace_ends:
        payload.extend(_namespace_chunk(_CHUNK_END_NAMESPACE, prefix, uri, line))
    return struct.pack("<HHI", _CHUNK_XML, 8, 8 + len(payload)) + payload


def _walk(element: Element) -> Iterable[Element]:
    yield element
    for child in element.children:
        yield from _walk(child)


# --------------------------------------------------------------- helpers


def attr(element: Element, name: str, strings: list[str],
         resource_ids: dict[int, int]) -> str | None:
    return element.attr_string(strings, name, resource_ids)


def add_activity(doc: Document, class_name: str, label: str) -> None:
    """Add (or replace) a launcher activity in the application element."""
    _ensure_android_namespace(doc)
    application = next((e for e in doc.root.children
                        if doc.strings[e.name] == "application"), None)
    if application is None:
        raise AxmlError("manifest has no <application> element")

    # Rebuilding an already patched apk must not stack duplicate components.
    application.children = [
        child for child in application.children
        if not (doc.strings[child.name] == "activity"
                and attr(child, "name", doc.strings, doc.resource_ids) == class_name)
    ]

    ns_index = _android_namespace_index(doc)
    activity = Element(name=_intern(doc, "activity"))
    activity.attributes = [
        _string_attr(doc, ns_index, "name", class_name),
        _string_attr(doc, ns_index, "label", label),
        _bool_attr(doc, ns_index, "exported", True),
    ]
    intent = Element(name=_intern(doc, "intent-filter"))
    action = Element(name=_intern(doc, "action"))
    action.attributes = [_string_attr(doc, ns_index, "name", "android.intent.action.MAIN")]
    category = Element(name=_intern(doc, "category"))
    category.attributes = [_string_attr(doc, ns_index, "name",
                                        "android.intent.category.LAUNCHER")]
    intent.children = [action, category]
    activity.children = [intent]
    application.children.append(activity)


def _android_namespace_index(doc: Document) -> int:
    for prefix, uri, _line in doc.namespace_starts:
        if uri < len(doc.strings) and doc.strings[uri] == _ANDROID_NS:
            return uri
    raise AxmlError("manifest has no android namespace")


def _ensure_android_namespace(doc: Document) -> None:
    if any(uri < len(doc.strings) and doc.strings[uri] == _ANDROID_NS
           for _prefix, uri, _line in doc.namespace_starts):
        return
    prefix = _intern(doc, "android")
    uri = _intern(doc, _ANDROID_NS)
    doc.namespace_starts.insert(0, (prefix, uri, 0))
    doc.namespace_ends.append((prefix, uri, 0))


def _intern(doc: Document, text: str) -> int:
    for idx, value in enumerate(doc.strings):
        if value == text:
            return idx
    doc.strings.append(text)
    return len(doc.strings) - 1


def _string_attr(doc: Document, namespace: int, name: str, value: str) -> XmlAttribute:
    name_idx = _intern(doc, name)
    value_idx = _intern(doc, value)
    _register_attribute(doc, name_idx, name)
    return XmlAttribute(namespace, name_idx, value_idx, _TYPE_STRING, value_idx)


def _bool_attr(doc: Document, namespace: int, name: str, value: bool) -> XmlAttribute:
    name_idx = _intern(doc, name)
    _register_attribute(doc, name_idx, name)
    return XmlAttribute(namespace, name_idx, _NO_INDEX, _TYPE_INT_BOOLEAN,
                     0xFFFFFFFF if value else 0)


def _register_attribute(doc: Document, name_idx: int, name: str) -> None:
    known = _ATTR_RESOURCE_IDS.get(name)
    if known is not None and name_idx not in doc.resource_ids:
        doc.resource_ids[name_idx] = known
