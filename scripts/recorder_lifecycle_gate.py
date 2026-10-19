#!/usr/bin/env python3
"""Static regression gate for Android screen capture lifecycle wiring.

This gate checks source invariants; it does NOT simulate screen lock or prove
capture works on a physical device.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / "app/src/main/java/com/nexvary/recorder/screen/ScreenRecordService.kt"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"


class ScreenCaptureContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.service = SERVICE.read_text(encoding="utf-8")
        cls.manifest = MANIFEST.read_text(encoding="utf-8")

    def test_foreground_permission_and_service_types(self):
        for permission in (
            "FOREGROUND_SERVICE_MEDIA_PROJECTION",
            "FOREGROUND_SERVICE_MICROPHONE",
            "FOREGROUND_SERVICE_CAMERA",
        ):
            with self.subTest(permission=permission):
                self.assertIn("android.permission." + permission, self.manifest)
        self.assertRegex(
            self.manifest,
            r'android:name="\.screen\.ScreenRecordService"[\s\S]*?'
            r'android:foregroundServiceType="mediaProjection\|microphone\|camera"',
        )

    def test_foreground_started_before_projection(self):
        body = self.service.split("private fun startRecording(", 1)[1].split(
            "private fun createOutput(", 1
        )[0]
        self.assertLess(body.index("startForeground("), body.index("getMediaProjection("))
        self.assertLess(body.index("getMediaProjection("), body.index("createVirtualDisplay("))
        self.assertLess(body.index("createVirtualDisplay("), body.index("recorder!!.start()"))

    def test_projection_revocation_and_user_stop_are_handled(self):
        self.assertIn("override fun onStop()", self.service)
        self.assertIn("finishRecording(fromProjectionCallback = true", self.service)
        self.assertIn("ACTION_STOP -> finishRecording(", self.service)
        self.assertIn("START_NOT_STICKY", self.service)

    def test_pause_resume_and_explicit_started_state(self):
        for token in ("ACTION_PAUSE", "ACTION_RESUME", "STATE_STARTED", "STATE_ERROR"):
            with self.subTest(token=token):
                self.assertIn(token, self.service)
        self.assertLess(
            self.service.index("recorder!!.start()"),
            self.service.index("sendStatus(\n                STATE_STARTED"),
        )


if __name__ == "__main__":
    unittest.main()
