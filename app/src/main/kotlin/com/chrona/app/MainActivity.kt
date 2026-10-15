package com.chrona.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.chrona.app.navigation.ChronaNavHost
import com.chrona.app.ui.design.ChronaTheme

/**
 * Single activity host. Chrona is a single-activity Compose app; the navigation shell owns
 * destinations and the theme owns visual tokens.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as ChronaApplication).container
        setContent {
            ChronaTheme {
                ChronaNavHost(container = container)
            }
        }
    }
}