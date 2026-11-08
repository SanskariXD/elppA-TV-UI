# Mi Box test session

The Xiaomi Mi Box is the first physical test device. Its exact model and firmware
are not yet known; do not assume a particular Android version or ADB port.

## Connect and install

Enable Developer options and the debugging option actually provided by the box.
For network debugging use the displayed endpoint. For Wireless debugging pair with
its displayed pairing port, then connect using its separate connection port.
Approve the computer on the TV. Keep the emulator separate from the box.

```powershell
adb devices -l
$device = "<exact-authorised-endpoint>"
python tools/elppa.py --serial $device probe
python tools/elppa.py --serial $device install --apk launcher/elppa/build/outputs/apk/release/elppa-release.apk
python tools/elppa.py --serial $device grant-content
```

The placeholder must be replaced with the endpoint shown by ADB. Stop on an
unauthorised/offline device, unsupported API (<26), or refused permission.
Installation opens the launcher as an app and does not switch Home.
For signing-key mismatches see [CODEX-CLI.md](CODEX-CLI.md).

## Establish actual content coverage

Turn Preview mode off. Open the OTT apps you already have, play a title briefly,
return to elppA, and inspect Settings → Content diagnostics. Record whether each
app publishes Watch Next and preview rows, whether art loads and whether selecting
a title opens its correct detail/playback screen. An empty row can be an app/provider
limitation; installing a launcher cannot force Netflix or another app to publish history.
Use Preview mode only to check layout when no content exists, then turn it off.

## Capture the real screen

These PowerShell commands avoid binary-redirection corruption on older Windows shells:

```powershell
New-Item -ItemType Directory -Force shots | Out-Null
adb -s $device shell screencap -p /sdcard/elppa-screen.png
adb -s $device pull /sdcard/elppa-screen.png shots/mibox-home.png
adb -s $device shell rm /sdcard/elppa-screen.png
```

Capture default and enlarged text, hero/actions focused, apps focused and the media
row focused. Do not publish account names, IPs or other personal information.
Screenshots stay in ignored `shots/`, never in commits.

## Test before setting Home

- D-pad left/right/up/down at all edges; long press, All apps, source/hide settings.
- Native box settings, network settings, app launch and return, permission recovery.
- Physical microphone button and Google Home phone D-pad/typing.
- Offline launch, artwork failure, idle return, standby/wake and reboot.
- No frozen focus, clipped title/action, cropped focus border or unexpected demo data.

A Mi Box opens its own Android settings. It cannot promise Sony picture/sound or
HDMI-input control on the connected TV; those remain tests on the original Sony.
An empty Inputs list on a streaming box is not evidence of a Sony integration fault.

Once the user verifies the app, the existing guarded tool can make it Home:

```powershell
python tools/elppa.py --serial $device make-home
# Keep the recovery path printed by the tool:
python tools/elppa.py --serial $device restore --record backup/<record-folder>/restore.json
```

Do not execute restore with a placeholder. Keep the stock launcher and all services
enabled. Test the physical Home/mic again after selection. Turn debugging off after
testing; elppA does not require a persistent ADB connection.

## Record results locally

Keep model, Android/API, build, app version, listing permission, provider counts,
D-pad/voice/phone/settings/standby/reboot outcomes and observed defects in ignored
`work/`. Separate an observed pass from a test that was not performed. Only sanitised
summaries belong in QA.md. No performance claims without device measurements.
