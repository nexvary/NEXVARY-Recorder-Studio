# Optional launcher appearance — engineering candidate 0.13.1

The appearance settings offer three fixed, locally bundled launcher entries:
Guardian, Clock and Calendar. They all open the existing Guardian main/child-mode
entry point. These are shortcut labels/icons, not independent clock/calendar apps.
The selection is explicitly user-confirmed and protected by the existing parent PIN
when configured. Restore the original from the same page.

The package/application identity remains `com.nexvary.recorder`. Android app settings,
recording notifications, normal camera/microphone indicators and stop controls remain
unchanged. This is not uninstall protection or guaranteed concealment. Previously
received off-device copies are independent of uninstalling; app-private originals,
unsent attachments and queues may be lost when an app is uninstalled/its data cleared.
An accepted SMTP transaction is still not proof of external inbox receipt.

## Implementation and recovery

`LauncherAppearance` uses three stable `activity-alias` names. MainActivity is never
disabled, so existing internal intents/notification destinations remain valid.
On API33+ one PackageManager batch atomically enables exactly one alias and disables
the others using DONT_KILL_APP. On API29–32 it enables the replacement first, then
disables the previous entries. An interruption of the old sequence may leave duplicate
shortcuts temporarily, but no intentional all-disabled state. An error attempts to
restore the original entry before cleaning up other aliases. Current selection is
read from persistent PackageManager state, not a conflicting preferences cache.
A process-wide lock serializes changes from multiple page/controller instances,
including rollback, so older-device enable/disable sequences cannot interleave.
Keep alias names stable in future versions so Android component overrides survive.

The manifest migrates the original MAIN/LAUNCHER filter from MainActivity to the
default-enabled Original alias. An old pinned shortcut may need refreshing by the
launcher after an update; its explicit MainActivity target is retained and enabled.
Actual launcher caches/pinned-icon/OEM behavior and physical upgrades require phone
verification. No arbitrary downloaded icons/names, keyboard access, accessibility
service, call-log permissions, device administration or OS restriction bypass is added.

## Why no all-shortcuts-hidden or dial-pad-code mode

Android's official LauncherApps documentation describes synthesized app-settings
entries when launcher entries are absent on modern versions; disabling all app
shortcuts is not guaranteed concealment. Dialer special-code handling depends on the
default dialer/platform. Android documents ACTION_SECRET_CODE broadcasts; a receiving
app is not required to become the default dialer merely to receive one. The sender
must be the default dialer or have carrier privileges, and the phone's actual dialer
must implement the trigger. Receiving the documented broadcast is therefore possible,
but a working end-to-end OPPO dial-pad launch has not been verified here. We keep the
launcher reachable instead of making emergency access depend on that untested route. Typing a code in arbitrary apps
would require inappropriate keyboard/accessibility interception; none is implemented.

Official references:
- https://developer.android.com/guide/topics/manifest/activity-alias-element
- https://developer.android.com/reference/android/content/pm/LauncherApps#getActivityList(java.lang.String,android.os.UserHandle)
- https://developer.android.com/reference/android/telephony/TelephonyManager#sendDialerSpecialCode(java.lang.String)

## Verification status at initial implementation

Local UI/page/help/resource/whitespace/shell checks passed. Five instrumentation cases
cover alias resolution/labels/reconstructed selection, real cancel/apply/rotation UI,
parent-PIN protection, concurrent controllers, and actual capture continuing through all three selections
with an unchanged PID/session and a readable finalized MP4. A focused API29/API35
workflow covers the old enable-first and newer atomic branches. The normal API33/34/35
matrix and DEBUG=false mail regression gate must pass before a signed owner APK is
handed off. At this initial checkpoint the new Android cases have not yet run.
