package tv.own.owntv.core.config

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import tv.own.owntv.core.network.HttpClient

private const val CONFIG_URL = "https://astrahosting.xyz/streamvault/config.json"
private const val DEFAULT_TTL_SECONDS = 3600L

private val Context.configStore: DataStore<Preferences> by preferencesDataStore(name = "owntv_remote_config")
private val KEY_JSON = stringPreferencesKey("config_json")
private val KEY_FETCHED_AT = longPreferencesKey("fetched_at")
private val KEY_VERSION = longPreferencesKey("config_version")

class RemoteConfigRepository(
    private val context: Context,
    private val http: HttpClient,
) {
    suspend fun fetchConfig(forceRefresh: Boolean = false): RemoteConfig = withContext(Dispatchers.IO) {
        val prefs = context.configStore.data.first()
        val cachedJson = prefs[KEY_JSON]
        val fetchedAt = prefs[KEY_FETCHED_AT] ?: 0L

        // Use disk cache if still within TTL and not force-refreshing.
        if (!forceRefresh && cachedJson != null) {
            val cached = runCatching { parse(cachedJson) }.getOrNull()
            if (cached != null) {
                val ttlMs = cached.ttlSeconds * 1000
                if (System.currentTimeMillis() - fetchedAt < ttlMs) {
                    return@withContext cached
                }
            }
        }

        // Fetch from network; fall back to stale cache if it fails.
        val fresh = runCatching {
            val raw = http.getText(CONFIG_URL)
            val config = parse(raw)
            context.configStore.edit { p ->
                p[KEY_JSON] = raw
                p[KEY_FETCHED_AT] = System.currentTimeMillis()
                p[KEY_VERSION] = config.version
            }
            config
        }.getOrNull()

        if (fresh != null) return@withContext fresh

        // Network failed — return stale cache rather than crashing the UI.
        cachedJson?.let { runCatching { parse(it) }.getOrNull() }
            ?: throw IllegalStateException("No config available and network request failed.")
    }

    private fun parse(raw: String): RemoteConfig {
        val json = JSONObject(raw)
        val version = json.optLong("version", 0L)
        val ttl = json.optLong("ttl_seconds", DEFAULT_TTL_SECONDS)
        val arr = json.optJSONArray("services")
            ?: throw IllegalStateException("config.json missing 'services'")
        val servers = (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            ServerOption(
                id = obj.getString("id"),
                name = obj.getString("display_name"),
                url = obj.getString("server_url"),
            )
        }
        return RemoteConfig(version = version, ttlSeconds = ttl, servers = servers)
    }
}
