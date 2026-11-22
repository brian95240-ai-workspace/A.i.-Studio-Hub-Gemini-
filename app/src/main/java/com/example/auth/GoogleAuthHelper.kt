package com.example.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed interface AuthOutcome {
    data class Success(val user: FirebaseUser) : AuthOutcome
    data object Cancelled : AuthOutcome
    data class Error(val message: String) : AuthOutcome
}

object GoogleAuthHelper {
    private const val TAG = "Auth"

    suspend fun attemptAutoSignIn(activityContext: Context): AuthOutcome {
        val current = FirebaseAuth.getInstance().currentUser
        if (current != null) return AuthOutcome.Success(current)

        val webClientId = activityContext.getString(R.string.default_web_client_id)
        val credentialManager = CredentialManager.create(activityContext)
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(true)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        return try {
            val response = credentialManager.getCredential(activityContext, request)
            signInWithCredential(response.credential)
        } catch (e: NoCredentialException) {
            Log.d(TAG, "No previously authorized Google account for silent sign-in")
            AuthOutcome.Cancelled
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Silent Google Sign-In cancelled", e)
            AuthOutcome.Cancelled
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Silent Google Sign-In skipped", e)
            AuthOutcome.Cancelled
        }
    }

    suspend fun signInInteractive(activityContext: Context): AuthOutcome {
        val webClientId = activityContext.getString(R.string.default_web_client_id)
        val credentialManager = CredentialManager.create(activityContext)
        val option = GetSignInWithGoogleOption.Builder(webClientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        return try {
            val response = credentialManager.getCredential(activityContext, request)
            signInWithCredential(response.credential)
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "User cancelled Google Sign-In", e)
            AuthOutcome.Cancelled
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Google Sign-In credential error", e)
            AuthOutcome.Error(e.errorMessage?.toString() ?: e.message ?: "Google Sign-In failed")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected Google Sign-In failure", e)
            AuthOutcome.Error(e.message ?: "Sign-in failed. Please try again.")
        }
    }

    private suspend fun signInWithCredential(credential: androidx.credentials.Credential): AuthOutcome {
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)
            val result = FirebaseAuth.getInstance().signInWithCredential(firebaseCred).await()
            val user = result.user
            return if (user != null) {
                AuthOutcome.Success(user)
            } else {
                AuthOutcome.Error("Authentication succeeded but user profile was unavailable.")
            }
        }
        return AuthOutcome.Error("Unsupported credential type returned.")
    }

    suspend fun signOut(activityContext: Context) {
        withContext(NonCancellable) {
            try {
                FirebaseAuth.getInstance().signOut()
                CredentialManager.create(activityContext)
                    .clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state on sign-out", e)
            }
        }
    }
}
