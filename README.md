# Jarvis — Android starter

Jarvis is a Czech-first Android tutoring app built with Kotlin and Jetpack Compose. It offers text chat, optional spoken replies, Czech speech input, a simple local offline tutor, and an optional online AI mode using an OpenAI-compatible chat-completions API.

## Open and run

1. Open this repository in Android Studio (a current version with Android SDK 35 support).
2. Let Gradle sync, then run the `app` configuration on an Android 8.0+ device or emulator.
3. For speech input, grant microphone permission. The device must have a speech recognition service installed; offline Czech recognition depends on that service and its downloaded language pack. Czech text-to-speech likewise requires a Czech voice installed on the device.
4. Online mode requires internet access, an API account/key, and a provider/model that supports the OpenAI-compatible `/v1/chat/completions` request format. In the app, switch to online mode and enter the provider's HTTPS endpoint, model name, and API key. The default endpoint/model are examples; availability and pricing depend on your provider.

## Privacy and API key

In online mode, the conversation is sent to the configured API provider to generate replies. Only use a provider and endpoint you trust. The API key is kept in app memory only and is not saved to disk; it is cleared when the app process ends. A key entered in a mobile app is still accessible to the device owner and should only be used for personal testing. Do not ship a shared production API key in an app; use a backend you control to protect it.

The app has no analytics or custom tracking. Android speech recognition and text-to-speech may be provided by other apps/services, depending on the device configuration.

## Offline limitation

Offline mode needs no network and uses a small set of local Czech teaching responses. It is not a full generative AI model and cannot answer arbitrary questions with the reasoning quality of a large AI. A capable generative offline assistant needs an on-device model and its model files, which are not included.
