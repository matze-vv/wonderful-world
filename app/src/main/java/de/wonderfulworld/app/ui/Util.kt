package de.wonderfulworld.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.wonderfulworld.app.WonderfulWorldApp
import de.wonderfulworld.app.app
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (WonderfulWorldApp) -> VM): VM {
    val app = LocalContext.current.app
    return viewModel(factory = viewModelFactory { initializer { create(app) } })
}

/** Das heutige Datum – aktualisiert sich beim Zurückkehren in die App und um Mitternacht. */
@Composable
fun rememberToday(): LocalDate {
    var today by remember { mutableStateOf(LocalDate.now()) }
    LifecycleResumeEffect(Unit) {
        today = LocalDate.now()
        onPauseOrDispose { }
    }
    LaunchedEffect(today) {
        val untilMidnight = Duration.between(LocalDateTime.now(), today.plusDays(1).atStartOfDay()).toMillis()
        delay(untilMidnight.coerceAtLeast(0) + 1_000)
        today = LocalDate.now()
    }
    return today
}
