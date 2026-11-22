package com.example.auth

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.FirebaseApp

private const val TAG = "AppCheckInit"

/**
 * Initializes FirebaseApp and installs the build-variant App Check provider:
 * - Debug builds (`src/debug`): `DebugAppCheckProviderFactory` (with optional launch Intent debug token)
 * - Release builds (`src/release`): `PlayIntegrityAppCheckProviderFactory` (debug provider absent from release classpath)
 */
fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        installVariantAppCheckProvider(context, intent)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}
