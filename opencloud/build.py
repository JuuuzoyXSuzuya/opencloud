"""Build an OpenCloud-patched TikTok apk."""

import argparse
import os
import re
import shutil
import sys
import tempfile
import zipfile
from pathlib import Path

from . import __version__, axml, catalog, patcher, toolchain

ROOT = Path(__file__).resolve().parent.parent
SETTINGS_CLASS = "dev.opencloud.region.SettingsActivity"
SETTINGS_LABEL = "OpenCloud"
MANIFEST = "AndroidManifest.xml"
PROFILE_NAMES = ("assets/dexopt/baseline.prof", "assets/dexopt/baseline.profm")
SIGNATURE_SUFFIXES = (".SF", ".RSA", ".DSA", ".EC")


class BuildError(RuntimeError):
    pass


def _note(message: str) -> None:
    print("[opencloud] %s" % message)


def _manifest_facts(data: bytes) -> tuple[axml.Document, str, int]:
    document = axml.parse(data)
    package = ""
    for attribute in document.root.attributes:
        if (attribute.namespace == 0xFFFFFFFF
                and document.strings[attribute.name] == "package"):
            value = attribute.as_string(document.strings)
            if value:
                package = value
            break
    minimum = 23
    for child in document.root.children:
        if document.strings[child.name] == "uses-sdk":
            minimum = _integer(child, document, "minSdkVersion", 0x0101020C, minimum)
            break
    return document, package, minimum


def _integer(element: axml.Element, document: axml.Document, name: str,
             resource: int, fallback: int) -> int:
    for attribute in element.attributes:
        if document.resource_ids.get(attribute.name) == resource:
            if attribute.value_type in (0x10, 0x11):
                return attribute.data
            text = attribute.as_string(document.strings)
            if text and text.isdigit():
                return int(text)
        elif document.strings[attribute.name] == name:
            text = attribute.as_string(document.strings)
            if text and text.isdigit():
                return int(text)
    return fallback


def _next_dex(names: set[str]) -> str:
    highest = 0
    for name in names:
        found = re.fullmatch(r"classes(\d*)\.dex", name)
        if found:
            highest = max(highest, int(found.group(1) or "1"))
    return "classes.dex" if highest == 0 else "classes%d.dex" % (highest + 1)


def _is_signature(name: str) -> bool:
    folded = name.upper()
    return (folded == "META-INF/MANIFEST.MF"
            or (folded.startswith("META-INF/") and folded.endswith(SIGNATURE_SUFFIXES)))


def _copy_info(source: zipfile.ZipInfo, compress: int) -> zipfile.ZipInfo:
    info = zipfile.ZipInfo(source.filename, source.date_time)
    info.compress_type = compress
    info.external_attr = source.external_attr
    info.create_system = source.create_system
    info.comment = source.comment
    return info


def _trim_input(source: Path, target: Path) -> None:
    """Copy only the manifest, resource table and dex files of an apk.

    TikTok ships hundreds of megabytes of native libraries and assets.  They
    are copied into the output unchanged, so apktool does not need to unpack
    them at all.
    """
    with zipfile.ZipFile(source) as archive, zipfile.ZipFile(target, "w") as out:
        for info in archive.infolist():
            name = info.filename
            if name == MANIFEST or name == "resources.arsc" or re.fullmatch(r"classes\d*\.dex", name):
                compress = zipfile.ZIP_STORED if name == "resources.arsc" else info.compress_type
                out.writestr(_copy_info(info, compress), archive.read(info))


def _dex_name(directory: str) -> str:
    if directory == "smali":
        return "classes.dex"
    return directory.replace("smali_classes", "classes", 1) + ".dex"


def assemble(source: Path, rebuilt: Path, manifest: bytes | None,
             mod_dex: Path, target: Path) -> None:
    """Overlay the rebuilt dex files and manifest onto the original apk."""
    with zipfile.ZipFile(source) as original, zipfile.ZipFile(rebuilt) as made:
        patched = {name: made.read(name) for name in made.namelist()
                   if re.fullmatch(r"classes\d*\.dex", name)}
        replaced = len(patched)
        names = set(original.namelist()) | set(patched)
        dex_name = _next_dex(names)
        if dex_name in original.namelist():
            raise BuildError("refusing to overwrite %s" % dex_name)
        manifest_info = original.getinfo(MANIFEST)
        with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as out:
            for info in original.infolist():
                name = info.filename
                if info.is_dir() or _is_signature(name) or name in PROFILE_NAMES:
                    continue
                if name == MANIFEST and manifest is not None:
                    continue
                data = original.read(info)
                compress = zipfile.ZIP_STORED if name == "resources.arsc" else info.compress_type
                if name in patched:
                    data = patched.pop(name)
                    compress = zipfile.ZIP_DEFLATED
                out.writestr(_copy_info(info, compress), data)
            for name, data in patched.items():
                info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                out.writestr(info, data)
            if manifest is not None:
                out.writestr(_copy_info(manifest_info, zipfile.ZIP_DEFLATED), manifest)
            info = zipfile.ZipInfo(dex_name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            out.writestr(info, mod_dex.read_bytes())
    _note("added %s, replaced %d dex files" % (dex_name, replaced))


def _compile_mod(sdk: toolchain.Sdk, jdk: toolchain.Jdk, work: Path,
                 default_country: str, minimum: int) -> Path:
    generated = work / "generated"
    catalog.generate(ROOT / "data" / "countries.tsv", generated, default_country)
    classes = work / "classes"
    classes.mkdir()
    sources = sorted((ROOT / "java").rglob("*.java")) + sorted(generated.rglob("*.java"))
    if not sources:
        raise BuildError("no Java sources to compile")
    command = [
        str(jdk.javac),
        "-encoding", "UTF-8",
        "-source", "8",
        "-target", "8",
        "-Xlint:-options",
        "-classpath", str(sdk.android_jar),
        "-d", str(classes),
    ] + [str(source) for source in sources]
    toolchain.run(command)

    dex_out = work / "mod-dex"
    dex_out.mkdir()
    class_files = sorted(classes.rglob("*.class"))
    toolchain.run([
        str(sdk.d8),
        "--min-api", str(max(minimum, 1)),
        "--lib", str(sdk.android_jar),
        "--output", str(dex_out),
    ] + [str(path) for path in class_files])
    dex = dex_out / "classes.dex"
    if not dex.is_file():
        raise BuildError("d8 did not produce classes.dex")
    return dex


def _sign(sdk: toolchain.Sdk, jdk: toolchain.Jdk, aligned: Path, target: Path) -> None:
    keystore = Path.home() / ".config" / "opencloud" / "opencloud.keystore"
    keystore.parent.mkdir(parents=True, exist_ok=True)
    if not keystore.is_file():
        _note("creating a local signing key at %s" % keystore)
        toolchain.run([
            str(jdk.keytool), "-genkeypair", "-keystore", str(keystore),
            "-alias", "opencloud", "-keyalg", "RSA", "-keysize", "2048",
            "-validity", "10000", "-storepass", "opencloud",
            "-keypass", "opencloud",
            "-dname", "CN=OpenCloud, O=OpenCloud, C=US",
        ])
    toolchain.run([
        str(sdk.apksigner), "sign",
        "--ks", str(keystore),
        "--ks-key-alias", "opencloud",
        "--ks-pass", "pass:opencloud",
        "--key-pass", "pass:opencloud",
        "--out", str(target), str(aligned),
    ])
    toolchain.run([str(sdk.apksigner), "verify", "--verbose", str(target)])


def build(args: argparse.Namespace) -> Path:
    source = Path(args.apk).expanduser().resolve()
    if not source.is_file():
        raise BuildError("input apk not found: %s" % source)
    if source.suffix.lower() != ".apk":
        raise BuildError("input must be an .apk file")

    target = (Path(args.out).expanduser().resolve()
              if args.out else source.with_name(source.stem + "-opencloud.apk"))
    if target == source:
        raise BuildError("output would overwrite the input apk")

    jdk = toolchain.find_jdk()
    sdk = toolchain.find_sdk()
    apktool = toolchain.ensure_apktool(ROOT / "tools")

    work = Path(tempfile.mkdtemp(prefix="opencloud-"))
    try:
        with zipfile.ZipFile(source) as archive:
            try:
                raw_manifest = archive.read(MANIFEST)
            except KeyError as error:
                raise BuildError("apk has no AndroidManifest.xml") from error
        document, package, minimum = _manifest_facts(raw_manifest)
        if not package:
            raise BuildError("manifest has no package name")
        _note("input %s (package %s, minSdk %d)" % (source.name, package, minimum))

        patched_manifest = None
        if not args.no_settings:
            axml.add_activity(document, SETTINGS_CLASS, SETTINGS_LABEL)
            patched_manifest = axml.serialize(document)
            _note("settings activity added to the manifest")

        trimmed = work / "trimmed.apk"
        _trim_input(source, trimmed)
        decoded = work / "decoded"
        _note("disassembling sources")
        toolchain.run([
            str(jdk.java), "-jar", str(apktool), "d", "-r", "-f",
            "-j", str(args.jobs), str(trimmed), "-o", str(decoded),
        ])
        trimmed.unlink(missing_ok=True)

        _note("rewriting telephony call sites")
        result = patcher.rewrite(decoded, strict=args.strict,
                                 allow_missing=args.allow_missing)
        for key in sorted(result.counts):
            print("  %4d  %s" % (result.counts[key], key))
        _note("%d call sites in %d files" % (result.total, result.files))
        for name in result.override_names:
            print("   method override  %s" % name)

        # Only the dex files that actually changed have to be assembled; every
        # other dex file is copied from the original apk byte for byte.
        keep_dirs = set(result.directories)
        for directory in sorted(decoded.iterdir()):
            if directory.is_dir() and directory.name.startswith("smali") and directory.name not in keep_dirs:
                shutil.rmtree(directory, ignore_errors=True)
        if not keep_dirs:
            raise BuildError("the patcher reported no changed dex directories")
        _note("assembling %d changed dex files" % len(keep_dirs))

        _note("compiling the mod")
        mod_dex = _compile_mod(sdk, jdk, work, args.country, minimum)

        _note("reassembling the apk")
        rebuilt = work / "rebuilt.apk"
        toolchain.run([
            str(jdk.java), "-jar", str(apktool), "b",
            "-j", str(args.jobs), str(decoded), "-o", str(rebuilt),
        ])
        if not args.keep:
            shutil.rmtree(decoded, ignore_errors=True)

        unsigned = work / "unsigned.apk"
        assemble(source, rebuilt, patched_manifest, mod_dex, unsigned)
        if not args.keep:
            rebuilt.unlink(missing_ok=True)
            mod_dex.unlink(missing_ok=True)
        _note("aligning")
        aligned = work / "aligned.apk"
        toolchain.run([str(sdk.zipalign), "-f", "-p", "4", str(unsigned), str(aligned)])
        if not args.keep:
            unsigned.unlink(missing_ok=True)
        if args.no_sign:
            shutil.copyfile(aligned, target)
            _note("unsigned output: %s" % target)
        else:
            _note("signing")
            _sign(sdk, jdk, aligned, target)
            _note("signed output: %s" % target)
        if not args.keep:
            aligned.unlink(missing_ok=True)
        return target
    finally:
        if args.keep:
            _note("working directory kept at %s" % work)
        else:
            shutil.rmtree(work, ignore_errors=True)


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(
        prog="opencloud",
        description="Patch a universal TikTok apk with the OpenCloud region profile.")
    result.add_argument("--version", action="version",
                        version="opencloud %s" % __version__)
    result.add_argument("apk", help="universal TikTok apk (arm64-v8a, nodpi)")
    result.add_argument("-o", "--out", help="output apk path")
    result.add_argument("--country", default="nl",
                        help="default country code from data/countries.tsv (default: nl)")
    result.add_argument("--strict", action="store_true",
                        help="fail when any optional hook has no call site")
    result.add_argument("--allow-missing", action="store_true",
                        help="do not fail when a required hook has no call site")
    result.add_argument("--no-settings", action="store_true",
                        help="do not add the OpenCloud settings activity")
    result.add_argument("--no-sign", action="store_true",
                        help="skip apksigner signing (the output is not installable)")
    result.add_argument("--jobs", type=int, default=max(1, os.cpu_count() or 1),
                        help="apktool worker threads")
    result.add_argument("--keep", action="store_true", help="keep the temporary work directory")
    return result


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    try:
        build(args)
    except (BuildError, patcher.PatchError, catalog.CatalogError,
            toolchain.ToolError, axml.AxmlError) as error:
        sys.stderr.write("opencloud: %s\n" % error)
        return 1
    return 0
