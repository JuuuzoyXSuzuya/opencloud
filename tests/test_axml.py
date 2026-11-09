import unittest

from opencloud import axml


NO = 0xFFFFFFFF
STRING = 0x03
BOOLEAN = 0x12
NAME = 0x01010003
EXPORTED = 0x01010010


def document():
    strings = [
        "manifest",
        "application",
        "package",
        "com.example.app",
        "android",
        "http://schemas.android.com/apk/res/android",
        "name",
        "exported",
    ]
    root = axml.Element(
        name=0,
        attributes=[axml.XmlAttribute(NO, 2, 3, STRING, 3)],
        children=[axml.Element(name=1)],
    )
    return axml.Document(
        strings=strings,
        resource_ids={6: NAME, 7: EXPORTED},
        namespace_starts=[(4, 5, 0)],
        namespace_ends=[(4, 5, 0)],
        root=root,
        utf8=False,
    )


class AxmlTests(unittest.TestCase):
    def test_round_trip_preserves_root_and_package(self):
        data = axml.serialize(document())
        parsed = axml.parse(data)
        self.assertEqual("manifest", parsed.strings[parsed.root.name])
        self.assertEqual("com.example.app",
                         axml.attr(parsed.root, "package", parsed.strings,
                                   parsed.resource_ids))

    def test_activity_is_added_and_replaced(self):
        doc = document()
        axml.add_activity(doc, "dev.opencloud.region.SettingsActivity", "OpenCloud")
        axml.add_activity(doc, "dev.opencloud.region.SettingsActivity", "OpenCloud")
        parsed = axml.parse(axml.serialize(doc))
        application = next(child for child in parsed.root.children
                           if parsed.strings[child.name] == "application")
        activities = [child for child in application.children
                      if parsed.strings[child.name] == "activity"]
        self.assertEqual(1, len(activities))
        self.assertEqual("dev.opencloud.region.SettingsActivity",
                         axml.attr(activities[0], "name", parsed.strings,
                                   parsed.resource_ids))
        self.assertEqual("OpenCloud",
                         axml.attr(activities[0], "label", parsed.strings,
                                   parsed.resource_ids))

    def test_new_strings_do_not_shift_existing_indexes(self):
        doc = document()
        before = doc.strings[3]
        axml.add_activity(doc, "dev.opencloud.region.SettingsActivity", "OpenCloud")
        self.assertEqual(before, doc.strings[3])
        parsed = axml.parse(axml.serialize(doc))
        self.assertEqual(before, parsed.strings[3])

