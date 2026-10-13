# OMNIRA

**A classroom collaboration app for Android.**

OMNIRA brings class announcements, study materials, assignments, attendance, routines, group chat, and member-to-member messaging together in one Compose application.

> **Media upload security:** Android apps can be inspected, so never embed Cloudinary API secrets in the app or repository. OMNIRA uses a restricted unsigned upload preset configured locally; rotate any credential previously exposed in an app build or source, and use a trusted backend for signed uploads. Never paste credentials into issues, commits, or documentation.

## What is inside

| Area | Capabilities |
| --- | --- |
| Classrooms | Create classes, request to join by code, approve requests, manage members and admins |
| Class content | Stream posts and comments, materials, assignments, class routines, attendance |
| Messaging | Class chat with attachments, per-class retention, moderation, reports, and unread counts |
| Inbox | Direct conversations with photos and files, read indicators, message editing and deletion |
| Notifications | Class/user notifications, approval requests, and unread badges |
| UI | Jetpack Compose, Material 3, responsive layouts, and OMNIRA's neumorphic visual style |

## Project map

```text
.
├── app/
│   └── src/main/java/com/omnira/
│       ├── MainActivity.kt          # Compose entry point and navigation
│       ├── data/                    # Firestore models and account deletion flow
│       ├── ui/
│       │   ├── components/          # Reusable Compose components
│       │   ├── screens/             # App screens and feature flows
│       │   └── theme/               # Colors, typography, and neumorphic modifiers
│       └── utils/                   # Upload, download, date, and attendance helpers
├── docs/
│   ├── ARCHITECTURE.md
│   ├── DATABASE.md
│   └── DESIGN.md
├── CHANGELOG.md                    # Production release notes
├── firestore.rules                 # Firestore rules starting point
├── LICENSE.md                       # OMNIRA proprietary-use notice
└── README.md
```

## Technology

- Kotlin and Jetpack Compose with Material 3
- Navigation Compose
- Firebase Authentication and Cloud Firestore
- Cloudinary Android SDK for media uploads
- Coil for image loading
- Gradle version catalog in `gradle/libs.versions.toml`

## Build and run

### Requirements

- Android Studio with Android SDK 35
- JDK 17 for the Android Gradle Plugin; app source/target compatibility is Java 11
- A Firebase project with the Android app registered and Authentication/Firestore configured
- A restricted unsigned Cloudinary upload preset (see Setup)

### Setup

1. Clone the repository and open it in Android Studio.
2. Configure Firebase for your own project. Place your local `google-services.json` at `app/google-services.json`; it is intentionally ignored by Git.
3. Enable the Firebase Authentication providers used by the app and configure Firestore.
4. Create a restricted unsigned Cloudinary upload preset. Add `cloudinaryUploadPreset=YOUR_PRESET_NAME` to `%USERPROFILE%\.gradle\gradle.properties` (Windows) or `~/.gradle/gradle.properties` (macOS/Linux). Do not put an API secret in the Android app or repository.
5. Sync Gradle and run the `app` configuration on an Android device or emulator.

Build from the repository root:

```powershell
.\gradlew.bat :app:assembleDebug
```

Compile Kotlin sources:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

## Firebase and access control

The app reads and writes Firestore directly from Android screens. Configure and test Firestore Security Rules in your Firebase project: UI-level admin checks are not a security boundary. Rules should validate authentication, class membership, administrator privileges, document ownership, and allowed fields for every write path.

The repository includes a starting ruleset in `firestore.rules`. Review and test it against your Firebase project before deploying. The preserved class, notification, and profile rules allow broad access to any authenticated user; production deployments should restrict them and add server-side validation for sensitive operations such as member management, approvals, notification delivery, and retention cleanup.

The Profile account-deletion flow requires the matching direct-message participant-removal rule in `firestore.rules`. Publish and test the rules in the Firebase project before enabling account deletion in a production build. Account cleanup runs from the client and can partially complete if a request fails; use a trusted backend for stronger transactional deletion guarantees.

## Documentation

- [Production changelog](CHANGELOG.md)
- [Project report (LaTeX)](docs/PROJECT_REPORT.tex)
- [Architecture and feature flows](docs/ARCHITECTURE.md)
- [Firestore collections and document shapes](docs/DATABASE.md)
- [UI design system](docs/DESIGN.md)
- [Copyright and commercial-use terms](LICENSE.md)

## License

OMNIRA is **not** released under an open-source license. All rights are reserved by the rights holder. Commercial use requires prior written permission and a separate written license agreement; see [LICENSE.md](LICENSE.md). Dependencies and third-party services remain subject to their own terms.
