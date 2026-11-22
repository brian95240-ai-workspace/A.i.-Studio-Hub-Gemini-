package com.example.data.security

import android.util.Log
import com.example.data.local.LocalPreferencesStore

data class SecretMigrationReport(
    val migratedExternalApiKey: Boolean = false,
    val migratedGithubPat: Boolean = false,
    val deletedLegacyExternalKey: Boolean = false,
    val deletedLegacyGithubPat: Boolean = false,
    val errorMessage: String? = null
) {
    val anyMigrated: Boolean
        get() = migratedExternalApiKey || migratedGithubPat
}

/**
 * One-time verified migration that moves legacy plaintext `external_api_key` and `github_pat`
 * entries out of DataStore ([LocalPreferencesStore]) into the AES-256-GCM [EncryptedEnvVaultStore].
 *
 * Safety contract:
 * 1. Reads any legacy plaintext values from DataStore.
 * 2. Writes each non-blank secret into [EncryptedEnvVaultStore] (preserving any existing non-blank vault entry).
 * 3. Re-reads and decrypts the vault file directly from disk to confirm the value matches.
 * 4. Deletes the legacy DataStore entry ONLY AFTER verified disk read-back succeeds.
 * 5. Never logs secret values. Safe to call repeatedly (idempotent).
 */
object SecretVaultMigration {

    private const val TAG = "SecretVaultMigration"

    suspend fun migrateIfNeeded(
        prefsStore: LocalPreferencesStore,
        vaultStore: EncryptedEnvVaultStore
    ): SecretMigrationReport {
        val legacy = prefsStore.readLegacyPlaintextSecrets()
        if (!legacy.hasExternalApiKeyEntry && !legacy.hasGithubPatEntry) {
            return SecretMigrationReport()
        }

        var migratedExt = false
        var migratedGh = false
        var deletedExt = false
        var deletedGh = false
        val errors = mutableListOf<String>()

        if (legacy.hasExternalApiKeyEntry) {
            val rawExt = legacy.externalApiKey.trim()
            if (rawExt.isBlank()) {
                // Empty legacy key — safe to remove immediately
                prefsStore.deleteLegacyExternalApiKey()
                deletedExt = true
            } else {
                val existingVaultVal = vaultStore.getSecretValue(EncryptedEnvVaultStore.KEY_EXTERNAL_API_KEY)
                val targetValue = existingVaultVal ?: rawExt
                val writeRes = vaultStore.upsertManagedCredential(
                    key = EncryptedEnvVaultStore.KEY_EXTERNAL_API_KEY,
                    value = targetValue,
                    category = EnvSecretCategory.API_KEY,
                    target = EnvAutoFillTarget.EXTERNAL_ROUTER_API_KEY,
                    note = "Migrated from local preferences into AES-256-GCM encrypted vault",
                    preserveExistingIfPresent = true
                )
                if (writeRes.isSuccess) {
                    val verified = vaultStore.verifySecretPersistedOnDisk(
                        EncryptedEnvVaultStore.KEY_EXTERNAL_API_KEY,
                        targetValue
                    )
                    if (verified) {
                        prefsStore.deleteLegacyExternalApiKey()
                        migratedExt = true
                        deletedExt = true
                        Log.i(TAG, "Migrated legacy external API key to encrypted vault and removed plaintext key.")
                    } else {
                        errors.add("External API key read-back verification failed; legacy key retained.")
                        Log.w(TAG, "External API key read-back verification failed; retaining legacy key.")
                    }
                } else {
                    errors.add("External API key vault write failed; legacy key retained.")
                    Log.w(TAG, "External API key vault write failed.")
                }
            }
        }

        if (legacy.hasGithubPatEntry) {
            val rawGh = legacy.githubPat.trim()
            if (rawGh.isBlank()) {
                prefsStore.deleteLegacyGithubPat()
                deletedGh = true
            } else {
                val existingVaultVal = vaultStore.getSecretValue(EncryptedEnvVaultStore.KEY_GITHUB_PAT)
                val targetValue = existingVaultVal ?: rawGh
                val writeRes = vaultStore.upsertManagedCredential(
                    key = EncryptedEnvVaultStore.KEY_GITHUB_PAT,
                    value = targetValue,
                    category = EnvSecretCategory.TOKEN,
                    target = EnvAutoFillTarget.GITHUB_PAT,
                    note = "Migrated from local preferences into AES-256-GCM encrypted vault",
                    preserveExistingIfPresent = true
                )
                if (writeRes.isSuccess) {
                    val verified = vaultStore.verifySecretPersistedOnDisk(
                        EncryptedEnvVaultStore.KEY_GITHUB_PAT,
                        targetValue
                    )
                    if (verified) {
                        prefsStore.deleteLegacyGithubPat()
                        migratedGh = true
                        deletedGh = true
                        Log.i(TAG, "Migrated legacy GitHub PAT to encrypted vault and removed plaintext key.")
                    } else {
                        errors.add("GitHub PAT read-back verification failed; legacy key retained.")
                        Log.w(TAG, "GitHub PAT read-back verification failed; retaining legacy key.")
                    }
                } else {
                    errors.add("GitHub PAT vault write failed; legacy key retained.")
                    Log.w(TAG, "GitHub PAT vault write failed.")
                }
            }
        }

        return SecretMigrationReport(
            migratedExternalApiKey = migratedExt,
            migratedGithubPat = migratedGh,
            deletedLegacyExternalKey = deletedExt,
            deletedLegacyGithubPat = deletedGh,
            errorMessage = errors.takeIf { it.isNotEmpty() }?.joinToString(" ")
        )
    }
}
