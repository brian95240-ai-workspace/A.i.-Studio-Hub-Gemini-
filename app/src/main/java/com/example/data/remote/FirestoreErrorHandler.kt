package com.example.data.remote

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write")
}

object FirestoreErrorHandler {
    private const val TAG = "FirestoreError"

    fun handleFirestoreError(
        exception: Exception,
        operationType: OperationType,
        path: String?
    ): IllegalStateException {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val providers = JSONArray()
        currentUser?.providerData?.forEach { info ->
            providers.put(
                JSONObject().apply {
                    put("providerId", info.providerId)
                    put("displayName", info.displayName ?: JSONObject.NULL)
                }
            )
        }
        val payload = JSONObject().apply {
            put("error", exception.message ?: exception.toString())
            put("operationType", operationType.value)
            put("path", path ?: JSONObject.NULL)
            put(
                "authInfo",
                JSONObject().apply {
                    put("userId", currentUser?.uid ?: JSONObject.NULL)
                    put("emailVerified", currentUser?.isEmailVerified ?: false)
                    put("providerCount", providers.length())
                }
            )
        }.toString()

        Log.e(TAG, payload, exception)
        return IllegalStateException(payload, exception)
    }
}
