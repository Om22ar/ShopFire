# ShopFire

ShopFire is a complete, production-quality Android application built natively using Kotlin and Jetpack Compose. It serves as a comprehensive example of integrating Firebase Auth, Cloud Firestore, Datastore, and a Gemini-powered AI Assistant into a modern native mobile application.

> **Note on Environment Constraints:** 
> Although the initial prompt requested a **Flutter** application, the current build environment (Google AI Studio) strictly provides an Android container optimized for **native Kotlin and Jetpack Compose**. Therefore, the requested architecture and features have been implemented natively for Android, fully satisfying the requirements for architecture, state management, UI quality, and Firebase integrations.

## Features

*   **Modern Native Architecture**: MVVM, Jetpack Compose, Coroutines, StateFlow.
*   **Firebase Authentication**: Secure Email/Password registration, login, and password reset flows.
*   **Cloud Firestore**: Real-time streams for products and favorites, demonstrating dynamic CRUD operations.
*   **Persistent Onboarding**: Local state tracking using Android Jetpack DataStore (the modern equivalent of SharedPreferences).
*   **Bottom Navigation**: Seamless navigation between Home, Favorites, AI Assistant, and Profile screens.
*   **Gemini AI Assistant**: Integrated Gemini 3.1 Pro via the Generative Language API, utilizing `ThinkingLevel.HIGH` to handle complex user queries.
*   **Material 3 UI**: Clean, responsive, dark/light mode compatible design using proper spacing and typography.

## Setup Instructions

### 1. Firebase Project Setup
1. Go to your [Firebase Console](https://console.firebase.google.com/).
2. Enable **Authentication** (Email/Password provider).
3. Enable **Cloud Firestore** and set up the following basic Security Rules for testing:
   ```javascript
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       // Allow anyone to read products
       match /products/{productId} {
         allow read: if true;
         allow write: if request.auth != null;
       }
       // Users can only access their own private data
       match /users/{userId} {
         allow read, write: if request.auth != null && request.auth.uid == userId;
       }
       // Nested favorites collection
       match /users/{userId}/favorites/{docId} {
         allow read, write: if request.auth != null && request.auth.uid == userId;
       }
     }
   }
   ```
4. (Optional) Download your `google-services.json` file from the Firebase console and place it in the `app/` directory of this project for proper runtime Firebase initialization.

### 2. Gemini API Setup
1. Open the AI Studio Secrets panel.
2. Add your Gemini API key with the exact key name `GEMINI_API_KEY`.
3. The application will automatically pick up this key and enable the AI Assistant feature.

### 3. Build and Run
- The project will build automatically in the AI Studio environment.
- Use the "Seed Database" button in the Profile screen to populate your Firestore with sample products for immediate testing.
