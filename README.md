# Call Recorder (personal use, Redmi Note 10 Pro Max / MIUI 14 / Android 13)

Call recorder for personal use.

## What it does
- Auto-records normal phone calls (incoming + outgoing), start/stop tied to
  call state (OFFHOOK / IDLE).
- Detects WhatsApp and IMO call screens via an Accessibility Service and
  auto-records those too, with speakerphone turned on automatically so the
  other person's voice reaches the microphone.
- Recordings list with play / share / delete, saved under
  `Android/data/com.example.callrecorder/files/Recordings/`.

## How to build
1. Install **Android Studio** (Hedgehog or newer).
2. Open the `CallRecorderApp` folder as a project (File → Open).
3. Let Gradle sync (needs internet the first time, for dependencies).
4. Connect your Redmi Note 10 Pro Max via USB with USB debugging on,
   press Run.

## First-run setup on the phone (required)
1. Open the app → **Grant recording permissions** → allow all.
2. Tap **Enable WhatsApp/IMO detection (Accessibility)** → find
   "Call Recorder" in the list → turn it on. Android will show a warning
   about accessibility services reading screen content — that's expected,
   since that's how it detects a call screen.
3. Tap **Disable battery optimization for this app**, and also go to
   **Security app → Permissions → Autostart** and enable it for this app
   (MIUI kills background apps aggressively otherwise).
4. In Recents, swipe the app and tap the lock icon so MIUI doesn't kill it.

## Known limitations (please read)
- On Android 10+, third-party apps cannot tap raw call audio directly.
  Normal calls use `VOICE_COMMUNICATION` audio source (falls back to plain
  `MIC` if that's blocked on your ROM). WhatsApp/IMO calls use `MIC` +
  auto-speakerphone, since there's no official audio-source permission for
  VoIP call audio.
- WhatsApp/IMO call detection is heuristic (looks for on-screen text like
  "Calling…", "Connected", or a running timer). It is not 100% reliable
  and may need tweaking after an app update — see the comments in
  `MessengerAccessibilityService.kt`.
- MIUI's own battery manager can still kill the background service even
  with the steps above; if recordings stop happening after a while, check
  Settings → Apps → Call Recorder → Battery saver → No restrictions again.

## Legal note
Call-recording consent laws vary by country/state. Make sure you comply
with the laws that apply to you before recording any call.
