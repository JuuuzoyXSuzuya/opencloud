"""Rewrite telephony call sites in decoded smali."""

import re
from dataclasses import dataclass
from pathlib import Path


class PatchError(RuntimeError):
    pass


PHONE_OWNER = "Landroid/telephony/TelephonyManager;"
SUBSCRIPTION = "Landroid/telephony/SubscriptionManager;"
SUBSCRIPTION_INFO = "Landroid/telephony/SubscriptionInfo;"
HOOKS = "Ldev/opencloud/region/Hooks;"


@dataclass(frozen=True)
class InstanceTarget:
    owner: str
    method: str
    signature: str
    hook: str
    required: bool = False

    @property
    def replacement(self) -> str:
        return "(" + self.owner + self.signature[1:]


@dataclass(frozen=True)
class StaticTarget:
    owner: str
    method: str
    signature: str
    hook: str
    required: bool = False

    @property
    def replacement(self) -> str:
        return self.signature


INSTANCE_TARGETS: tuple[InstanceTarget, ...] = (
    InstanceTarget(PHONE_OWNER, "getSimCountryIso", "()Ljava/lang/String;", "country", True),
    InstanceTarget(PHONE_OWNER, "getNetworkCountryIso", "()Ljava/lang/String;", "networkCountry", True),
    InstanceTarget(PHONE_OWNER, "getSimOperator", "()Ljava/lang/String;", "simOperator", True),
    InstanceTarget(PHONE_OWNER, "getNetworkOperator", "()Ljava/lang/String;", "networkOperator", True),
    InstanceTarget(PHONE_OWNER, "getSimOperatorName", "()Ljava/lang/String;", "simOperatorName", True),
    InstanceTarget(PHONE_OWNER, "getNetworkOperatorName", "()Ljava/lang/String;", "networkOperatorName", True),
    InstanceTarget(PHONE_OWNER, "getSimState", "()I", "simState"),
    InstanceTarget(PHONE_OWNER, "getSimState", "(I)I", "simStateForSlot"),
    InstanceTarget(PHONE_OWNER, "hasIccCard", "()Z", "iccPresent"),
    InstanceTarget(PHONE_OWNER, "isNetworkRoaming", "()Z", "roaming"),
    InstanceTarget(PHONE_OWNER, "getSimCarrierId", "()I", "carrierId"),
    InstanceTarget(PHONE_OWNER, "getPhoneCount", "()I", "phoneCount"),
    InstanceTarget(PHONE_OWNER, "getActiveModemCount", "()I", "modemCount"),
    InstanceTarget(PHONE_OWNER, "getPhoneType", "()I", "phoneType"),
    InstanceTarget(SUBSCRIPTION, "getActiveSubscriptionInfoCount", "()I", "activeSubscriptions"),
    InstanceTarget(SUBSCRIPTION_INFO, "getCountryIso", "()Ljava/lang/String;", "subscriptionCountry"),
)

STATIC_TARGETS: tuple[StaticTarget, ...] = (
    StaticTarget(SUBSCRIPTION, "getDefaultDataSubscriptionId", "()I", "defaultDataId"),
    StaticTarget(SUBSCRIPTION, "getDefaultVoiceSubscriptionId", "()I", "defaultVoiceId"),
    StaticTarget(SUBSCRIPTION, "getDefaultSmsSubscriptionId", "()I", "defaultSmsId"),
    StaticTarget(SUBSCRIPTION, "getActiveDataSubscriptionId", "()I", "activeDataId"),
)

# Font overrides.  These are kept in a separate tuple because the method name
# "create" is too common to be useful as a prefilter; the scanner looks for the
# Typeface owner instead and only then runs these patterns.
TYPEFACE_OWNER = "Landroid/graphics/Typeface;"
FONT_OWNER_MARKER = TYPEFACE_OWNER + "->"

FONT_TARGETS: tuple[StaticTarget, ...] = (
    StaticTarget(TYPEFACE_OWNER, "create",
                 "(Ljava/lang/String;I)Landroid/graphics/Typeface;", "typeface"),
    StaticTarget(TYPEFACE_OWNER, "create",
                 "(Landroid/graphics/Typeface;I)Landroid/graphics/Typeface;", "typeface"),
    StaticTarget(TYPEFACE_OWNER, "defaultFromStyle",
                 "(I)Landroid/graphics/Typeface;", "defaultTypeface"),
    StaticTarget(TYPEFACE_OWNER, "createFromAsset",
                 "(Landroid/content/res/AssetManager;Ljava/lang/String;)"
                 "Landroid/graphics/Typeface;", "typefaceFromAsset"),
    StaticTarget(TYPEFACE_OWNER, "createFromFile",
                 "(Ljava/lang/String;)Landroid/graphics/Typeface;", "typefaceFromFile"),
)


@dataclass(frozen=True)
class MethodOverride:
    """Replace a whole smali method body.

    Some behaviours are selected in a single branching method rather than at a
    call site, so rewriting the caller is not enough.  The override keeps the
    original descriptor and signature and only swaps the instructions.
    """

    descriptor: str
    method: str
    signature: str
    body: str
    required: bool = False


# TikTok ships two Google providers in the Lobby auth library: "google"
# (native Google Sign-In) and "google_web" (AppAuth + Custom Tabs).  The
# native one validates the signing certificate, so it fails on a repackaged
# APK.  The provider mapper (LX/1DZZ.LJFF on older releases, LX/16ry.LIZ on
# 47.7.x) is rewritten to answer "google_web" for "google", which sends the
# login through the browser, where the OAuth client is not bound to the APK
# signature.
GOOGLE_WEB_BODY = """\
.method public static final METHODNAME(Ljava/lang/String;)Ljava/lang/String;
    .locals 2

    const-string v0, "google"
    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-eqz v0, :cond_facebook
    const-string v0, "google_web"
    return-object v0

    :cond_facebook
    const-string v0, "facebook"
    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-eqz v0, :cond_line
    const-string v0, "facebook"
    return-object v0

    :cond_line
    const-string v0, "line"
    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-eqz v0, :cond_kakao
    const-string v0, "line"
    return-object v0

    :cond_kakao
    const-string v0, "kakaotalk"
    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-eqz v0, :cond_empty
    const-string v0, "kakaotalk"
    return-object v0

    :cond_empty
    const-string v0, ""
    return-object v0
.end method"""


def _google_web_body(method: str) -> str:
    return GOOGLE_WEB_BODY.replace("METHODNAME", method)

# LX/005C.LJJIII(int) is TikTok's central "apply appearance" entry point.
# Rather than replacing its body (whose private fields change between TikTok
# releases), the hook is prepended to the original method.  ThemePlugin.mode()
# returns the requested value unchanged when the plugin is off.
THEME_PREFIX = """\
    invoke-static {p0}, Ldev/opencloud/region/ThemePlugin;->mode(I)I
    move-result p0
"""


METHOD_OVERRIDES: tuple[MethodOverride, ...] = (
    MethodOverride("LX/1DZZ;", "LJFF",
                   "(Ljava/lang/String;)Ljava/lang/String;",
                   _google_web_body("LJFF")),
    MethodOverride("LX/16ry;", "LIZ",
                   "(Ljava/lang/String;)Ljava/lang/String;",
                   _google_web_body("LIZ")),
)


@dataclass(frozen=True)
class MethodPrefix:
    """Prepend instructions to the existing body of a known smali method.

    Used where the method body contains version-specific details: the original
    code stays intact and only the hook at the top is new.
    """

    descriptor: str
    method: str
    signature: str
    code: str
    label: str
    marker: str = ""
    required: bool = False


# TikTok renames its obfuscated appearance controller on almost every major
# release, so the same hook is offered for every name seen so far.  The marker
# check keeps a name clash from patching an unrelated method.
METHOD_PREFIXES: tuple[MethodPrefix, ...] = (
    MethodPrefix("LX/005C;", "LJJIII", "(I)V", THEME_PREFIX, "theme",
                 "sput p0, LX/", False),
    MethodPrefix("LX/004m;", "LJJIII", "(I)V", THEME_PREFIX, "theme",
                 "sput p0, LX/", False),
)


@dataclass(frozen=True)
class FileEdit:
    """Replace a snippet inside one method of one known smali class.

    The settings screen is a Compose fragment: its layout is built in code and
    cannot be extended by a call-site rewrite.  Wrapping the view it returns is
    the least invasive hook that still puts a real row into TikTok's own
    settings screen.
    """

    smali_path: str
    method: str
    signature: str
    old: str
    new: str
    label: str
    required: bool = False


SETTINGS_PATH = ("com/ss/android/ugc/aweme/setting/ui/rvmpcompose/"
                 "SettingsComposeRvmpFragment.smali")
SETTINGS_RETURN = "    return-object v5"
SETTINGS_WRAP = (
    "    invoke-static {v5, p0}, Ldev/opencloud/region/SettingsEntry;->"
    "decorate(Landroid/view/View;Ljava/lang/Object;)Landroid/view/View;\n"
    "    move-result-object v5\n"
    "    return-object v5"
)

FILE_EDITS: tuple[FileEdit, ...] = (
    FileEdit(
        SETTINGS_PATH,
        "onCreateView",
        "(Landroid/view/LayoutInflater;Landroid/view/ViewGroup;Landroid/os/Bundle;)"
        "Landroid/view/View;",
        SETTINGS_RETURN,
        SETTINGS_WRAP,
        "settings entry",
        required=False,
    ),
)


def _method_block(text: str, method: str, signature: str) -> re.Match[str] | None:
    pattern = re.compile(
        r"(?ms)^\.method[^\n]*\b"
        + re.escape(method)
        + re.escape(signature)
        + r"[ \t]*\r?\n.*?^\.end method[ \t]*\r?$"
    )
    return pattern.search(text)


def _method_pattern(override: MethodOverride) -> re.Pattern[str]:
    return re.compile(
        r"(?ms)^\.method[^\n]*\b"
        + re.escape(override.method)
        + re.escape(override.signature)
        + r"[ \t]*\r?\n.*?^\.end method[ \t]*\r?$"
    )


def _prefix_block(block: str, code: str) -> str:
    lines = block.splitlines(keepends=True)
    insert_at = 1
    for index in range(1, len(lines)):
        stripped = lines[index].strip()
        if not stripped or stripped.startswith("."):
            insert_at = index + 1
            continue
        break
    return "".join(lines[:insert_at]) + code + "".join(lines[insert_at:])


def _instance_pattern(target: InstanceTarget) -> re.Pattern[str]:
    return re.compile(
        r"(?m)^([ \t]*)invoke-virtual(/range)?([ \t]+\{[^}]*\},[ \t]*)"
        + re.escape(target.owner)
        + r"->"
        + re.escape(target.method)
        + re.escape(target.signature)
        + r"[ \t]*\r?$"
    )


def _static_pattern(target: StaticTarget) -> re.Pattern[str]:
    return re.compile(
        r"(?m)^([ \t]*)invoke-static(/range)?([ \t]+\{[^}]*\},[ \t]*)"
        + re.escape(target.owner)
        + r"->"
        + re.escape(target.method)
        + re.escape(target.signature)
        + r"[ \t]*\r?$"
    )


@dataclass
class Result:
    counts: dict[str, int]
    files: int
    directories: tuple[str, ...] = ()
    overrides: int = 0
    override_names: tuple[str, ...] = ()

    @property
    def total(self) -> int:
        return sum(self.counts.values())


def rewrite(root: Path, strict: bool = False, allow_missing: bool = False) -> Result:
    directories = sorted(p for p in root.iterdir()
                         if p.is_dir() and p.name.startswith("smali"))
    if not directories:
        raise PatchError("apktool produced no smali directories")
    names = {target.method for target in INSTANCE_TARGETS}
    names.update(target.method for target in STATIC_TARGETS)
    counts: dict[str, int] = {}
    changed = 0
    changed_dirs: set[str] = set()
    override_names: list[str] = []
    missing: list[str] = []

    for directory in directories:
        for path in directory.rglob("*.smali"):
            text = path.read_text(encoding="utf-8")
            has_phone = any(name in text for name in names)
            has_font = FONT_OWNER_MARKER in text
            if not has_phone and not has_font:
                continue
            previous = text
            if has_phone:
                for target in INSTANCE_TARGETS:
                    pattern = _instance_pattern(target)
                    replacement = r"\1invoke-static\2\3" + HOOKS + "->" + target.hook + target.replacement
                    text, hits = pattern.subn(replacement, text)
                    if hits:
                        key = "%s->%s%s" % (target.owner, target.method, target.signature)
                        counts[key] = counts.get(key, 0) + hits
                for target in STATIC_TARGETS:
                    pattern = _static_pattern(target)
                    replacement = r"\1invoke-static\2\3" + HOOKS + "->" + target.hook + target.replacement
                    text, hits = pattern.subn(replacement, text)
                    if hits:
                        key = "%s->%s%s" % (target.owner, target.method, target.signature)
                        counts[key] = counts.get(key, 0) + hits
            if has_font:
                for target in FONT_TARGETS:
                    pattern = _static_pattern(target)
                    replacement = r"\1invoke-static\2\3" + HOOKS + "->" + target.hook + target.replacement
                    text, hits = pattern.subn(replacement, text)
                    if hits:
                        key = "%s->%s%s" % (target.owner, target.method, target.signature)
                        counts[key] = counts.get(key, 0) + hits
            if text != previous:
                path.write_text(text, encoding="utf-8")
                changed += 1
                changed_dirs.add(directory.name)

    for override in METHOD_OVERRIDES:
        relative = override.descriptor[1:-1] + ".smali"
        applied = False
        for directory in directories:
            path = directory / relative
            if not path.is_file():
                continue
            text = path.read_text(encoding="utf-8")
            text, hits = _method_pattern(override).subn(override.body, text)
            if hits:
                path.write_text(text, encoding="utf-8")
                changed += 1
                changed_dirs.add(directory.name)
                override_names.append(override.descriptor + "->"
                                      + override.method + override.signature)
                applied = True
        if not applied and (override.required or strict):
            missing.append(override.descriptor + "->"
                           + override.method + override.signature)

    for prefix in METHOD_PREFIXES:
        relative = prefix.descriptor[1:-1] + ".smali"
        applied = False
        for directory in directories:
            path = directory / relative
            if not path.is_file():
                continue
            text = path.read_text(encoding="utf-8")
            match = _method_block(text, prefix.method, prefix.signature)
            if match is None:
                continue
            block = match.group(0)
            if prefix.marker and prefix.marker not in block:
                continue
            patched = _prefix_block(block, prefix.code)
            text = text[:match.start()] + patched + text[match.end():]
            path.write_text(text, encoding="utf-8")
            changed += 1
            changed_dirs.add(directory.name)
            counts[prefix.label] = counts.get(prefix.label, 0) + 1
            applied = True
        if not applied and (prefix.required or strict):
            missing.append(prefix.descriptor + "->"
                           + prefix.method + prefix.signature)

    for edit in FILE_EDITS:
        applied = False
        for directory in directories:
            path = directory / edit.smali_path
            if not path.is_file():
                continue
            text = path.read_text(encoding="utf-8")
            match = _method_block(text, edit.method, edit.signature)
            if match is None:
                continue
            block = match.group(0)
            hits = block.count(edit.old)
            if not hits:
                continue
            patched = block.replace(edit.old, edit.new)
            text = text[:match.start()] + patched + text[match.end():]
            path.write_text(text, encoding="utf-8")
            changed += 1
            changed_dirs.add(directory.name)
            counts[edit.label] = counts.get(edit.label, 0) + hits
            applied = True
        if not applied and (edit.required or strict):
            missing.append(edit.smali_path + "->" + edit.method + edit.signature)

    for target in INSTANCE_TARGETS + STATIC_TARGETS:
        key = "%s->%s%s" % (target.owner, target.method, target.signature)
        if counts.get(key, 0):
            continue
        if target.required:
            missing.append(key)
        elif strict:
            missing.append(key)
    if missing and not allow_missing:
        raise PatchError("no call sites matched:\n  " + "\n  ".join(missing))
    if not counts:
        raise PatchError("no telephony call sites were rewritten; this apk is not supported")
    return Result(counts, changed, tuple(sorted(changed_dirs)),
                  len(override_names), tuple(sorted(override_names)))
