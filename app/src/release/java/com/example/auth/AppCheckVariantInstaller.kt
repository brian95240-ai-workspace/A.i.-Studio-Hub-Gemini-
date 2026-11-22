package com.example.auth

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

private const val TAG = "AppCheckVariantRelease"

internal fun installVariantAppCheckProvider(context: Context, intent: Intent?) {
    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
        PlayIntegrityAppCheckProviderFactory.getInstance()
    )
    Log.d(TAG, "PlayIntegrityAppCheckProviderFactory installed (release variant).")
}
