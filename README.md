# Jarvis — soukromý offline pomocník pro Android

Jarvis is a Czech-first Android app starter built with Kotlin and Jetpack Compose. It offers local teacher, coding, and filmmaking tutor modes; offline speech input/output; a private on-device memory and chat history; date/time awareness using the device clock; and an optional daily notification digest.

## Privacy

- The app has no INTERNET permission and does not send chat, memories, or notifications to cloud AI.
- Chat history (up to 100 messages), up to 50 user-entered memory notes, and up to 200 captured notification entries are stored in the app's private local preferences. App backup is disabled.
- Notification access is sensitive and optional. The user must explicitly enable Jarvis in Android's notification access settings and may revoke it at any time.
- Voice input uses Android on-device speech recognition only (Android 12+); an offline Czech speech pack is required. An installed Czech offline TTS voice is needed for speech output.

## Memory, clock, and daily summary

Add notes under **Soukromá paměť**; they stay on this device and can be deleted with **Zapomenout vše**. The chat is restored locally when the app is reopened. Ask Jarvis for the current time/date to get the device's current Czech-local date and time. Jarvis does not infer that the system clock is correct; it uses Android's configured clock.

For the daily notification digest, choose **Zapnout přehled**, grant notification permission, then choose **Povolit přístup** and enable Jarvis in Android settings. The digest is scheduled for approximately **18:00 local time**. Android power management may delay it. Tap **Dnešní přehled** in the app to view the same-day entries. Captured notification content is stored locally, so enable access only if you are comfortable with Jarvis reading those notifications.

## Run

Open in Android Studio with Android SDK 35 support, sync Gradle, and run `app` on Android 8.0+. Jarvis is a local rule-based tutor rather than a full generative AI model. Notification access, alarms, and offline voice support should be tested on a device with the required permissions and Czech language packs.
