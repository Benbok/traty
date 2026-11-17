package ru.my.traty

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.my.traty.ui.MainViewModel
import ru.my.traty.ui.TratyRoot
import ru.my.traty.ui.receipt.ReceiptCameraEntry
import ru.my.traty.ui.receipt.RECEIPT_CAMERA_ACTION
import ru.my.traty.ui.designsystem.theme.TratyTheme
import ru.my.traty.ui.designsystem.theme.holidayOn
import ru.my.traty.ui.designsystem.theme.isDark
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private var receiptCameraRequested by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receiptCameraRequested = savedInstanceState?.getBoolean(RECEIPT_CAMERA_ACTION)
            ?: (intent.action == RECEIPT_CAMERA_ACTION)
        setContent {
            val vm: MainViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            val isDark = settings.themeMode.isDark()

            DisposableEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (isDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    },
                )
                onDispose {}
            }

            // Праздник проверяется при открытии экрана
            val todayHoliday = remember { holidayOn(LocalDate.now()) }
            TratyTheme(
                darkTheme = isDark,
                dynamicColor = settings.dynamicColor,
                holiday = todayHoliday.takeIf { settings.holidayDecor },
            ) {
                TratyRoot(vm)
                ReceiptCameraEntry(
                    requested = receiptCameraRequested,
                    onHandled = { receiptCameraRequested = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == RECEIPT_CAMERA_ACTION) receiptCameraRequested = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(RECEIPT_CAMERA_ACTION, receiptCameraRequested)
        super.onSaveInstanceState(outState)
    }
}
