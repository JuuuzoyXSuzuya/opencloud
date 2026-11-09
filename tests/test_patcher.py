import tempfile
import unittest
from pathlib import Path

from opencloud import patcher


REQUIRED = (
    "getSimCountryIso",
    "getNetworkCountryIso",
    "getSimOperator",
    "getNetworkOperator",
    "getSimOperatorName",
    "getNetworkOperatorName",
)


def sample(include_required=True):
    lines = []
    if include_required:
        for name in REQUIRED:
            lines.append("    invoke-virtual {p0}, Landroid/telephony/TelephonyManager;->%s()Ljava/lang/String;"
                         % name)
    lines.append("    invoke-virtual/range {p0 .. p0}, Landroid/telephony/TelephonyManager;->getSimState()I")
    lines.append("    invoke-virtual {p0, v0}, Landroid/telephony/TelephonyManager;->getSimState(I)I")
    lines.append("    invoke-static {}, Landroid/telephony/SubscriptionManager;->getDefaultDataSubscriptionId()I")
    return (".method public static a(Landroid/telephony/TelephonyManager;I)V\n"
            "    .locals 2\n" + "\n".join(lines) + "\n"
            "    return-void\n.end method\n")


class PatcherTests(unittest.TestCase):
    def test_instance_and_static_calls_are_rewritten(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "smali").mkdir()
            path = root / "smali" / "A.smali"
            path.write_text(sample(), encoding="utf-8")
            result = patcher.rewrite(root)
            self.assertEqual(9, result.total)
            text = path.read_text(encoding="utf-8")
            self.assertIn("invoke-static {p0}, Ldev/opencloud/region/Hooks;->country"
                          "(Landroid/telephony/TelephonyManager;)Ljava/lang/String;", text)
            self.assertIn("invoke-static/range {p0 .. p0}, Ldev/opencloud/region/Hooks;->simState"
                          "(Landroid/telephony/TelephonyManager;)I", text)
            self.assertIn("invoke-static {}, Ldev/opencloud/region/Hooks;->defaultDataId()I",
                          text)

    def test_missing_required_hook_fails(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "smali").mkdir()
            (root / "smali" / "A.smali").write_text(sample(False), encoding="utf-8")
            with self.assertRaises(patcher.PatchError):
                patcher.rewrite(root)

    def test_allow_missing_overrides_required_check(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "smali").mkdir()
            (root / "smali" / "A.smali").write_text(sample(False), encoding="utf-8")
            result = patcher.rewrite(root, allow_missing=True)
            self.assertGreater(result.total, 0)

    def test_no_smali_directory_fails(self):
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaises(patcher.PatchError):
                patcher.rewrite(Path(temp))

    def test_settings_entry_wraps_on_create_view(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "smali").mkdir()
            (root / "smali" / "A.smali").write_text(sample(), encoding="utf-8")
            fragment = (".method public final onCreateView(Landroid/view/LayoutInflater;"
                        "Landroid/view/ViewGroup;Landroid/os/Bundle;)Landroid/view/View;\n"
                        "    .locals 6\n\n"
                        "    new-instance v5, Landroidx/compose/ui/platform/ComposeView;\n"
                        "    return-object v5\n"
                        ".end method\n")
            target = root / "smali" / patcher.SETTINGS_PATH
            target.parent.mkdir(parents=True)
            target.write_text(fragment, encoding="utf-8")
            result = patcher.rewrite(root)
            self.assertEqual(1, result.counts.get("settings entry"))
            text = target.read_text(encoding="utf-8")
            self.assertIn("Ldev/opencloud/region/SettingsEntry;->decorate", text)

    def test_google_web_method_is_replaced(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "smali").mkdir()
            (root / "smali" / "A.smali").write_text(sample(), encoding="utf-8")
            original = (".method public static final LJFF(Ljava/lang/String;)Ljava/lang/String;\n"
                        "    .locals 1\n\n"
                        "    const-string v0, \"google\"\n"
                        "    return-object v0\n"
                        ".end method\n")
            target = root / "smali" / "X" / "1DZZ.smali"
            target.parent.mkdir()
            target.write_text(original, encoding="utf-8")
            result = patcher.rewrite(root)
            self.assertEqual(1, result.overrides)
            text = target.read_text(encoding="utf-8")
            self.assertIn('const-string v0, "google_web"', text)
            self.assertIn('const-string v0, "facebook"', text)

