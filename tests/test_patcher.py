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

    def test_typeface_calls_are_rewritten(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "smali").mkdir()
            path = root / "smali" / "B.smali"
            path.write_text(
                ".method public static a()V\n    .locals 2\n"
                "    invoke-static {v0, v1}, Landroid/graphics/Typeface;->"
                "create(Ljava/lang/String;I)Landroid/graphics/Typeface;\n"
                "    invoke-static {v0, v1}, Landroid/graphics/Typeface;->"
                "create(Landroid/graphics/Typeface;I)Landroid/graphics/Typeface;\n"
                "    invoke-static {v0}, Landroid/graphics/Typeface;->"
                "defaultFromStyle(I)Landroid/graphics/Typeface;\n"
                "    invoke-static {v0, v1}, Landroid/graphics/Typeface;->"
                "createFromAsset(Landroid/content/res/AssetManager;Ljava/lang/String;)"
                "Landroid/graphics/Typeface;\n"
                "    invoke-static {v0}, Landroid/graphics/Typeface;->"
                "createFromFile(Ljava/lang/String;)Landroid/graphics/Typeface;\n"
                "    return-void\n.end method\n",
                encoding="utf-8")
            result = patcher.rewrite(root, allow_missing=True)
            self.assertEqual(5, result.total)
            text = path.read_text(encoding="utf-8")
            self.assertIn("Hooks;->typeface(Ljava/lang/String;I)", text)
            self.assertIn("Hooks;->typeface(Landroid/graphics/Typeface;I)", text)
            self.assertIn("Hooks;->defaultTypeface(I)", text)
            self.assertIn("Hooks;->typefaceFromAsset(Landroid/content/res/AssetManager;"
                          "Ljava/lang/String;)", text)
            self.assertIn("Hooks;->typefaceFromFile(Ljava/lang/String;)", text)

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


    def test_google_web_method_is_replaced_in_new_location(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "smali").mkdir()
            (root / "smali" / "A.smali").write_text(sample(), encoding="utf-8")
            original = (".method public static final LIZ(Ljava/lang/String;)Ljava/lang/String;\n"
                        "    .locals 1\n\n"
                        "    const-string v0, \"google\"\n"
                        "    return-object v0\n"
                        ".end method\n")
            target = root / "smali" / "X" / "16ry.smali"
            target.parent.mkdir()
            target.write_text(original, encoding="utf-8")
            result = patcher.rewrite(root)
            self.assertEqual(1, result.overrides)
            text = target.read_text(encoding="utf-8")
            self.assertIn(".method public static final LIZ(Ljava/lang/String;)"
                          "Ljava/lang/String;", text)
            self.assertIn('const-string v0, "google_web"', text)

    def test_settings_entry_wraps_only_the_normal_return(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "smali").mkdir()
            (root / "smali" / "A.smali").write_text(sample(), encoding="utf-8")
            fragment = (".method public final onCreateView(Landroid/view/LayoutInflater;"
                        "Landroid/view/ViewGroup;Landroid/os/Bundle;)Landroid/view/View;\n"
                        "    .locals 6\n\n"
                        "    return-object v5\n"
                        "    :catchall_0\n"
                        "    move-exception v1\n"
                        "    return-object v5\n"
                        ".end method\n")
            target = root / "smali" / patcher.SETTINGS_PATH
            target.parent.mkdir(parents=True)
            target.write_text(fragment, encoding="utf-8")
            result = patcher.rewrite(root)
            self.assertEqual(1, result.counts.get("settings entry"))
            text = target.read_text(encoding="utf-8")
            self.assertEqual(1, text.count("SettingsEntry;->decorate"))
            self.assertEqual(2, text.count("return-object v5"))

    def test_prefix_lands_after_an_annotation_block(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "smali").mkdir()
            (root / "smali" / "A.smali").write_text(sample(), encoding="utf-8")
            original = (".method public getItems()Ljava/util/List;\n"
                        "    .locals 3\n"
                        "    .annotation system Ldalvik/annotation/Signature;\n"
                        "        value = {\n"
                        "            \"()Ljava/util/List;\"\n"
                        "        }\n"
                        "    .end annotation\n\n"
                        "    .prologue\n"
                        "    iget-object v0, p0, Lcom/ss/android/ugc/aweme/feed/model/"
                        "FeedItemList;->items:Ljava/util/List;\n"
                        "    return-object v0\n"
                        ".end method\n")
            target = root / "smali" / patcher.FEED_PATH
            target.parent.mkdir(parents=True)
            target.write_text(original, encoding="utf-8")
            patcher.rewrite(root)
            text = target.read_text(encoding="utf-8")
            self.assertIn("Filters;->filterFeed", text)
            self.assertLess(text.index(".end annotation"),
                            text.index("Filters;->filterFeed"))
            self.assertLess(text.index("Filters;->filterFeed"),
                            text.index("iget-object v0"))

    def test_feed_hook_is_prepended_to_get_items(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "smali").mkdir()
            (root / "smali" / "A.smali").write_text(sample(), encoding="utf-8")
            original = (".method public getItems()Ljava/util/List;\n"
                        "    .locals 4\n\n"
                        "    iget-object v0, v3, Lcom/ss/android/ugc/aweme/feed/model/"
                        "FeedItemList;->items:Ljava/util/List;\n"
                        "    return-object v0\n"
                        ".end method\n")
            target = root / "smali" / patcher.FEED_PATH
            target.parent.mkdir(parents=True)
            target.write_text(original, encoding="utf-8")
            result = patcher.rewrite(root)
            self.assertEqual(1, result.counts.get("no ads"))
            text = target.read_text(encoding="utf-8")
            self.assertIn("Ldev/opencloud/region/Filters;->filterFeed", text)
            self.assertLess(text.index("Filters;->filterFeed"),
                            text.index("iget-object v0"))

    def test_feed_hook_skips_a_class_without_the_items_field(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "smali").mkdir()
            (root / "smali" / "A.smali").write_text(sample(), encoding="utf-8")
            original = (".method public getItems()Ljava/util/List;\n"
                        "    .locals 1\n\n"
                        "    const/4 v0, 0x0\n"
                        "    return-object v0\n"
                        ".end method\n")
            target = root / "smali" / patcher.FEED_PATH
            target.parent.mkdir(parents=True)
            target.write_text(original, encoding="utf-8")
            patcher.rewrite(root)
            text = target.read_text(encoding="utf-8")
            self.assertNotIn("Filters;->filterFeed", text)

    def test_live_tab_hook_hides_the_tab_before_the_original_logic(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "smali").mkdir()
            (root / "smali" / "A.smali").write_text(sample(), encoding="utf-8")
            original = (".method public final enable()Z\n"
                        "    .locals 2\n\n"
                        "    const-string v0, \"live_tab_single\"\n"
                        "    invoke-static {v1, v0}, Lkotlin/jvm/internal/Intrinsics;"
                        "LJI:(Ljava/lang/Object;Ljava/lang/Object;)Z\n"
                        "    move-result v0\n"
                        "    return v0\n"
                        ".end method\n")
            target = root / "smali" / patcher.LIVE_TAB_PATH
            target.parent.mkdir(parents=True)
            target.write_text(original, encoding="utf-8")
            result = patcher.rewrite(root)
            self.assertEqual(1, result.counts.get("no live"))
            text = target.read_text(encoding="utf-8")
            self.assertIn("Filters;->hideLiveTab()Z", text)
            self.assertIn("const-string v0, \"live_tab_single\"", text)
            self.assertLess(text.index("hideLiveTab"), text.index("live_tab_single"))

    def test_theme_hook_is_prepended_to_the_original_body(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "smali").mkdir()
            (root / "smali" / "A.smali").write_text(sample(), encoding="utf-8")
            original = (".method public static LJJIII(I)V\n"
                        "    .locals 2\n"
                        "    .param p0, \"mode\"\n\n"
                        "    const/4 v0, 0x1\n"
                        "    sput p0, LX/005C;->LLJJI:I\n"
                        "    return-void\n"
                        ".end method\n")
            target = root / "smali" / "X" / "005C.smali"
            target.parent.mkdir()
            target.write_text(original, encoding="utf-8")
            result = patcher.rewrite(root)
            self.assertEqual(1, result.counts.get("theme"))
            text = target.read_text(encoding="utf-8")
            self.assertIn("ThemePlugin;->mode(I)I", text)
            self.assertIn("sput p0, LX/005C;->LLJJI:I", text)
            self.assertLess(text.index("ThemePlugin;->mode"), text.index("sput p0"))
            self.assertLess(text.index(".param p0"), text.index("ThemePlugin;->mode"))
