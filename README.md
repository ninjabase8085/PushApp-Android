# PushApp-Android SDK

A lightweight Android SDK to support push notifications, custom in-app messages (popup, banner, PiP), event tracking, and session handling for your apps.

---

## 📦 Installation

Add the following to your root `build.gradle`:

```gradle
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```
Then, add the dependency in your module-level build.gradle:

```gradle
dependencies {
    implementation("com.github.ninjabase8085:PushApp-Android:v1.0.2")
}
```

## 🚀 Initialization
Initialize the SDK in your MainActivity (or Application class if preferred):

```kotlin
PushApp.getInstance().initialize(
    context = this,
    identifier = "yourTenant#yourChannelId",
    sandbox = true // or false for production
)
```

To login the user:

```kotlin
PushApp.getInstance().login("user_id")
```

## 🎯 Event Tracking
To track user actions or custom events:

```kotlin
PushApp.getInstance().sendEvent("event_name", mapOf("key" to "value"))
```

## 🔔 Notification Handling
The SDK auto-registers FCM token and handles push notifications. Ensure you have Firebase configured.

## In-App Notifications
- The SDK handles:
--Popup full-screen .
--Banner with inline dismiss.
--PiP small floating view with expand logic to popup.

No integration required from your side. The SDK renders them when triggered.

## 📄 ProGuard
If using ProGuard, add:

```kotlin
-keep class com.mehery.pushapp.** { *; }
```

## 🏷️ Versions
Latest version: v1.0.2
Hosted on JitPack

## 💬 Support
Raise issues or feature requests in GitHub Issues
