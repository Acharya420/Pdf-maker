# PDF Maker — Android Studio Project, Cloud Backend & Admin Panel

**PDF Maker** is a modern, offline-first Android application built with **Kotlin**, **Jetpack Compose**, **Material 3** (Liquid Glass UI), **Room**, **DataStore**, **Android Photo Picker**, **Storage Access Framework (SAF)**, and **PDFBox-Android**, paired with a **Firebase Backend (Authentication, Cloud Firestore, Cloud Functions, Security Rules)** and a **separate React + TypeScript + Tailwind CSS Admin Panel**.

All PDF creation, compression, merging, splitting, rotation, page reordering, password encryption, and rendering execute **100% locally on the user's device**—private photos and PDFs are never uploaded to external servers.

---

## 1. Repository Structure

```text
.
├── app/                              # Complete Android Studio (Kotlin + Compose) project
│   ├── src/main/
│   │   ├── AndroidManifest.xml       # FileProvider, Photo Picker / minimal permissions
│   │   ├── java/com/example/
│   │   │   ├── PdfMakerApp.kt        # Application class (PDFBox, Room, DataStore, lazy Firestore)
│   │   │   ├── MainActivity.kt       # Edge-to-edge host + Dynamic theme & localization
│   │   │   ├── data/
│   │   │   │   ├── local/            # Room DB (PdfDocumentEntity, Dao), DataStore Preferences
│   │   │   │   ├── model/            # PageSize, Orientation, Margins, Layout, Quality, SplitMode
│   │   │   │   └── remote/           # CredentialManager Google Sign-In & CloudSyncRepository
│   │   │   ├── pdf/                  # Offline PDF processing engines
│   │   │   │   ├── PdfVaultManager.kt
│   │   │   │   ├── ImageToPdfEngine.kt
│   │   │   │   └── PdfToolsEngine.kt
│   │   │   ├── viewmodel/            # MainViewModel, ImageToPdfViewModel, PdfToolsViewModel
│   │   │   └── ui/                   # Liquid Glass M3 Theme, i18n, Navigation, All Screens
│   │   └── res/                      # Adaptive launcher icon, local fonts, illustrations, strings
│   └── src/test/                     # Robolectric unit tests, PDF engine E2E tests, Firestore Rule tests
├── admin-panel/                      # Separate React + TypeScript + Tailwind Admin Dashboard
│   ├── src/
│   │   ├── App.tsx                   # Role-gated Admin UI (Overview, Config, Announcements, Push, Feedback, Users)
│   │   ├── firebase.ts               # Web Firebase SDK initialization targeting named Firestore DB
│   │   └── types.ts                  # Typed Admin domain models
│   ├── .env.example                  # Firebase Web App environment placeholders
│   └── package.json
├── functions/                        # Firebase Cloud Functions (Node.js / TypeScript + Admin SDK)
│   └── src/index.ts                  # sendPushNotification (FCM), setUserAccountStatus, setAdminRole
├── firebase-blueprint.json           # Canonical Firestore entity & collection schema
├── firestore.rules                   # Zero-trust Firestore Security Rules
├── firestore.test.js                 # Tier 1 Node.js emulator security rules test suite
└── firebase.json                     # Emulator & Firestore rules configuration
```

---

## 2. Database Structure

### Local Persistence (Room Database — `pdf_maker_db`)
Used for 100% offline history, favorites, export tracking, and instant file management (works in Guest Mode with zero login):
- **`pdf_documents`**:
  - `id` (PK), `fileName`, `filePath`, `operationType` (`IMAGE_TO_PDF`, `MERGE`, `SPLIT`, `COMPRESS`, `ORGANIZE`, `ROTATE`, `CONVERT`, `PROTECT`), `pageCount`, `fileSizeBytes`, `createdAt`, `updatedAt`, `isFavorite`, `isPasswordProtected`, `thumbnailPath`

### User Preferences (Jetpack DataStore)
- `onboardingCompleted`, `themeMode` (`system` | `light` | `dark`), `languageCode` (`en` | `es` | `hi` | `fr` | `de`), `guestDisplayName`

### Cloud Firestore Database (`ai-studio-android-pdfmaker-dcb36df0-08dc-4927-833f-13c41b1da3a7`)
Privacy-preserving cloud sync for optionally signed-in Google users + Admin Panel operations:
- `/users/{userId}` — Profile preferences (`preferredTheme`, `preferredLanguage`), aggregate counts (`pdfsCreatedCount`, `totalBytesProcessed`), account `status` (`active` | `suspended`)
- `/users/{userId}/pdf_records/{recordId}` — Synced PDF operation metadata (file name, operation type, page count, size—never raw PDFs)
- `/app_config/global` — Maintenance mode toggle & message, latest/min supported version codes, force-update flag, remote feature toggles
- `/announcements/{announcementId}` — Active announcements displayed on the Android Home dashboard
- `/feedback/{feedbackId}` — User bug reports/feature requests with Admin status & reply tracking
- `/admins/{adminUid}` — Trusted Admin SDK role registry (`super_admin` | `moderator`)

---

## 3. Firebase Configuration Instructions

1. **Android Firebase Config**:
   - The Android app uses `app/google-services.json` and `app/src/main/res/values/firebase_applet_config.xml` (containing `firestore_database_id`).
   - User sign-in in the Android app is optional (Guest Mode works offline) and uses **Google Sign-In via Jetpack Credential Manager** (`default_web_client_id` auto-generated by the Google Services Gradle plugin).
   - Ensure the **Google Sign-In** provider is enabled in Firebase Console → Authentication → Sign-in method, and register both Debug and Release SHA-1/SHA-256 keystore fingerprints in Project Settings.

2. **Deploy Security Rules**:
   ```bash
   firebase deploy --only firestore:rules --project <YOUR_FIREBASE_PROJECT_ID>
   ```

3. **Run Security Rules Tests Locally**:
   ```bash
   firebase emulators:exec --only firestore,auth --project demo-no-project "node --test firestore.test.js"
   ```

---

## 4. Separate Admin Panel & Cloud Functions Setup

Never ship admin credentials inside the Android APK. Deploy the Admin Panel (`admin-panel/`) and Cloud Functions (`functions/`) separately:

### Cloud Functions (`functions/`)
```bash
cd functions
npm install
npm run build
firebase deploy --only functions --project <YOUR_FIREBASE_PROJECT_ID>
```
Cloud Functions exported:
- `sendPushNotification` — Verifies admin identity via Firebase Auth token & `admins/{uid}` / custom claims, then dispatches FCM messages to topics (e.g., `all_users`).
- `setUserAccountStatus` — Enables or suspends a Firebase Auth account and mirrors `status` in Firestore.
- `setAdminRole` — Grants/revokes `admin: true` custom claims and updates `/admins/{uid}`.

### React Admin Panel (`admin-panel/`)
1. Copy `admin-panel/.env.example` to `admin-panel/.env` and fill in your Firebase **Web App** config (`VITE_FIREBASE_API_KEY`, `VITE_FIREBASE_AUTH_DOMAIN`, `VITE_FIREBASE_PROJECT_ID`, `VITE_FIREBASE_STORAGE_BUCKET`, `VITE_FIREBASE_MESSAGING_SENDER_ID`, `VITE_FIREBASE_APP_ID`, `VITE_FIREBASE_DATABASE_ID`).
2. Install dependencies, run locally, or build for production hosting:
   ```bash
   cd admin-panel
   npm install
   npm run dev      # Local development server
   npm run build    # Production bundle in admin-panel/dist/
   ```
3. Deploy `admin-panel/dist/` to Firebase Hosting, Vercel, or Cloud Run behind your authorized Google Admin account.

---

## 5. Building Debug APK, Release APK & Signed App Bundle (.aab)

### Using Android Studio
1. Open the repository root in **Android Studio (Ladybug / Meerkat or newer)**.
2. Allow Gradle sync to complete (`compileSdk = 36`, `minSdk = 24`, `targetSdk = 36`, Java 11).
3. **Debug APK**:
   - Select **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
   - Output: `app/build/outputs/apk/debug/app-debug.apk`.
4. **Release APK or Signed Android App Bundle (.aab) for Google Play**:
   - Select **Build → Generate Signed App Bundle or APK…**.
   - Choose **Android App Bundle** (`.aab` — recommended for Google Play) or **APK**.
   - Create or select your upload keystore (`.jks`), enter keystore password, key alias, and key password.
   - Select the **release** build variant and click **Create**.
   - Outputs:
     - `.aab`: `app/build/outputs/bundle/release/app-release.aab`
     - `.apk`: `app/build/outputs/apk/release/app-release.apk`

### Using Gradle CLI
- **Run Unit & Robolectric Tests**:
  ```bash
  gradle :app:testDebugUnitTest
  ```
- **Build Debug APK**:
  ```bash
  gradle :app:assembleDebug
  ```
- **Build Release APK** (set `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD` env vars):
  ```bash
  KEYSTORE_PATH=/path/to/upload.jks STORE_PASSWORD=... KEY_PASSWORD=... gradle :app:assembleRelease
  ```
- **Build Signed Android App Bundle (.aab)**:
  ```bash
  KEYSTORE_PATH=/path/to/upload.jks STORE_PASSWORD=... KEY_PASSWORD=... gradle :app:bundleRelease
  ```
