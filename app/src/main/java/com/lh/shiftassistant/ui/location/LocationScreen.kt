package com.lh.shiftassistant.ui.location

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lh.shiftassistant.AppContainer
import com.lh.shiftassistant.data.LocationEntity
import com.lh.shiftassistant.ui.AppViewModel
import com.lh.shiftassistant.ui.components.AppListCard
import com.lh.shiftassistant.ui.components.ListDivider
import com.lh.shiftassistant.ui.components.ListRow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationScreen(container: AppContainer, viewModel: AppViewModel) {
    val repository = container.shiftRepository
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showNew by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LocationEntity?>(null) }
    var deleting by remember { mutableStateOf<LocationEntity?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("地点管理") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNew = true }) {
                Icon(Icons.Filled.Add, contentDescription = "新增地点")
            }
        }
    ) { padding ->
        // 整页一张大圆角卡片,行与行之间虚线分隔(地点数少,Column 足够)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            if (locations.isEmpty()) {
                Text(
                    "暂无地点。排班时可从地点库选择,地点库每月按实际排班手动维护。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp)
                )
            } else {
                AppListCard {
                    locations.forEachIndexed { index, location ->
                        if (index > 0) ListDivider()
                        LocationRow(
                            location = location,
                            onEdit = { editing = location },
                            onDelete = { deleting = location }
                        )
                    }
                }
            }
        }
    }

    if (showNew) {
        LocationEditDialog(
            initial = null,
            onDismiss = { showNew = false },
            onSave = { name ->
                scope.launch {
                    repository.addLocation(name)
                    Toast.makeText(context, "地点已保存", Toast.LENGTH_SHORT).show()
                }
                showNew = false
            }
        )
    }
    editing?.let { location ->
        LocationEditDialog(
            initial = location,
            onDismiss = { editing = null },
            onSave = { name ->
                scope.launch {
                    repository.updateLocation(location.copy(name = name))
                    Toast.makeText(context, "地点已保存", Toast.LENGTH_SHORT).show()
                }
                editing = null
            }
        )
    }
    deleting?.let { location ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除地点") },
            text = { Text("删除地点「${location.name}」?相关排班记录的地点将清空。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repository.deleteLocation(location)
                        Toast.makeText(context, "地点已删除", Toast.LENGTH_SHORT).show()
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
private fun LocationRow(
    location: LocationEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ListRow(
        onClick = onEdit,
        title = {
            Text(location.name, style = MaterialTheme.typography.titleMedium)
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

@Composable
private fun LocationEditDialog(
    initial: LocationEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增地点" else "编辑地点") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("地点名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Spacer(Modifier.padding(top = 8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmed = name.trim()
                if (trimmed.isEmpty()) {
                    error = "请填写地点名称"
                } else {
                    onSave(trimmed)
                }
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
