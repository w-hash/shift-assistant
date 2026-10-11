package com.lh.shiftassistant.ui.settings

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lh.shiftassistant.AppContainer
import com.lh.shiftassistant.BuildConfig
import com.lh.shiftassistant.R
import com.lh.shiftassistant.calendar.CalendarOption
import com.lh.shiftassistant.ui.components.DashedDivider
import com.lh.shiftassistant.ui.components.SettingsCard
import com.lh.shiftassistant.widget.ShiftWidgetReceiver
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer) {
    val settings = container.settingsRepository
    val widgetUpdater = container.widgetUpdater
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val widgetDays by settings.widgetDays.collectAsStateWithLifecycle(4)
    val calendarSyncOn by settings.calendarSyncEnabled.collectAsStateWithLifecycle(false)
    val calendarSync = container.calendarSyncManager

    var pendingImportText by remember { mutableStateOf<String?>(null) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var showWidgetGuide by remember { mutableStateOf(false) }
    var showClearCalendarConfirm by remember { mutableStateOf(false) }

    // 日历同步目标(会话8:写入系统自带日历,可手动更换)
    var targetCalendarName by remember { mutableStateOf<String?>(null) }
    var showCalendarPicker by remember { mutableStateOf(false) }
    var calendarOptions by remember { mutableStateOf<List<CalendarOption>>(emptyList()) }

    LaunchedEffect(calendarSyncOn) {
        targetCalendarName = if (calendarSyncOn) {
            runCatching { calendarSync.currentTargetName() }.getOrNull()
        } else {
            null
        }
    }

    // 日历权限请求(开启同步 / 立即同步时用)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.READ_CALENDAR] == true &&
            result[Manifest.permission.WRITE_CALENDAR] == true
        if (granted) {
            scope.launch {
                settings.setCalendarSyncEnabled(true)
                val n = runCatching { calendarSync.syncNow() }.getOrDefault(0)
                Toast.makeText(
                    context,
                    if (n > 0) "已开启同步,写入 $n 个班次" else "已开启同步",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            Toast.makeText(context, "未授予日历权限,无法同步", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestCalendarPermission() {
        permissionLauncher.launch(
            arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        )
    }

    // 导出:手动构造 ACTION_CREATE_DOCUMENT(支持自定义建议文件名)
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (uri != null) {
            scope.launch {
                val ok = runCatching {
                    val json = container.backupManager.export()
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("无法写入文件")
                }.isSuccess
                Toast.makeText(
                    context,
                    if (ok) "备份已导出" else "导出失败",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // 导入:读取文件内容,确认后整体覆盖
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    }
                }.getOrNull()
                if (text != null) {
                    pendingImportText = text
                    showImportConfirm = true
                }
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 小组件天数
            Text("小组件显示天数", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                Column(modifier = Modifier.selectableGroup().padding(vertical = 4.dp)) {
                    SettingsRadioRow(
                        text = "4 天(今天+未来3天)",
                        selected = widgetDays == 4,
                        onClick = { scope.launch { settings.setWidgetDays(4); widgetUpdater.refreshAll() } }
                    )
                    DashedDivider(startIndent = 12.dp)
                    SettingsRadioRow(
                        text = "7 天",
                        selected = widgetDays == 7,
                        onClick = { scope.launch { settings.setWidgetDays(7); widgetUpdater.refreshAll() } }
                    )
                }
            }
            DashedDivider()
            Spacer(Modifier.height(8.dp))

            // 添加桌面卡片
            Text("桌面小组件", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "把班表添加到桌面,不打开 App 也能查看今天和未来几天的班次。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val mgr = AppWidgetManager.getInstance(context)
                            val provider = ComponentName(context, ShiftWidgetReceiver::class.java)
                            val before = mgr.getAppWidgetIds(provider).size
                            runCatching { mgr.requestPinAppWidget(provider, null, null) }
                            android.util.Log.i("ShiftWidget", "requestPinAppWidget called")
                            // 3 秒后检查小组件实例是否真的增加(ColorOS 上 requestPinAppWidget 可能无实际效果)
                            scope.launch {
                                delay(3000)
                                val after = AppWidgetManager.getInstance(context).getAppWidgetIds(provider).size
                                if (after > before) {
                                    Toast.makeText(context, "已添加到桌面", Toast.LENGTH_SHORT).show()
                                } else {
                                    showWidgetGuide = true
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("添加桌面卡片")
                    }
                }
            }
            DashedDivider()
            Spacer(Modifier.height(8.dp))

            // 日历同步(替代桌面小组件,利用系统日历的"日程"卡片)
            Text("同步到系统日历", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "开启同步",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = calendarSyncOn,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (calendarSync.hasPermissions()) {
                                        scope.launch {
                                            settings.setCalendarSyncEnabled(true)
                                            val n = runCatching { calendarSync.syncNow() }.getOrDefault(0)
                                            Toast.makeText(
                                                context,
                                                if (n > 0) "已开启同步,写入 $n 个班次" else "已开启同步",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    } else {
                                        requestCalendarPermission()
                                    }
                                } else {
                                    scope.launch {
                                        calendarSync.disableAndClear()
                                        Toast.makeText(context, "已关闭同步并清除日历事件", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                    DashedDivider()
                    Text(
                        "把今天及未来的班次写入系统日历,桌面「日程」卡片即可显示班次。班次会写入下方选择的日历(默认自动选择系统本地日历),请确保该日历在日历应用中对勾可见。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    DashedDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "同步目标日历",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                targetCalendarName ?: "自动选择中…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = {
                            scope.launch {
                                calendarOptions = runCatching { calendarSync.listWritableCalendars() }
                                    .getOrDefault(emptyList())
                                showCalendarPicker = true
                            }
                        }) {
                            Text("更换")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = {
                                if (calendarSync.hasPermissions()) {
                                    scope.launch {
                                        val n = runCatching { calendarSync.syncNow() }.getOrDefault(0)
                                        Toast.makeText(
                                            context,
                                            if (n > 0) "已同步 $n 个班次" else "没有需要同步的班次",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                } else {
                                    requestCalendarPermission()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("立即同步")
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { showClearCalendarConfirm = true }) {
                            Text("清除同步事件", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            DashedDivider()
            Spacer(Modifier.height(8.dp))

            // 备份
            Text("数据备份", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "导出当前全部数据(班次/地点/排班)为 JSON 文件,换机或重装后可导入还原。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row {
                        OutlinedButton(
                            onClick = {
                                val date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                                    .addCategory(Intent.CATEGORY_OPENABLE)
                                    .setType("application/json")
                                    .putExtra(Intent.EXTRA_TITLE, "班表助手备份_$date.json")
                                exportLauncher.launch(intent)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("导出备份")
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("导入备份")
                        }
                    }
                }
            }
            DashedDivider()
            Spacer(Modifier.height(8.dp))

            // 关于
            Text("关于", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("班表助手", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "版本 ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "仅本地数据,不上传任何信息。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text("导入备份") },
            text = { Text("导入将覆盖当前全部数据,确定继续?") },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    val text = pendingImportText
                    pendingImportText = null
                    if (text != null) {
                        scope.launch {
                            val ok = runCatching { container.backupManager.import(text) }.isSuccess
                            Toast.makeText(
                                context,
                                if (ok) "备份已还原" else "导入失败:文件格式不支持",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = false }) { Text("取消") }
            }
        )
    }

    // 添加桌面卡片引导(系统不支持 requestPinAppWidget 时)
    if (showWidgetGuide) {
        AlertDialog(
            onDismissRequest = { showWidgetGuide = false },
            title = { Text(stringResource(R.string.settings_widget_guide_title)) },
            text = { Text(stringResource(R.string.settings_widget_guide_msg)) },
            confirmButton = {
                TextButton(onClick = { showWidgetGuide = false }) {
                    Text(stringResource(R.string.settings_widget_guide_ok))
                }
            }
        )
    }

    // 选择同步目标日历
    if (showCalendarPicker) {
        AlertDialog(
            onDismissRequest = { showCalendarPicker = false },
            title = { Text("选择同步目标日历") },
            text = {
                if (calendarOptions.isEmpty()) {
                    Text("没有可写入的日历。")
                } else {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .selectableGroup()
                    ) {
                        calendarOptions.forEach { option ->
                            SettingsRadioRow(
                                text = option.displayName +
                                    if (option.accountName.isNotBlank()) "(${option.accountName})" else "",
                                selected = false,
                                onClick = {
                                    showCalendarPicker = false
                                    scope.launch {
                                        calendarSync.selectCalendar(option.id)
                                        val n = runCatching { calendarSync.syncNow() }.getOrDefault(0)
                                        targetCalendarName = option.displayName
                                        Toast.makeText(
                                            context,
                                            if (n > 0) {
                                                "已切换到「${option.displayName}」并同步 $n 个班次"
                                            } else {
                                                "已选择日历,没有需要同步的班次"
                                            },
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCalendarPicker = false }) { Text("取消") }
            }
        )
    }

    // 清除已同步日历事件确认
    if (showClearCalendarConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCalendarConfirm = false },
            title = { Text("清除同步事件") },
            text = { Text("确定清除日历中所有由「班表助手」写入的班次事件?") },
            confirmButton = {
                TextButton(onClick = {
                    showClearCalendarConfirm = false
                    scope.launch {
                        val n = runCatching { calendarSync.clearAll() }.getOrDefault(0)
                        Toast.makeText(context, "已清除日历事件($n 条)", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showClearCalendarConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SettingsRadioRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.padding(horizontal = 2.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
