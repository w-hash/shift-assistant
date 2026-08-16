package com.lh.shiftassistant.ui.shift

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lh.shiftassistant.data.ShiftTypeEntity
import com.lh.shiftassistant.ui.theme.ShiftPalette
import com.lh.shiftassistant.util.TimeFormat

/** 班次新增/编辑表单 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShiftEditDialog(
    initial: ShiftTypeEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, shortName: String, startMinute: Int, endMinute: Int, color: Int, sortOrder: Int) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var shortName by remember { mutableStateOf(initial?.shortName ?: "") }
    var startMinute by remember { mutableIntStateOf(initial?.startMinute ?: 0) }
    var endMinute by remember { mutableIntStateOf(initial?.endMinute ?: 480) }
    var color by remember { mutableIntStateOf(initial?.color ?: ShiftPalette.first().value.toInt()) }
    var sortOrder by remember { mutableIntStateOf(initial?.sortOrder ?: 1) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增班次" else "编辑班次") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("班次全名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = shortName,
                    onValueChange = { if (it.length <= 4) shortName = it },
                    label = { Text("简称(≤4字)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    TimeField(
                        label = "开始时间",
                        minute = startMinute,
                        allow24 = false,
                        onMinuteChange = { startMinute = it.coerceAtMost(1439) },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.size(12.dp))
                    TimeField(
                        label = "结束时间",
                        minute = endMinute,
                        allow24 = true,
                        onMinuteChange = { endMinute = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "时长:${TimeFormat.durationText(startMinute, endMinute)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                Text("颜色", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ShiftPalette.forEach { c ->
                        val cInt = c.value.toInt()
                        val selected = color == cInt
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(c)
                                .then(
                                    if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                    else Modifier
                                )
                                .clickable { color = cInt },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = sortOrder.toString(),
                    onValueChange = { it.filter(Char::isDigit).take(3).let { s -> sortOrder = s.toIntOrNull() ?: 0 } },
                    label = { Text("排序(小的在前)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val trimmedName = name.trim()
                val trimmedShort = shortName.trim()
                when {
                    trimmedName.isEmpty() -> error = "请填写班次全名"
                    trimmedShort.isEmpty() -> error = "请填写简称"
                    startMinute > 1439 -> error = "开始时间不能为24:00"
                    endMinute <= startMinute -> error = "结束时间必须晚于开始时间"
                    else -> onSave(trimmedName, trimmedShort, startMinute, endMinute, color, sortOrder)
                }
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 时间输入框:点击弹出 TimePicker;结束时间支持 24:00 */
@Composable
fun TimeField(
    label: String,
    minute: Int,
    allow24: Boolean,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = { showPicker = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(TimeFormat.formatMinute(minute))
        }
    }

    if (showPicker) {
        TimePickerDialog(
            initialMinute = minute,
            allow24 = allow24,
            onDismiss = { showPicker = false },
            onConfirm = { onMinuteChange(it); showPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialMinute: Int,
    allow24: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val is24 = initialMinute == 1440
    var check24 by remember { mutableStateOf(is24) }
    val state = rememberTimePickerState(
        initialHour = if (is24) 0 else initialMinute / 60,
        initialMinute = if (is24) 0 else initialMinute % 60,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择时间") },
        text = {
            Column {
                if (allow24) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = check24,
                            onCheckedChange = { check24 = it }
                        )
                        Text("24:00(次日00:00)")
                    }
                }
                if (!check24) {
                    TimePicker(state = state)
                } else {
                    Text(
                        "结束时间将设为 24:00",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(if (check24) 1440 else state.hour * 60 + state.minute)
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
