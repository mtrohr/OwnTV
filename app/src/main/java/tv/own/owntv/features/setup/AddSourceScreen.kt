package tv.own.owntv.features.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.core.config.ServerOption
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVTextField
import tv.own.owntv.ui.theme.OwnTVTheme

@Composable
fun AddSourceScreen(
    servers: List<ServerOption>,
    onStartXtream: (name: String, server: String, user: String, pass: String, userAgent: String, epgUrl: String, refreshOnStart: Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initial: SourceEntity? = null,
    initialRefresh: Boolean = false,
    // M3U callback kept for compatibility but is no longer surfaced in the UI.
    onStartM3u: ((name: String, url: String, userAgent: String, epgUrl: String, refreshOnStart: Boolean) -> Unit)? = null,
) {
    val colors = OwnTVTheme.colors
    val editing = initial != null

    // When editing, try to match the existing URL back to a known server option.
    val initialServer = if (initial != null) servers.firstOrNull { it.url == initial.url } ?: servers.firstOrNull() else servers.firstOrNull()

    var selectedServer by remember { mutableStateOf(initialServer) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var password by remember { mutableStateOf(initial?.password ?: "") }
    var userAgent by remember { mutableStateOf(initial?.userAgent ?: "") }
    var refreshOnStart by remember { mutableStateOf(initialRefresh) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    val canStart = selectedServer != null && username.isNotBlank() && password.isNotBlank()

    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 48.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(modifier = Modifier.widthIn(max = 560.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (editing) "Edit source" else "Add your source",
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (editing) "Update your credentials or toggle refresh on startup."
                    else "Select a server and enter your username and password.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))

                // Server selector — locked while editing (server doesn't change after import).
                if (servers.isNotEmpty()) {
                    Text("Server", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        servers.forEachIndexed { index, option ->
                            val isSelected = selectedServer?.id == option.id
                            ServerChip(
                                label = option.name,
                                selected = isSelected,
                                enabled = !editing,
                                modifier = if (index == 0 && !editing) Modifier.fillMaxWidth().focusRequester(firstFocus) else Modifier.fillMaxWidth(),
                                onClick = { if (!editing) selectedServer = option },
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                } else {
                    Text(
                        "No servers available. Please try again later.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                }

                OwnTVTextField(
                    name, { name = it },
                    label = "Name (optional)",
                    placeholder = "My IPTV",
                    modifier = Modifier.fillMaxWidth(),
                    focusRequester = if (editing) firstFocus else null,
                )
                Spacer(Modifier.height(14.dp))
                OwnTVTextField(username, { username = it }, label = "Username", modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(14.dp))
                OwnTVTextField(
                    password, { password = it },
                    label = if (editing) "Password (leave blank to keep)" else "Password",
                    isPassword = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(14.dp))
                OwnTVTextField(
                    userAgent, { userAgent = it },
                    label = "User-Agent (optional)",
                    placeholder = "e.g. VLC/3.0.20 LibVLC/3.0.20",
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))
                ToggleRow(
                    label = "Refresh on startup",
                    desc = "Re-sync this source when the app opens",
                    checked = refreshOnStart,
                ) { refreshOnStart = it }

                Spacer(Modifier.height(28.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OwnTVButton("Back", onClick = onBack, style = OwnTVButtonStyle.SECONDARY)
                    Spacer(Modifier.weight(1f))
                    OwnTVButton(
                        label = if (editing) "Save" else "Start Import",
                        onClick = {
                            val srv = selectedServer ?: return@OwnTVButton
                            onStartXtream(name, srv.url, username, password, userAgent, "", refreshOnStart)
                        },
                        enabled = canStart,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, desc: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = { onToggle(!checked) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        contentAlignment = Alignment.CenterStart,
    ) { _ ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(desc, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            Box(
                modifier = Modifier.size(52.dp, 30.dp).clip(CircleShape).background(if (checked) colors.primary else colors.surfaceContainerHighest),
                contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(Modifier.padding(3.dp).size(24.dp).clip(CircleShape).background(Color.White))
            }
        }
    }
}

@Composable
private fun ServerChip(label: String, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = modifier,
        selected = selected,
        shape = RoundedCornerShape(14.dp),
        focusedContainerColor = if (enabled) colors.surfaceContainerHighest else colors.surfaceContainerHigh,
        unfocusedContainerColor = colors.surfaceContainerHigh,
        selectedContainerColor = colors.primaryContainer,
        contentAlignment = Alignment.CenterStart,
    ) { _ ->
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) colors.onPrimaryContainer else colors.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}
