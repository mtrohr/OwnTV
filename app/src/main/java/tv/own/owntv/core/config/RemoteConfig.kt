package tv.own.owntv.core.config

data class ServerOption(
    val id: String,
    val name: String,
    val url: String,
)

data class RemoteConfig(
    val servers: List<ServerOption>,
)
