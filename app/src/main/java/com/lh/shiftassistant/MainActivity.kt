package com.lh.shiftassistant

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lh.shiftassistant.ui.AppViewModel
import com.lh.shiftassistant.ui.AppViewModelFactory
import com.lh.shiftassistant.ui.MainScreen
import com.lh.shiftassistant.ui.theme.ShiftAssistantTheme
import java.time.LocalDate
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: AppViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ShiftAssistantApp).container
        viewModel = viewModels<AppViewModel> { AppViewModelFactory(container) }.value

        setContent {
            // 手动日/夜主题(两档切换,持久保存;默认浅色)
            val darkTheme by container.settingsRepository.darkTheme.collectAsStateWithLifecycle(false)
            val scope = rememberCoroutineScope()
            ShiftAssistantTheme(darkTheme = darkTheme) {
                MainScreen(
                    container = container,
                    viewModel = viewModel,
                    darkTheme = darkTheme,
                    onToggleTheme = {
                        scope.launch {
                            container.settingsRepository.setDarkTheme(!darkTheme)
                            container.widgetUpdater.refreshAll()
                        }
                    }
                )
            }
        }

        intent.getStringExtra(EXTRA_DATE)?.let { parseDate(it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_DATE)?.let { parseDate(it) }
    }

    private fun parseDate(value: String) {
        runCatching { LocalDate.parse(value) }.getOrNull()?.let { viewModel.handleWidgetDate(it) }
    }

    companion object {
        const val EXTRA_DATE = "extra_date"
    }
}
