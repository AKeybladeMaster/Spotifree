from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

class ContractTest(unittest.TestCase):
    def test_android_manifest_and_widget_xml(self):
        ET.parse(ROOT / 'android/app/src/main/AndroidManifest.xml')
        ET.parse(ROOT / 'android/app/src/main/res/xml/player_widget_info.xml')
        ET.parse(ROOT / 'android/app/src/main/res/layout/player_widget.xml')

    def test_desktop_resource_xml(self):
        ET.parse(ROOT / 'desktop/assets.qrc')

    def test_no_visible_added_headers_or_filter_controls(self):
        android = (ROOT / 'android/app/src/main/java/com/spotifree/app/MainActivity.java').read_text()
        desktop = (ROOT / 'desktop/src/AppWindow.cpp').read_text()
        self.assertNotIn('showProfiles(', android)
        self.assertNotIn('adButton', android)
        self.assertNotIn('profilePicker', desktop)
        self.assertNotIn('adBlock.Check', desktop)
        self.assertNotIn('addJavascriptInterface(', android)
        self.assertIn('setCentralWidget(view_)', desktop)
        self.assertIn('setContentView(browser)', android)

    def test_android_media_controls_require_active_media(self):
        activity = (ROOT / 'android/app/src/main/java/com/spotifree/app/MainActivity.java').read_text()
        service = (ROOT / 'android/app/src/main/java/com/spotifree/app/PlaybackService.java').read_text()
        self.assertIn('hasMedia:', activity)
        self.assertIn('clearPlayback();', activity)
        self.assertIn('stopService(new Intent(this, PlaybackService.class))', activity)
        self.assertIn('PlayerWidget.renderAll(this, "Spotifree", false)', service)

    def test_release_workflow_covers_all_platforms_as_preview(self):
        workflow = (ROOT / '.github/workflows/release.yml').read_text()
        for target in ('desktop-windows:', 'desktop-macos:', 'desktop-linux:', 'android:'):
            self.assertIn(target, workflow)
        self.assertIn('--prerelease', workflow)

    def test_protected_package_is_not_redistributed(self):
        self.assertFalse((ROOT / 'Spotify').exists())
        self.assertFalse(any(ROOT.rglob('Spotify.exe')))
        self.assertFalse(any(ROOT.rglob('Spotify.dll')))

if __name__ == '__main__':
    unittest.main()
