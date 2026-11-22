package com.example.auth

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

sealed interface DriveAuthResult {
    data class Authorized(val accessToken: String) : DriveAuthResult
    data class NeedsConsent(val pendingIntent: PendingIntent) : DriveAuthResult
    data class Error(val message: String) : DriveAuthResult
}

object GoogleDriveAuthManager {
    private const val TAG = "DriveAuth"
    const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

    suspend fun authorizeDrive(context: Context): DriveAuthResult {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
            .build()

        return try {
            val result = Identity.getAuthorizationClient(context)
                .authorize(request)
                .await()

            when {
                result.hasResolution() && result.pendingIntent != null -> {
                    DriveAuthResult.NeedsConsent(result.pendingIntent!!)
                }
                !result.accessToken.isNullOrBlank() -> {
                    DriveAuthResult.Authorized(result.accessToken!!)
                }
                else -> {
                    DriveAuthResult.Error("Google Drive authorization did not return an access token.")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Google Drive authorization failed", e)
            DriveAuthResult.Error(e.message ?: "Google Drive authorization failed.")
        }
    }

    fun extractTokenFromIntent(context: Context, data: Intent?): DriveAuthResult {
        if (data == null) return DriveAuthResult.Error("Google Drive consent was cancelled.")
        return try {
            val result = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(data)
            val token = result.accessToken
            if (!token.isNullOrBlank()) {
                DriveAuthResult.Authorized(token)
            } else {
                DriveAuthResult.Error("No Google Drive access token granted.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed extracting Google Drive token from intent", e)
            DriveAuthResult.Error(e.message ?: "Unable to complete Google Drive authorization.")
        }
    }
}
