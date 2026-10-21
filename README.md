<div align="center">
  <img src="https://raw.githubusercontent.com/Bahlenx/Accommodation-seeking-app/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" alt="iKhaya app icon" width="120" />
  <h1>iKhaya Accommodation App</h1>
  <p><strong>Find accommodation. Connect with landlords. Manage the rental journey.</strong></p>
  <p>A native Android application designed to bring accommodation discovery and rental communication into one place.</p>
  <p>
    <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=flat&logo=android&logoColor=white" alt="Platform: Android" />
    <img src="https://img.shields.io/badge/Language-Java-orange?style=flat&logo=openjdk&logoColor=white" alt="Language: Java" />
    <img src="https://img.shields.io/badge/Backend-Supabase-3ECF8E?style=flat&logo=supabase&logoColor=white" alt="Backend: Supabase" />
    <img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License: MIT" />
  </p>
</div>

---

## Table of Contents

- [About the Project](#about-the-project)
- [Project Goals](#project-goals)
- [Features](#features)
- [Technology Stack](#technology-stack)
- [Application Structure](#application-structure)
- [Requirements](#requirements)
- [Getting Started](#getting-started)
- [Supabase Configuration](#supabase-configuration)
- [Build and Run](#build-and-run)
- [Screenshots](#screenshots)
- [Security Notes](#security-notes)
- [Testing](#testing)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)
- [Contact](#contact)

## About the Project

**iKhaya** is a final-year Android project that aims to make accommodation discovery and rental-related communication easier to manage. It provides workflows for tenants, landlords, and administrators, including property search, listing management, rental applications, enquiries, viewings, and messaging.

The mobile application is built with native Android technologies using Java and XML layouts. **Supabase is the backend platform** used by the application for authentication, REST-based database access, and property-image storage. The app also includes property-location map views and language settings.

This repository contains the Android application source code. Running the app against a backend requires a correctly configured Supabase project and the database schema, access policies, and storage configuration expected by the application.

## Project Goals

- Help accommodation seekers discover and compare available properties.
- Provide landlords with tools to create and manage property listings.
- Support rental workflows such as applications, enquiries, saved listings, and viewing arrangements.
- Provide distinct experiences for tenants, landlords, and administrators.
- Make property locations and essential account settings accessible in the mobile app.

## Features

### Accounts and authentication

- Registration and sign-in screens.
- Six-digit one-time password (OTP) verification with email and SMS options in the verification flow.
- Password-based sign-in, password recovery, and password-change screens.
- Supabase Auth integration for verification, authentication sessions, access tokens, and refresh tokens.
- Session persistence and token-refresh support.

OTP delivery depends on the email and/or SMS providers configured in your Supabase project.

### Tenant experience

- Home feed and accommodation discovery.
- Property search results and filtering controls.
- Property details and location display.
- Saved listings and favourites.
- Rental application and enquiry workflows.
- Viewing-related screens and tenant profile/settings pages.

### Landlord experience

- Landlord dashboard.
- Create, edit, and manage property listings.
- Upload property images to Supabase Storage.
- Review application and enquiry workflows.
- Landlord profile, personal-information, notification, and payment-record screens.

### Administrator experience

- Administrative dashboard.
- User-management screens.
- Pending-listing queue and active-listing screens.
- Listing inspection and review workflow.

Administrator permissions, listing approvals, and access restrictions must be enforced by the backend as well as reflected in the user interface.

### Messaging, location, and settings

- Direct messaging and a community conversation flow.
- Message retrieval and sending through Supabase REST endpoints.
- OpenStreetMap-based map views for properties.
- Android location permissions for location-related features.
- Language settings and localised text resources included in the project.
- Notifications and problem-reporting screens.

## Technology Stack

| Area | Technologies |
|---|---|
| Android application | Native Android, Java, XML layouts |
| UI components | AndroidX AppCompat, AndroidX Activity, ConstraintLayout, Material Components, RecyclerView |
| Backend | Supabase Auth, PostgreSQL, REST/PostgREST, Supabase Storage |
| HTTP and JSON | OkHttp, `HttpURLConnection`, `org.json` |
| Maps and location | OpenStreetMap using osmdroid, Android location permissions |
| Build system | Gradle wrapper, Kotlin DSL build scripts, Gradle version catalog |
| Testing libraries | JUnit and AndroidX Espresso |

The app's checked-in build configuration specifies Android Gradle Plugin **9.4.1**, Gradle wrapper **9.6.0**, compile SDK **37**, target SDK **37**, minimum SDK **29**, and Java source/target compatibility **11**. Use an Android Studio release compatible with the configured Android Gradle Plugin and a compatible JDK for Gradle.

## Application Structure

The application follows an activity-centred Android structure. Activities handle individual screens, while helper classes centralise backend requests, session handling, and application logic.

```text
Accommodation-seeking-app/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── java/com/example/ikhaya_accommodation_seeking_app/
│           │   ├── OuterMainActivity.java
│           │   ├── RegistrationActivity.java
│           │   ├── LoginActivity.java
│           │   ├── OtpActivity.java
│           │   ├── PersonalActivity.java
│           │   ├── HomeActivity.java
│           │   ├── SearchResultsActivity.java
│           │   ├── PropertyDetailsActivity.java
│           │   ├── SavedListingsActivity.java
│           │   ├── AddListingActivity.java
│           │   ├── EditListingActivity.java
│           │   ├── MyListingsActivity.java
│           │   ├── LandlordDashboardActivity.java
│           │   ├── AdminDashboardActivity.java
│           │   ├── ChatActivity.java
│           │   ├── UserSelectionActivity.java
│           │   ├── ViewingsActivity.java
│           │   ├── SupabaseHelper.java
│           │   ├── ApplicationManager.java
│           │   ├── ListingManager.java
│           │   ├── UserProfileLoader.java
│           │   ├── IdSecurityHelper.java
│           │   └── ... additional activities, models, and adapters
│           └── res/
│               ├── drawable/   # icons, backgrounds, and visual assets
│               ├── layout/     # XML layouts for application screens
│               ├── mipmap-*/   # launcher icons
│               └── values*/    # strings, colours, themes, language resources
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
├── gradlew.bat
├── README.md
└── LICENSE
```

### Key implementation classes

| Class | Responsibility |
|---|---|
| `SupabaseHelper.java` | Supabase HTTP requests, session values, token refresh, JSON request/response helpers, and Storage requests |
| `ApplicationManager.java` | Application-related operations |
| `ListingManager.java` | Listing helper and locally stored listing data |
| `UserProfileLoader.java` | Profile-loading support for account workflows |
| `IdSecurityHelper.java` | South African ID-number validation and related helper routines |
| `AnalyticsHelper.java` | Analytics-related helper functionality |

The source package contains additional activities, adapters, models, and XML layouts for the tenant, landlord, and administrator screens.

## Requirements

Before building the project, install or configure:

- **Android Studio** compatible with the Android Gradle Plugin version configured in the repository.
- A compatible **JDK** for running Gradle.
- Android SDK Platform 37 and the SDK/build tools required by the project.
- Git.
- An Android emulator or physical device running **Android 10 (API 29) or newer**.
- A Supabase project with Authentication, database/API access, and Storage configured.

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/Bahlenx/Accommodation-seeking-app.git
cd Accommodation-seeking-app
```

### 2. Open the Android project

1. Launch Android Studio.
2. Select **Open** and choose the cloned `Accommodation-seeking-app` directory.
3. Allow Android Studio to synchronise the Gradle project.
4. Install any Android SDK components requested by the IDE.

Use the Gradle wrapper included in the repository rather than relying on a separately installed Gradle version.

### 3. Configure Supabase

Follow the [Supabase Configuration](#supabase-configuration) instructions below. The app depends on Supabase project settings, database tables, access policies, and Storage configuration matching the requests made by the Android client.

### 4. Build and launch

Connect an emulator or Android device, select the app run configuration in Android Studio, and press **Run**.

You can also build a debug APK from the repository root.

On macOS/Linux:

```bash
./gradlew assembleDebug
```

On Windows:

```bat
gradlew.bat assembleDebug
```

The debug APK is normally generated under `app/build/outputs/apk/debug/`.

## Supabase Configuration

The application uses Supabase for authentication, REST-based database access, and image storage. Configure the project before testing features that rely on remote data.

### 1. Create or select a Supabase project

Open the [Supabase dashboard](https://supabase.com/dashboard) and create a project or select one you already maintain.

### 2. Configure application credentials

The current client configuration is in:

```text
app/src/main/java/com/example/ikhaya_accommodation_seeking_app/SupabaseHelper.java
```

In that file, locate the `SUPABASE_URL` and `SUPABASE_KEY` constants and set them to the URL and publishable/anonymous client key for the Supabase project you intend to use.

```java
private static final String SUPABASE_URL = "https://YOUR_PROJECT.supabase.co";
private static final String SUPABASE_KEY = "YOUR_SUPABASE_PUBLISHABLE_OR_ANON_KEY";
```

The values above are examples, not working credentials. Do not use a privileged server key in the Android app.

### 3. Configure authentication

In your Supabase project:

1. Enable and configure the authentication methods used by the app.
2. Configure email delivery/SMTP for email OTP and password-recovery messages.
3. Configure an SMS provider if you intend to use the SMS OTP option.
4. Test registration, OTP verification, sign-in, password recovery, and session refresh with a development account.

OTP delivery and behaviour depend on your Supabase Auth settings and provider configuration.

### 4. Configure database tables and access policies

The app makes REST requests for profile and role data, property listings, applications, enquiries, viewings, and messages. Ensure that your Supabase schema contains the tables and columns expected by those requests.

Configure Row Level Security (RLS) and database policies so that users can access only the records they are permitted to access. Enforce role checks and administrative operations on the backend; changing the visible Android screens alone is not sufficient security.

This repository does not include a verified, complete database migration script. A new Supabase project may need its schema and policies recreated to match the existing client implementation.

### 5. Configure property-image storage

The app uses a Supabase Storage bucket named `property-images` for listing images. Create the bucket if needed and configure upload and read policies for your use case. Avoid opening write permissions to all users unless that is an intentional and properly controlled design decision.

## Screenshots

The repository includes launcher artwork and XML layouts, but it does not currently have a curated screenshot gallery. For a stronger project presentation, add screenshots from a running emulator or device to a folder such as `docs/screenshots/` and include the tenant home screen, property details, authentication flow, and landlord dashboard here.

For example, after adding those assets, they can be embedded like this:

```markdown
![Tenant home screen](docs/screenshots/tenant-home.png)
![Property details screen](docs/screenshots/property-details.png)
```

Replace these example paths with the real screenshot filenames you add to the repository.

## Security Notes

- Never commit privileged keys, server credentials, SMTP passwords, or real user data.
- Use only the intended publishable/anonymous client key in the mobile app. Never embed a Supabase `service_role` key in an Android application.
- Protect database records and stored files with appropriate RLS and Storage policies.
- Remember that values packaged into an Android app can be inspected. A client key is not a substitute for backend access control.
- Review client-side handling of personal information, including South African ID numbers, before deploying with real users. Do not rely on a hard-coded application-wide secret to protect sensitive data in a distributed app.
- Use test accounts and non-production data during development.

## Testing

The project includes JUnit and AndroidX Espresso test dependencies, along with a starter unit-test scaffold. Not every user workflow should be assumed to have automated coverage.

Before a release, test at least:

- Registration, OTP verification, sign-in, password recovery, and expired-session handling.
- Listing creation, editing, filtering, and image upload.
- Tenant applications, landlord enquiries, and viewing workflows.
- Access boundaries between tenant, landlord, and administrator roles.
- Message sending, message loading, and backend/network failure handling.

Run the existing local test task from the repository root:

```bash
./gradlew test
```

On Windows, use `gradlew.bat test`.

## Roadmap

The following items are suggestions for future development, not a statement that they are already implemented:

- [ ] Add a curated screenshot gallery or a short product walkthrough.
- [ ] Expand automated tests for authentication, listings, applications, and messaging.
- [ ] Document and version the Supabase schema and Row Level Security policies.
- [ ] Review configuration management for the Supabase URL and client key.
- [ ] Add push notifications for messages, enquiries, and viewing updates.
- [ ] Improve error handling, accessibility, and behaviour on slow or unavailable networks.

## Contributing

Bug reports, suggestions, and contributions are welcome. For substantial changes, open an issue first to discuss the proposed work.

When submitting a pull request:

1. Describe the problem and the changes made.
2. Include screenshots for interface changes where practical.
3. Test the affected workflow and note any required Supabase configuration.
4. Do not include personal user data, real credentials, or privileged keys in commits.

## License

This project is distributed under the **MIT License**. See the [`LICENSE`](LICENSE) file for the full licence text.

## Contact

For questions, feedback, or project enquiries, contact the iKhaya Accommodation App team:

**Email:** [ikhayaaccom.admin08@gmail.com](mailto:ikhayaaccom.admin08@gmail.com)

**Repository:** [Bahlenx/Accommodation-seeking-app](https://github.com/Bahlenx/Accommodation-seeking-app)
