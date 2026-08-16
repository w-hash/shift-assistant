package com.lh.shiftassistant.ui.shift

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lh.shiftassistant.AppContainer
import com.lh.shiftassistant.data.ShiftTypeEntity
import com.lh.shiftassistant.ui.AppViewModel
import com.lh.shiftassistant.ui.components.AppListCard
import com.lh.shiftassistant.ui.components.ListDivider
import com.lh.shiftassistant.ui.components.ListRow
import com.lh.shiftassistant.util.TimeFormat
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftManagementScreen(container: AppContainer, viewModel: AppViewModel) {
    val repository = container.shiftRepository
    val shifts by viewModel.shifts.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showNew by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ShiftTypeEntity?>(null) }
    var deleting by remember { mutableStateOf<ShiftTypeEntity?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("班次管理") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNew = true }) {
                Icon(Icons.Filled.Add, contentDescription = "新增班次")
            }
        }
    ) { padding ->
        // 整页一张大圆角卡片,行与行之间虚线分隔(班次数少,Column 足够)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            AppListCard {
                shifts.forEachIndexed { index, shift ->
                    if (index > 0) ListDivider()
                    ShiftRow(
                        shift = shift,
                        onEdit = { editing = shift },
                        onDelete = { deleting = shift }
                    )
                }
            }
        }
    }

    if (showNew) {
        ShiftEditDialog(
            initial = null,
            onDismiss = { showNew = false },
            onSave = { name, short, start, end, color, sort ->
                scope.launch {
                    repository.addShift(name, short, start, end, color, sort)
                    Toast.makeText(context, "班次已保存", Toast.LENGTH_SHORT).show()
                }
                showNew = false
            }
        )
    }
    editing?.let { shift ->
        ShiftEditDialog(
            initial = shift,
            onDismiss = { editing = null },
            onSave = { name, short, start, end, color, sort ->
                scope.launch {
                    repository.updateShift(
                        shift.copy(
                            name = name, shortName = short,
                            startMinute = start, endMinute = end,
                            color = color, sortOrder = sort
                        )
                    )
                    Toast.makeText(context, "班次已保存", Toast.LENGTH_SHORT).show()
                }
                editing = null
            }
        )
    }
    deleting?.let { shift ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除班次") },
            text = { Text("删除班次「${shift.name}」?该班次的排班记录将变为休。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repository.deleteShift(shift)
                        Toast.makeText(context, "班次已删除", Toast.LENGTH_SHORT).show()
                    }
                    deleting = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ShiftRow(
    shift: ShiftTypeEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ListRow(
        onClick = onEdit,
        leading = {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(shift.color))
            )
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(shift.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(
                    "(${shift.shortName})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        subtitle = {
            Text(
                text = "${TimeFormat.formatMinute(shift.startMinute)} - ${TimeFormat.formatMinute(shift.endMinute)} · ${TimeFormat.durationText(shift.startMinute, shift.endMinute)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailing = {
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "编辑")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除")
                }
            }
        }
    )
}
