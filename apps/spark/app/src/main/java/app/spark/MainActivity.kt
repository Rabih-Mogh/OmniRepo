package app.spark

import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.spark.data.Library
import app.spark.data.Store
import app.spark.ui.App
import app.spark.ui.Sounds
import app.spark.ui.SparkTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.open(this)
        Library.open(this)
        Sounds.open(this)
        // The volume keys set how loud the app's sounds are, whether or not one is playing.
        volumeControlStream = AudioManager.STREAM_MUSIC
        setContent {
            SparkTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.safeDrawingPadding()) { App() }
                }
            }
        }
    }
}
