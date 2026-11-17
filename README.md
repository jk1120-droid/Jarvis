# Jarvis — Android starter

Jarvis is a small Czech-first Android tutoring app starter built with Kotlin and Jetpack Compose. It has a text chat, optional spoken replies, Czech speech input, and a local offline response engine. The UI and tutor responses do not need Wi-Fi.

## Open and run

1. Open this repository in Android Studio (a current version with Android SDK 35 support).
2. Let Gradle sync, then run the `app` configuration on an Android 8.0+ device or emulator.
3. For speech input, grant microphone permission. The device must have a speech recognition service installed; offline Czech recognition depends on that service and its downloaded language pack. Czech text-to-speech likewise requires a Czech voice installed on the device.

## Important limitation

This is a working starter, not a full generative AI model. The included tutor uses a small set of local Czech teaching responses, so it works without Wi-Fi but cannot answer arbitrary questions with the reasoning quality of a large AI. A genuinely capable offline assistant needs an on-device model and its model files, which are not included. The app deliberately has no internet permission and does not send chat or audio anywhere.

## Next steps

- Replace or extend `LocalTutor` with a licensed, quantized on-device language model.
- Add lessons and conversation storage with Room.
- Add tests for tutor responses and speech behavior.
