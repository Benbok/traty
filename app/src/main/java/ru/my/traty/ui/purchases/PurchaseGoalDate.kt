package ru.my.traty.ui.purchases

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalDate

/** Если приложение остаётся открытым, предложение меняется и после полуночи. */
@Composable
internal fun purchaseGoalToday(): State<LocalDate> {
    val resumed = remember { mutableStateOf(LocalDate.now()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumed.value = LocalDate.now() }
    return produceState(resumed.value, resumed.value) {
        while (isActive) {
            value = LocalDate.now()
            delay(60_000 - System.currentTimeMillis().mod(60_000L))
        }
    }
}
