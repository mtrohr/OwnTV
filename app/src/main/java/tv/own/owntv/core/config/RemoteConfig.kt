package tv.own.owntv.core.config

data class ServerOption(
    val id: String,
    val name: String,
    val url: String,
)

data class RemoteConfig(
    val version: Long,
    val ttlSeconds: Long,
    val servers: List<ServerOption>,
)
