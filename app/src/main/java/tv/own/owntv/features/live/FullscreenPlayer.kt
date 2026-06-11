package tv.own.owntv.features.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.koin.compose.koinInject
import tv.own.owntv.player.MpvVideoSurface
import tv.own.owntv.player.OwnTVPlayer
import tv.own.owntv.player.PlayerHud

/** Immersive fullscreen playback: mpv video surface + the custom OwnTV HUD. */
@Composable
fun FullscreenPlayer(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val player = koinInject<OwnTVPlayer>()
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        MpvVideoSurface(player = player, modifier = Modifier.fillMaxSize())
        PlayerHud(player = player, onBack = onBack, modifier = Modifier.fillMaxSize())
    }
}
