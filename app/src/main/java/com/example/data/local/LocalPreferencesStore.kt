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
import com.example.data.model.RoutingMode
import com.example.data.model.RoutingPreferences
import com.example.data.model.VortexJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.vortexDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "vortex_local_prefs"
)

class LocalPreferencesStore(private val context: Context) {

    private object Keys {
        val STRATEGIC_ROUTING_ENABLED = booleanPreferencesKey("strategic_routing_enabled")
        val ROUTING_MODE = stringPreferencesKey("routing_mode")
        val ESCALATION_THRESHOLD = intPreferencesKey("escalation_threshold")
        val EXTERNAL_ENABLED = booleanPreferencesKey("external_enabled")
        val EXTERNAL_BASE_URL = stringPreferencesKey("external_base_url")
        val EXTERNAL_API_KEY = stringPreferencesKey("external_api_key")
        val DOMAIN_MATRIX_JSON = stringPreferencesKey("domain_matrix_json")
        val GITHUB_PAT = stringPreferencesKey("github_pat")
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

    val routingPreferences: Flow<RoutingPreferences> = context.vortexDataStore.data.map { prefs ->
        val matrix = VortexJson.decodeStringMap(prefs[Keys.DOMAIN_MATRIX_JSON])
            .ifEmpty { RoutingPreferences.defaultDomainMatrix() }
        val savedPat = prefs[Keys.GITHUB_PAT] ?: ""
        val savedMethod = prefs[Keys.GITHUB_AUTH_METHOD]
            ?: if (savedPat.isNotBlank()) "PAT" else "NONE"
        RoutingPreferences(
            strategicRoutingEnabled = prefs[Keys.STRATEGIC_ROUTING_ENABLED] ?: true,
            routingMode = RoutingMode.fromWire(prefs[Keys.ROUTING_MODE]),
            escalationThreshold = prefs[Keys.ESCALATION_THRESHOLD] ?: 6,
            externalEnabled = prefs[Keys.EXTERNAL_ENABLED] ?: false,
            externalBaseUrl = prefs[Keys.EXTERNAL_BASE_URL] ?: "https://openrouter.ai/api/v1/",
            externalApiKey = prefs[Keys.EXTERNAL_API_KEY] ?: "",
            domainModelMatrix = matrix,
            githubPat = savedPat,
            githubUsername = prefs[Keys.GITHUB_USERNAME] ?: "",
            githubDefaultPrivate = prefs[Keys.GITHUB_DEFAULT_PRIVATE] ?: true,
            githubAutoMirror = prefs[Keys.GITHUB_AUTO_MIRROR] ?: false,
            githubAuthMethod = savedMethod,
            githubOAuthClientId = prefs[Keys.GITHUB_OAUTH_CLIENT_ID] ?: ""
        )
    }

    val safFolderBindings: Flow<Map<String, String>> = context.vortexDataStore.data.map { prefs ->
        VortexJson.decodeStringMap(prefs[Keys.SAF_BINDINGS_JSON])
    }

    val safSyncedHashes: Flow<Map<String, String>> = context.vortexDataStore.data.map { prefs ->
        VortexJson.decodeStringMap(prefs[Keys.SAF_HASHES_JSON])
    }

    val driveFolderIds: Flow<Map<String, String>> = context.vortexDataStore.data.map { prefs ->
        VortexJson.decodeStringMap(prefs[Keys.DRIVE_FOLDER_IDS_JSON])
    }

    val driveSyncedHashes: Flow<Map<String, String>> = context.vortexDataStore.data.map { prefs ->
        VortexJson.decodeStringMap(prefs[Keys.DRIVE_HASHES_JSON])
    }

    val driveLastSyncLabels: Flow<Map<String, String>> = context.vortexDataStore.data.map { prefs ->
        VortexJson.decodeStringMap(prefs[Keys.DRIVE_LAST_SYNC_JSON])
    }

    val githubSyncedHashes: Flow<Map<String, String>> = context.vortexDataStore.data.map { prefs ->
        VortexJson.decodeStringMap(prefs[Keys.GITHUB_SYNCED_HASHES_JSON])
    }

    val githubLastSyncLabels: Flow<Map<String, String>> = context.vortexDataStore.data.map { prefs ->
        VortexJson.decodeStringMap(prefs[Keys.GITHUB_LAST_SYNC_JSON])
    }

    /**
     * Device-local universal Infrastructure & MCP Registry.
     * PRIVATE_SOLO endpoints remain strictly on this device and are never synced to Firestore.
     */
    val infrastructureRegistry: Flow<InfrastructureRegistryState> = context.vortexDataStore.data.map { prefs ->
        InfrastructureRegistryEngine.mergeWithDefaults(
            InfrastructureRegistryCodec.decode(prefs[Keys.INFRA_REGISTRY_JSON])
        )
    }

    suspend fun getCurrentRoutingPreferences(): RoutingPreferences = routingPreferences.first()

    suspend fun saveRoutingPreferences(updated: RoutingPreferences) {
        context.vortexDataStore.edit { prefs ->
            prefs[Keys.STRATEGIC_ROUTING_ENABLED] = updated.strategicRoutingEnabled
            prefs[Keys.ROUTING_MODE] = updated.routingMode.wire
            prefs[Keys.ESCALATION_THRESHOLD] = updated.escalationThreshold.coerceIn(4, 9)
            prefs[Keys.EXTERNAL_ENABLED] = updated.externalEnabled
            prefs[Keys.EXTERNAL_BASE_URL] = updated.externalBaseUrl.trim()
            prefs[Keys.EXTERNAL_API_KEY] = updated.externalApiKey.trim()
            prefs[Keys.DOMAIN_MATRIX_JSON] = VortexJson.encodeStringMap(updated.domainModelMatrix)
            prefs[Keys.GITHUB_PAT] = updated.githubPat.trim()
            prefs[Keys.GITHUB_USERNAME] = updated.githubUsername.trim()
            prefs[Keys.GITHUB_DEFAULT_PRIVATE] = updated.githubDefaultPrivate
            prefs[Keys.GITHUB_AUTO_MIRROR] = updated.githubAutoMirror
            prefs[Keys.GITHUB_AUTH_METHOD] = updated.githubAuthMethod
            prefs[Keys.GITHUB_OAUTH_CLIENT_ID] = updated.githubOAuthClientId.trim()
        }
    }

    suspend fun saveGitHubCredentials(
        token: String,
        username: String,
        authMethodWire: String,
        oauthClientId: String? = null
    ) {
        context.vortexDataStore.edit { prefs ->
            prefs[Keys.GITHUB_PAT] = token.trim()
            prefs[Keys.GITHUB_USERNAME] = username.trim()
            prefs[Keys.GITHUB_AUTH_METHOD] = authMethodWire
            if (oauthClientId != null) {
                prefs[Keys.GITHUB_OAUTH_CLIENT_ID] = oauthClientId.trim()
            }
        }
    }

    suspend fun clearGitHubCredentials() {
        context.vortexDataStore.edit { prefs ->
            prefs[Keys.GITHUB_PAT] = ""
            prefs[Keys.GITHUB_USERNAME] = ""
            prefs[Keys.GITHUB_AUTH_METHOD] = "NONE"
            prefs[Keys.GITHUB_AUTO_MIRROR] = false
        }
    }

    suspend fun setGitHubAutoMirror(enabled: Boolean) {
        context.vortexDataStore.edit { prefs ->
            prefs[Keys.GITHUB_AUTO_MIRROR] = enabled
        }
    }

    suspend fun setGitHubDefaultPrivate(isPrivate: Boolean) {
        context.vortexDataStore.edit { prefs ->
            prefs[Keys.GITHUB_DEFAULT_PRIVATE] = isPrivate
        }
    }

    suspend fun saveGitHubSyncState(
        projectId: String,
        workspaceSha256: String,
        summaryLabel: String
    ) {
        context.vortexDataStore.edit { prefs ->
            val hashes = VortexJson.decodeStringMap(prefs[Keys.GITHUB_SYNCED_HASHES_JSON]).toMutableMap()
            hashes[projectId] = workspaceSha256
            prefs[Keys.GITHUB_SYNCED_HASHES_JSON] = VortexJson.encodeStringMap(hashes)

            val labels = VortexJson.decodeStringMap(prefs[Keys.GITHUB_LAST_SYNC_JSON]).toMutableMap()
            labels[projectId] = summaryLabel
            prefs[Keys.GITHUB_LAST_SYNC_JSON] = VortexJson.encodeStringMap(labels)
        }
    }

    suspend fun setStrategicRoutingEnabled(enabled: Boolean) {
        context.vortexDataStore.edit { prefs ->
            prefs[Keys.STRATEGIC_ROUTING_ENABLED] = enabled
        }
    }

    suspend fun saveSafFolderBinding(projectId: String, treeUri: String) {
        context.vortexDataStore.edit { prefs ->
            val current = VortexJson.decodeStringMap(prefs[Keys.SAF_BINDINGS_JSON]).toMutableMap()
            current[projectId] = treeUri
            prefs[Keys.SAF_BINDINGS_JSON] = VortexJson.encodeStringMap(current)
        }
    }

    suspend fun saveSafSyncedHash(projectId: String, sha256: String) {
        context.vortexDataStore.edit { prefs ->
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
        context.vortexDataStore.edit { prefs ->
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
        context.vortexDataStore.edit { prefs ->
            prefs[Keys.INFRA_REGISTRY_JSON] = InfrastructureRegistryCodec.encode(
                state.copy(updatedAtMillis = System.currentTimeMillis())
            )
        }
    }
}
