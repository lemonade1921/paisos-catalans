package cat.paisoscatalans

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cat.paisoscatalans.ui.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // El tema (clar, fosc, OLED) l'aplica App() segons els ajustos desats.
        setContent { App() }
    }
}
