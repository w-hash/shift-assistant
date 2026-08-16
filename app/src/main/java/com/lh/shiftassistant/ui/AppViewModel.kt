package com.lh.shiftassistant.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lh.shiftassistant.AppContainer
import com.lh.shiftassistant.data.LocationEntity
import com.lh.shiftassistant.data.ShiftTypeEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 全局共享状态:班次/地点列表(随 dataVersion 自动刷新)+ 小组件点击跳转的日期。
 * 注意:数据层无 Flow(本机 Room KSP 限制),此处监听 repository.dataVersion 重载。
 */
class AppViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.shiftRepository

    private val _shifts = MutableStateFlow<List<ShiftTypeEntity>>(emptyList())
    val shifts: StateFlow<List<ShiftTypeEntity>> = _shifts.asStateFlow()

    private val _locations = MutableStateFlow<List<LocationEntity>>(emptyList())
    val locations: StateFlow<List<LocationEntity>> = _locations.asStateFlow()

    init {
        viewModelScope.launch {
            repository.dataVersion.collect {
                _shifts.value = repository.getShifts()
                _locations.value = repository.getLocations()
            }
        }
    }

    private val _pendingDate = MutableStateFlow<LocalDate?>(null)
    val pendingDate: StateFlow<LocalDate?> = _pendingDate.asStateFlow()

    /** 小组件点击:携带日期打开 App */
    fun handleWidgetDate(date: LocalDate) {
        _pendingDate.value = date
    }

    fun consumePendingDate() {
        _pendingDate.value = null
    }
}

class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            return AppViewModel(container) as T
        }
        throw IllegalArgumentException("未知 ViewModel 类型:${modelClass.name}")
    }
}
