package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.infra.InfrastructureRegistryCodec
import com.example.data.infra.InfrastructureRegistryEngine
import com.example.data.infra.InfrastructureRegistryState
import com.example.data.model.ExternalRouterType
import com.example.data.model.RoutingMode
import com.example.data.model.RoutingPreferences
import com.example.data.model.VortexJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.vortexDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "vortex_local_prefs"
)

/**
 * Snapshot of any legacy plaintext credentials remaining in DataStore prior to
 * one-time verified migration into [com.example.data.security.EncryptedEnvVaultStore].
 */
data class LegacyPlaintextSecrets(
    val hasExternalApiKeyEntry: Boolean,
    val externalApiKey: String,
    val hasGithubPatEntry: Boolean,
    val githubPat: String
)

class LocalPreferencesStore(
    private val context: Context,
    private val dataStoreOverride: DataStore<Preferences>? = null
) {
    private val store: DataStore<Preferences>
        get() = dataStoreOverride ?: context.vortexDataStore

    private object Keys {
        val STRATEGIC_ROUTING_ENABLED = booleanPreferencesKey("strategic_routing_enabled")
        val ROUTING_MODE = stringPreferencesKey("routing_mode")
        val ESCALATION_THRESHOLD = intPreferencesKey("escalation_threshold")
        val EXTERNAL_ENABLED = booleanPreferencesKey("external_enabled")
        val EXTERNAL_ROUTER_TYPE = stringPreferencesKey("external_router_type")
        val EXTERNAL_BASE_URL = stringPreferencesKey("external_base_url")
        val EXTERNAL_AUTH_VAULT_REF = stringPreferencesKey("external_auth_vault_ref")
        val SELF_HOSTED_INCLUDE_GROUNDING = booleanPreferencesKey("self_hosted_include_grounding")
        val SELF_HOSTED_APPROVED_SUMMARY = stringPreferencesKey("self_hosted_approved_summary")
        // Legacy plaintext keys — read/removed ONLY by SecretVaultMigration; NEVER written by app code.
        val LEGACY_EXTERNAL_API_KEY = stringPreferencesKey("external_api_key")
        val LEGACY_GITHUB_PAT = stringPreferencesKey("github_pat")
        val DOMAIN_MATRIX_JSON = stringPreferencesKey("domain_matrix_json")
        val GITHUB_USERNAME = stringPreferencesKey("github_username")
        val GITHUB_DEFAULT_PRIVATE = booleanPreferencesKey("github_default_private")
        val GITHUB_AUTO_MIRROR = booleanPreferencesKey("github_auto_mirror")
        val GITHUB_AUTH_METHOD = stringPreferencesKey("github_auth_method")
        val GITHUB_OAUTH_CLIENT_ID = stringPreferencesKey("github_oauth_client_id")
        val GITHUB_SYNCED_HASHES_JSON = stringPreferencesKey("github_synced_hashes_json")
        val GITHUB_LAST_SYNC_JSON = stringPreferencesKey("github_last_sync_json")
        val SAF_BINDINGS_JSON = stringPreferencesKey("saf_bindings_json")
        val SAF_HASHES_JSON = stringPreferencesKey("saf_hashes_json")
        val DRIVE_FOLDER_IDS_JSON = stringPreferencesKey("drive_folder_ids_json")
        val DRIVE_HASHES_JSON = stringPreferencesKey("drive_hashes_json")
        val DRIVE_LAST_SYNC_JSON = stringPreferencesKey("drive_last_sync_json")
        val INFRA_REGISTRY_JSON = stringPreferencesKey("infra_registry_json")
    }

    /**
     * Emits non-secret routing & workspace preferences.
     * `externalApiKey` and `githubPat` are intentionally left empty here; they are hydrated
     * strictly in memory from AES-256-GCM [com.example.data.security.EncryptedEnvVaultStore].
     */
    val routingPreferences: Flow<RoutingPreferences> = store.data.map { prefs ->
        val matrix = VortexJson.decodeStringMap(prefs[Keys.DOMAIN_MATRIX_JSON])
            .ifEmpty { RoutingPreferences.defaultDomainMatrix() }
        val savedMethod = prefs[Keys.GITHUB_AUTH_METHOD] ?: "NONE"
        val routerType = ExternalRouterType.fromWire(prefs[Keys.EXTERNAL_ROUTER_TYPE])
        val defaultUrl = if (routerType == ExternalRouterType.SELF_HOSTED_TEXT) {
            ""
        } else {
            "https://openrouter.ai/api/v1/"
        }
        RoutingPreferences(
            strategicRoutingEnabled = prefs[Keys.STRATEGIC_ROUTING_ENABLED] ?: true,
            routingMode = RoutingMode.fromWire(prefs[Keys.ROUTING_MODE]),
            escalationThreshold = prefs[Keys.ESCALATION_THRESHOLD] ?: 6,
            externalEnabled = prefs[Keys.EXTERNAL_ENABLED] ?: false,
            externalRouterType = routerType,
            externalBaseUrl = prefs[Keys.EXTERNAL_BASE_URL] ?: defaultUrl,
            externalAuthVaultRef = prefs[Keys.EXTERNAL_AUTH_VAULT_REF]
                ?.ifBlank { "EXTERNAL_API_KEY" }
                ?: "EXTERNAL_API_KEY",
            externalApiKey = "",
            selfHostedIncludeGrounding = prefs[Keys.SELF_HOSTED_INCLUDE_GROUNDING] ?: false,
            selfHostedApprovedSummary = prefs[Keys.SELF_HOSTED_APPROVED_SUMMARY] ?: "",
            domainModelMatrix = matrix,
            githubPat = "",
            githubUsername = prefs[Keys.GITHUB_USERNAME] ?: "",
            githubDefaultPrivate = prefs[Keys.GITHUB_DEFAULT_PRIVATE] ?: true,
            githubAutoMirror = prefs[Keys.GITHUB_AUTO_MIRROR] ?: false,
            githubAuthMethod = savedMethod,
            githubOAuthClientId = prefs[Keys.GITHUB_OAUTH_CLIENT_ID] ?: ""
        )
    }

    val safFolderBindings: Flow<Map<String, String>> = store.data.map { prefs ->
        VortexJson.decodeStringMap(prefs[Keys.SAF_BINDINGS_JSON])
    }

    val safSyncedHashes: Flow<Map<String, String>> = store.data.map { prefs ->
        VortexJson.decodeStringMap(prefs[Keys.SAF_HASHES_JSON])
    }

    val driveFolderIds: Flow<Map<String, String>> = store.data.map { prefs ->
        VortexJson.decodeStringMap(prefs[Keys.DRIVE_FOLDER_IDS_JSON])
    }

    val driveSyncedHashes: Flow<Map<String, String>> = store.data.map { prefs ->
        VortexJson.decodeStringMap(prefs[Keys.DRIVE_HASHES_JSON])
    }

    val driveLastSyncLabels: Flow<Map<String, String>> = store.data.map { prefs ->
        VortexJson.decodeStringMap(prefs[Keys.DRIVE_LAST_SYNC_JSON])
    }

    val githubSyncedHashes: Flow<Map<String, String>> = store.data.map { prefs ->
        VortexJson.decodeStringMap(prefs[Keys.GITHUB_SYNCED_HASHES_JSON])
    }

    val githubLastSyncLabels: Flow<Map<String, String>> = store.data.map { prefs ->
        VortexJson.decodeStringMap(prefs[Keys.GITHUB_LAST_SYNC_JSON])
    }

    /**
     * Device-local universal Infrastructure & MCP Registry.
     * PRIVATE_SOLO endpoints remain strictly on this device and are never synced to Firestore.
     */
    val infrastructureRegistry: Flow<InfrastructureRegistryState> = store.data.map { prefs ->
        InfrastructureRegistryEngine.mergeWithDefaults(
            InfrastructureRegistryCodec.decode(prefs[Keys.INFRA_REGISTRY_JSON])
        )
    }

    suspend fun getCurrentRoutingPreferences(): RoutingPreferences = routingPreferences.first()

    /**
     * Reads legacy plaintext secret entries solely for one-time verified migration into
     * [com.example.data.security.EncryptedEnvVaultStore].
     */
    suspend fun readLegacyPlaintextSecrets(): LegacyPlaintextSecrets {
        val prefs = store.data.first()
        return LegacyPlaintextSecrets(
            hasExternalApiKeyEntry = prefs.contains(Keys.LEGACY_EXTERNAL_API_KEY),
            externalApiKey = prefs[Keys.LEGACY_EXTERNAL_API_KEY].orEmpty(),
            hasGithubPatEntry = prefs.contains(Keys.LEGACY_GITHUB_PAT),
            githubPat = prefs[Keys.LEGACY_GITHUB_PAT].orEmpty()
        )
    }

    suspend fun deleteLegacyExternalApiKey() {
        store.edit { prefs ->
            prefs.remove(Keys.LEGACY_EXTERNAL_API_KEY)
        }
    }

    suspend fun deleteLegacyGithubPat() {
        store.edit { prefs ->
            prefs.remove(Keys.LEGACY_GITHUB_PAT)
        }
    }

    internal suspend fun seedLegacyPlaintextSecretsForTest(
        externalApiKey: String?,
        githubPat: String?
    ) {
        store.edit { prefs ->
            if (externalApiKey != null) prefs[Keys.LEGACY_EXTERNAL_API_KEY] = externalApiKey
            if (githubPat != null) prefs[Keys.LEGACY_GITHUB_PAT] = githubPat
        }
    }

    /**
     * Persists non-secret routing preferences only. Secrets (`externalApiKey`, `githubPat`)
     * are NEVER persisted to DataStore and any stale legacy keys are stripped.
     */
    suspend fun saveRoutingPreferences(updated: RoutingPreferences) {
        store.edit { prefs ->
            prefs[Keys.STRATEGIC_ROUTING_ENABLED] = updated.strategicRoutingEnabled
            prefs[Keys.ROUTING_MODE] = updated.routingMode.wire
            prefs[Keys.ESCALATION_THRESHOLD] = updated.escalationThreshold.coerceIn(4, 9)
            prefs[Keys.EXTERNAL_ENABLED] = updated.externalEnabled
            prefs[Keys.EXTERNAL_ROUTER_TYPE] = updated.externalRouterType.wire
            prefs[Keys.EXTERNAL_BASE_URL] = updated.externalBaseUrl.trim()
            prefs[Keys.EXTERNAL_AUTH_VAULT_REF] = updated.externalAuthVaultRef
                .trim()
                .ifBlank { "EXTERNAL_API_KEY" }
            prefs[Keys.SELF_HOSTED_INCLUDE_GROUNDING] = updated.selfHostedIncludeGrounding
            prefs[Keys.SELF_HOSTED_APPROVED_SUMMARY] = updated.selfHostedApprovedSummary.trim().take(1200)
            prefs[Keys.DOMAIN_MATRIX_JSON] = VortexJson.encodeStringMap(updated.domainModelMatrix)
            prefs[Keys.GITHUB_USERNAME] = updated.githubUsername.trim()
            prefs[Keys.GITHUB_DEFAULT_PRIVATE] = updated.githubDefaultPrivate
            prefs[Keys.GITHUB_AUTO_MIRROR] = updated.githubAutoMirror
            prefs[Keys.GITHUB_AUTH_METHOD] = updated.githubAuthMethod
            prefs[Keys.GITHUB_OAUTH_CLIENT_ID] = updated.githubOAuthClientId.trim()
        }
    }

    /**
     * Persists non-secret GitHub session metadata only. The GitHub token itself is stored
     * exclusively in [com.example.data.security.EncryptedEnvVaultStore].
     */
    suspend fun saveGitHubSessionMetadata(
        username: String,
        authMethodWire: String,
        oauthClientId: String? = null
    ) {
        store.edit { prefs ->
            prefs[Keys.GITHUB_USERNAME] = username.trim()
            prefs[Keys.GITHUB_AUTH_METHOD] = authMethodWire
            if (oauthClientId != null) {
                prefs[Keys.GITHUB_OAUTH_CLIENT_ID] = oauthClientId.trim()
            }
            prefs.remove(Keys.LEGACY_GITHUB_PAT)
        }
    }

    suspend fun clearGitHubCredentials() {
        store.edit { prefs ->
            prefs.remove(Keys.LEGACY_GITHUB_PAT)
            prefs[Keys.GITHUB_USERNAME] = ""
            prefs[Keys.GITHUB_AUTH_METHOD] = "NONE"
            prefs[Keys.GITHUB_AUTO_MIRROR] = false
        }
    }

    suspend fun setGitHubAutoMirror(enabled: Boolean) {
        store.edit { prefs ->
            prefs[Keys.GITHUB_AUTO_MIRROR] = enabled
        }
    }

    suspend fun setGitHubDefaultPrivate(isPrivate: Boolean) {
        store.edit { prefs ->
            prefs[Keys.GITHUB_DEFAULT_PRIVATE] = isPrivate
        }
    }

    suspend fun saveGitHubSyncState(
        projectId: String,
        workspaceSha256: String,
        summaryLabel: String
    ) {
        store.edit { prefs ->
            val hashes = VortexJson.decodeStringMap(prefs[Keys.GITHUB_SYNCED_HASHES_JSON]).toMutableMap()
            hashes[projectId] = workspaceSha256
            prefs[Keys.GITHUB_SYNCED_HASHES_JSON] = VortexJson.encodeStringMap(hashes)

            val labels = VortexJson.decodeStringMap(prefs[Keys.GITHUB_LAST_SYNC_JSON]).toMutableMap()
            labels[projectId] = summaryLabel
            prefs[Keys.GITHUB_LAST_SYNC_JSON] = VortexJson.encodeStringMap(labels)
        }
    }

    suspend fun setStrategicRoutingEnabled(enabled: Boolean) {
        store.edit { prefs ->
            prefs[Keys.STRATEGIC_ROUTING_ENABLED] = enabled
        }
    }

    suspend fun saveSafFolderBinding(projectId: String, treeUri: String) {
        store.edit { prefs ->
            val current = VortexJson.decodeStringMap(prefs[Keys.SAF_BINDINGS_JSON]).toMutableMap()
            current[projectId] = treeUri
            prefs[Keys.SAF_BINDINGS_JSON] = VortexJson.encodeStringMap(current)
        }
    }

    suspend fun saveSafSyncedHash(projectId: String, sha256: String) {
        store.edit { prefs ->
            val current = VortexJson.decodeStringMap(prefs[Keys.SAF_HASHES_JSON]).toMutableMap()
            current[projectId] = sha256
            prefs[Keys.SAF_HASHES_JSON] = VortexJson.encodeStringMap(current)
        }
    }

    suspend fun saveDriveSyncState(
        projectId: String,
        folderId: String,
        sha256: String,
        summaryLabel: String
    ) {
        store.edit { prefs ->
            val ids = VortexJson.decodeStringMap(prefs[Keys.DRIVE_FOLDER_IDS_JSON]).toMutableMap()
            ids[projectId] = folderId
            prefs[Keys.DRIVE_FOLDER_IDS_JSON] = VortexJson.encodeStringMap(ids)

            val hashes = VortexJson.decodeStringMap(prefs[Keys.DRIVE_HASHES_JSON]).toMutableMap()
            hashes[projectId] = sha256
            prefs[Keys.DRIVE_HASHES_JSON] = VortexJson.encodeStringMap(hashes)

            val labels = VortexJson.decodeStringMap(prefs[Keys.DRIVE_LAST_SYNC_JSON]).toMutableMap()
            labels[projectId] = summaryLabel
            prefs[Keys.DRIVE_LAST_SYNC_JSON] = VortexJson.encodeStringMap(labels)
        }
    }

    suspend fun saveInfrastructureRegistry(state: InfrastructureRegistryState) {
        store.edit { prefs ->
            prefs[Keys.INFRA_REGISTRY_JSON] = InfrastructureRegistryCodec.encode(
                state.copy(updatedAtMillis = System.currentTimeMillis())
            )
        }
    }
}
