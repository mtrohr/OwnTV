package tv.own.owntv.core.config

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import tv.own.owntv.core.network.HttpClient

private const val CONFIG_URL = "https://astrahosting.xyz/streamvault/config.json"

class RemoteConfigRepository(private val http: HttpClient) {

    @Volatile private var cached: RemoteConfig? = null

    suspend fun fetchConfig(forceRefresh: Boolean = false): RemoteConfig = withContext(Dispatchers.IO) {
        if (!forceRefresh) cached?.let { return@withContext it }
        val json = JSONObject(http.getText(CONFIG_URL))
        val arr = json.optJSONArray("servers") ?: throw IllegalStateException("config.json missing 'servers'")
        val servers = (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            ServerOption(
                id = obj.getString("id"),
                name = obj.getString("name"),
                url = obj.getString("url"),
            )
        }
        RemoteConfig(servers).also { cached = it }
    }
}
