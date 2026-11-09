"""Validation and Java code generation for the built-in country table."""

import re
from dataclasses import dataclass
from pathlib import Path


class CatalogError(ValueError):
    pass


@dataclass(frozen=True)
class Country:
    iso: str
    name: str
    numeric: str
    carrier: str


_ISO = re.compile(r"^[a-z]{2}$")
_NUMERIC = re.compile(r"^[0-9]{5,6}$")


def load(path: Path) -> list[Country]:
    if not path.is_file():
        raise CatalogError("country table not found: %s" % path)
    rows: list[Country] = []
    seen_numeric: set[str] = set()
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) != 4:
            raise CatalogError("%s:%d: expected four tab-separated fields" % (path, number))
        iso, name, numeric, carrier = (part.strip() for part in parts)
        if not _ISO.match(iso):
            raise CatalogError("%s:%d: bad ISO code %r" % (path, number, iso))
        if not name:
            raise CatalogError("%s:%d: empty country name" % (path, number))
        if not _NUMERIC.match(numeric):
            raise CatalogError("%s:%d: bad MCC/MNC %r" % (path, number, numeric))
        if not carrier:
            raise CatalogError("%s:%d: empty operator name" % (path, number))
        # A country may list several operators.  The MCC/MNC pair stays unique
        # because it identifies one network.
        if numeric in seen_numeric:
            raise CatalogError("%s:%d: duplicate MCC/MNC %r" % (path, number, numeric))
        seen_numeric.add(numeric)
        rows.append(Country(iso, name, numeric, carrier))
    if not rows:
        raise CatalogError("country table is empty")
    return rows


def _java(text: str) -> str:
    out = ['"']
    for char in text:
        if char == "\\":
            out.append("\\\\")
        elif char == '"':
            out.append('\\"')
        elif char == "\n":
            out.append("\\n")
        elif char == "\r":
            out.append("\\r")
        elif char == "\t":
            out.append("\\t")
        elif 0x20 <= ord(char) < 0x7F:
            out.append(char)
        else:
            out.append("\\u%04x" % ord(char))
    out.append('"')
    return "".join(out)


def java_source(rows: list[Country], default_iso: str) -> str:
    if not any(row.iso == default_iso for row in rows):
        raise CatalogError("default country %r is not in the table" % default_iso)
    lines = [
        "package dev.opencloud.region;",
        "",
        "import java.util.ArrayList;",
        "import java.util.LinkedHashMap;",
        "import java.util.List;",
        "import java.util.Locale;",
        "",
        "/** Generated from data/countries.tsv; do not edit by hand. */",
        "final class Atlas {",
        "    static final String DEFAULT_ISO = %s;" % _java(default_iso),
        "    private static final String[][] ROWS = {",
    ]
    for row in rows:
        lines.append("        {%s, %s, %s, %s},"
                     % (_java(row.iso), _java(row.name), _java(row.numeric), _java(row.carrier)))
    lines.extend([
        "    };",
        "    private static final LinkedHashMap<String, Profile> BY_KEY =",
        "            new LinkedHashMap<String, Profile>();",
        "    private static final LinkedHashMap<String, ArrayList<Profile>> BY_ISO =",
        "            new LinkedHashMap<String, ArrayList<Profile>>();",
        "    static {",
        "        for (String[] row : ROWS) {",
        "            Profile profile = new Profile(row[0], row[2], row[3], row[1]);",
        "            BY_KEY.put(row[0] + \":\" + row[2], profile);",
        "            ArrayList<Profile> list = BY_ISO.get(row[0]);",
        "            if (list == null) {",
        "                list = new ArrayList<Profile>();",
        "                BY_ISO.put(row[0], list);",
        "            }",
        "            list.add(profile);",
        "        }",
        "    }",
        "",
        "    private Atlas() {",
        "    }",
        "",
        "    static Profile find(String iso) {",
        "        if (iso == null) {",
        "            return null;",
        "        }",
        "        ArrayList<Profile> list = BY_ISO.get(iso.toLowerCase(Locale.ROOT));",
        "        return list == null || list.isEmpty() ? null : list.get(0);",
        "    }",
        "",
        "    static Profile find(String iso, String numeric) {",
        "        if (iso == null) {",
        "            return null;",
        "        }",
        "        String key = iso.toLowerCase(Locale.ROOT) + \":\" + (numeric == null ? \"\" : numeric);",
        "        Profile found = BY_KEY.get(key);",
        "        return found == null ? find(iso) : found;",
        "    }",
        "",
        "    static List<Profile> profilesFor(String iso) {",
        "        if (iso == null) {",
        "            return new ArrayList<Profile>();",
        "        }",
        "        ArrayList<Profile> list = BY_ISO.get(iso.toLowerCase(Locale.ROOT));",
        "        return list == null ? new ArrayList<Profile>() : new ArrayList<Profile>(list);",
        "    }",
        "",
        "    static List<Profile> countries() {",
        "        ArrayList<Profile> out = new ArrayList<Profile>();",
        "        for (ArrayList<Profile> list : BY_ISO.values()) {",
        "            if (!list.isEmpty()) {",
        "                out.add(list.get(0));",
        "            }",
        "        }",
        "        return out;",
        "    }",
        "",
        "    static Profile defaultProfile() {",
        "        Profile found = find(DEFAULT_ISO);",
        "        if (found == null && !BY_KEY.isEmpty()) {",
        "            found = BY_KEY.values().iterator().next();",
        "        }",
        "        return found;",
        "    }",
        "",
        "    static List<Profile> all() {",
        "        return new ArrayList<Profile>(BY_KEY.values());",
        "    }",
        "}",
        "",
    ])
    return "\n".join(lines)


def generate(tsv: Path, out_dir: Path, default_iso: str) -> list[Country]:
    rows = load(tsv)
    target = out_dir / "dev" / "opencloud" / "region" / "Atlas.java"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(java_source(rows, default_iso), encoding="utf-8")
    return rows
