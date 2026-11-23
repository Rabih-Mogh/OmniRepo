package app.recitation

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import app.recitation.data.Appearance
import app.recitation.player.PlaybackService
import app.recitation.ui.App
import app.recitation.ui.Choices
import app.recitation.ui.ClearOfTheSides
import app.recitation.ui.RecitationTheme

class MainActivity : ComponentActivity() {

    private val model: AppModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The bars at the top and bottom take the page's colour, white by day and black by
        // night, so the phone's own icons over them are dark by day and light by night.
        val clear = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = clear, navigationBarStyle = clear)
        if (savedInstanceState == null) shared(intent)
        setContent {
            val arabic = LocalConfiguration.current.locales[0].language == "ar"
            val choices = remember { Choices(applicationContext, arabic) }
            // Light or dark as the reader chose in the menu; as the phone is set, until they do.
            val dark = when (choices.appearance) {
                Appearance.AUTO -> isSystemInDarkTheme()
                Appearance.LIGHT -> false
                Appearance.DARK -> true
            }
            // The phone's own icons over the app's bars have to show against them.
            DisposableEffect(dark) {
                val bars = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                onDispose {}
            }
            RecitationTheme(dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ClearOfTheSides { App(model, choices) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        shared(intent)
    }

    /**
     * A playlist shared from the YouTube app lands in the "add playlist" box; a tap on the
     * notification of a recitation that is playing shows its surah.
     */
    private fun shared(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let(model::offer)
            PlaybackService.OPEN_RECITING -> model.showReciting = true
        }
    }
}
