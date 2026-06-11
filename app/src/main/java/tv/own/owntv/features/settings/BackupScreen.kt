package tv.own.owntv.features.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.ui.components.BrowseMode
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.StorageBrowser
import tv.own.owntv.ui.theme.OwnTVTheme

/** Phase 12 — Backup & Restore (Settings → Backup). Uses an in-app file picker (no SAF). */
@Composable
fun BackupScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: BackupViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = OwnTVTheme.colors

    var browser by remember { mutableStateOf(BrowseMode.FOLDER) } // which picker
    var showBrowser by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(50); runCatching { firstFocus.requestFocus() } }

    BackHandler { onBack() }

    Column(modifier = modifier.fillMaxSize().background(colors.surface).padding(horizontal = 40.dp, vertical = 28.dp)) {
        Text("Backup & Restore", style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(
            "Save your profiles and sources (URLs, credentials, PINs) to a file, or restore them on a new " +
                "device. Channels/movies re-sync from your sources after restoring.",
            style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.widthIn(max = 680.dp),
        )
        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OwnTVButton("Export backup", onClick = { browser = BrowseMode.FOLDER; showBrowser = true }, enabled = state != BackupViewModel.State.Working, modifier = Modifier.focusRequester(firstFocus))
            OwnTVButton("Restore backup", onClick = { browser = BrowseMode.FILE; showBrowser = true }, style = OwnTVButtonStyle.SECONDARY, enabled = state != BackupViewModel.State.Working)
        }
        Spacer(Modifier.height(20.dp))

        when (val s = state) {
            BackupViewModel.State.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
                OwnTVSpinner(sizeDp = 22)
                Spacer(Modifier.width(12.dp))
                Text("Working…", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            }
            is BackupViewModel.State.Done -> Text(s.message, style = MaterialTheme.typography.bodyLarge, color = colors.primary)
            is BackupViewModel.State.Error -> Text(s.message, style = MaterialTheme.typography.bodyLarge, color = Color(0xFFEF4444))
            BackupViewModel.State.Idle -> Unit
        }
    }

    if (showBrowser) {
        StorageBrowser(
            title = if (browser == BrowseMode.FOLDER) "Choose a folder to save the backup" else "Pick a backup file to restore",
            mode = browser,
            fileExtension = "json",
            onPick = { file -> showBrowser = false; if (browser == BrowseMode.FOLDER) vm.export(file) else vm.import(file) },
            onDismiss = { showBrowser = false },
        )
    }
}
