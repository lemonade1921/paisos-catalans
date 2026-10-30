package cat.paisoscatalans

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import cat.paisoscatalans.recordatori.Recordatori
import cat.paisoscatalans.ui.App

class MainActivity : ComponentActivity() {
    /** Pestanya que cal obrir (p. ex. "joc" quan es toca el recordatori). */
    private val obre = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        obre.value = intent?.getStringExtra(Recordatori.EXTRA_OBRE)
        // El tema (clar, fosc, OLED) l'aplica App() segons els ajustos desats.
        setContent { App(obre = obre.value, onObert = { obre.value = null }) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(Recordatori.EXTRA_OBRE)?.let { obre.value = it }
    }
}
