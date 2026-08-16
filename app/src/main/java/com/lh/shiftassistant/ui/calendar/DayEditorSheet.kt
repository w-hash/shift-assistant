package com.lh.shiftassistant.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lh.shiftassistant.AppContainer
import com.lh.shiftassistant.data.DayDetail
import com.lh.shiftassistant.data.Defaults
import com.lh.shiftassistant.data.LocationEntity
import com.lh.shiftassistant.data.ShiftTypeEntity
import com.lh.shiftassistant.ui.components.AppCard
import com.lh.shiftassistant.ui.theme.DayOffColor
import java.time.LocalDate
import kotlinx.coroutines.launch

/** 当日编辑 BottomSheet:班次单选 + 地点 + 备注 + 换班痕迹提示 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DayEditorSheet(
    date: LocalDate,
    container: AppContainer,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val repository = container.shiftRepository
    val scope = rememberCoroutineScope()

    // 该天的现有数据 + 班次/地点列表(打开弹层时加载;dataVersion 变化后自动重载)
    val version by repository.dataVersion.collectAsStateWithLifecycle()
    val shifts by produceState<List<ShiftTypeEntity>>(initialValue = emptyList(), version) {
        value = repository.getShifts()
    }
    val locations by produceState<List<LocationEntity>>(initialValue = emptyList(), version) {
        value = repository.getLocations()
    }
    val detail by produceState<DayDetail?>(initialValue = null, version) {
        value = repository.dayDetail(Defaults.PERSON_ID, date)
    }

    // 选中状态初始为 null;detail 异步加载完成后同步一次(修复:打开弹层时未加载完导致默认"休"的 bug)
    var selectedShiftId by remember { mutableStateOf<Long?>(null) }
    var selectedLocationId by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }
    LaunchedEffect(detail) {
        if (!initialized && detail != null) {
            initialized = true
            selectedShiftId = detail?.entry?.shiftTypeId
            selectedLocationId = detail?.entry?.locationId
            note = detail?.entry?.note ?: ""
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            // 标题
            Text(
                text = "${date.monthValue}月${date.dayOfMonth}日 星期${WEEKDAYS_CN[date.dayOfWeek.value - 1]}",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(4.dp))

            // 原班提示(换班痕迹)
            detail?.let { d ->
                val original = d.originalShift
                if (original != null) {
                    Text(
                        text = "原班:${original.name} → ${d.shift?.name ?: "休"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }

            // 班次选择(含"休")
            Text("选择班次", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            AppCard {
                Column(modifier = Modifier.padding(12.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        shifts.forEach { shift ->
                            ShiftChip(
                                text = shift.shortName,
                                color = Color(shift.color),
                                selected = selectedShiftId == shift.id,
                                onClick = { selectedShiftId = shift.id }
                            )
                        }
                        ShiftChip(
                            text = "休",
                            color = DayOffColor,
                            selected = selectedShiftId == null,
                            onClick = { selectedShiftId = null }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // 地点选择(地点库,可空)
            if (locations.isNotEmpty() || selectedLocationId != null) {
                Text("选择地点", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                AppCard {
                    Column(modifier = Modifier.padding(12.dp)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            LocationChip(
                                text = "无",
                                selected = selectedLocationId == null,
                                onClick = { selectedLocationId = null }
                            )
                            locations.forEach { loc ->
                                LocationChip(
                                    text = loc.name,
                                    selected = selectedLocationId == loc.id,
                                    onClick = { selectedLocationId = loc.id }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // 备注
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("备注(选填)") },
                singleLine = true
            )
            Spacer(Modifier.height(16.dp))

            // 按钮
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            repository.setDay(Defaults.PERSON_ID, date, null, null, null)
                            onSaved()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("清空为休")
                }
                Button(
                    onClick = {
                        scope.launch {
                            repository.setDay(Defaults.PERSON_ID, date, selectedShiftId, selectedLocationId, note)
                            onSaved()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("保存")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ShiftChip(text: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.size(6.dp))
                Text(text)
            }
        },
        leadingIcon = {
            Spacer(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = if (selected) color.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
private fun LocationChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            labelColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        )
    )
}

private val WEEKDAYS_CN = arrayOf("一", "二", "三", "四", "五", "六", "日")
