package ru.my.traty

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ru.my.traty.ui.TratyRoot
import ru.my.traty.ui.designsystem.theme.TratyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TratyTheme {
                TratyRoot()
            }
        }
    }
}
