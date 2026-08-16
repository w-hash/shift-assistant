package com.lh.shiftassistant.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lh.shiftassistant.AppContainer
import com.lh.shiftassistant.data.DayDetail
import com.lh.shiftassistant.data.Defaults
import com.lh.shiftassistant.data.ScheduleEntryEntity
import com.lh.shiftassistant.data.ShiftRepository
import com.lh.shiftassistant.data.ShiftTypeEntity
import com.lh.shiftassistant.ui.AppViewModel
import com.lh.shiftassistant.ui.theme.ChangeBadgeColor
import com.lh.shiftassistant.ui.theme.DayOffColor
import com.lh.shiftassistant.util.MonthGrid
import com.lh.shiftassistant.util.TimeFormat
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.launch

private val ANCHOR = YearMonth.of(2024, 1)
private const val PAGE_COUNT = 1200 // 100 年跨度
private val WEEKDAY_HEADERS = listOf("一", "二", "三", "四", "五", "六", "日")

@Composable
fun CalendarScreen(
    container: AppContainer,
    viewModel: AppViewModel,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val repository = container.shiftRepository
    val shifts by viewModel.shifts.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }

    var currentMonth by rememberSaveable { mutableStateOf(YearMonth.from(today)) }
    var editingDate by rememberSaveable { mutableStateOf<String?>(null) }

    fun monthToPage(ym: YearMonth): Int =
        (ym.year - ANCHOR.year) * 12 + (ym.monthValue - ANCHOR.monthValue)

    fun pageToMonth(page: Int): YearMonth = ANCHOR.plusMonths(page.toLong())

    val pagerState = rememberPagerState(
        initialPage = monthToPage(currentMonth).coerceIn(0, PAGE_COUNT - 1)
    ) { PAGE_COUNT }

    // 页码变化 -> 当前月(驱动标题与当月数据)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            currentMonth = pageToMonth(page)
        }
    }

    // 小组件点击:跳转对应月份并打开当日编辑
    val pendingDate by viewModel.pendingDate.collectAsStateWithLifecycle()
    LaunchedEffect(pendingDate) {
        val date = pendingDate ?: return@LaunchedEffect
        val targetPage = monthToPage(YearMonth.from(date)).coerceIn(0, PAGE_COUNT - 1)
        if (pagerState.currentPage != targetPage) pagerState.scrollToPage(targetPage)
        editingDate = date.toString()
        viewModel.consumePendingDate()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 月份标题 + 上/下月箭头 + 日/夜切换
        MonthHeader(
            yearMonth = currentMonth,
            darkTheme = darkTheme,
            onToggleTheme = onToggleTheme,
            onPrev = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) } },
            onNext = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(PAGE_COUNT - 1)) } }
        )

        // 今日卡片
        TodayCard(repository = repository, today = today, onJumpToday = {
            scope.launch { pagerState.animateScrollToPage(monthToPage(YearMonth.from(today))) }
        })

        // 星期表头
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            WEEKDAY_HEADERS.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 月历分页
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            MonthView(
                yearMonth = pageToMonth(page),
                repository = repository,
                shifts = shifts,
                today = today,
                onDayClick = { editingDate = it.toString() }
            )
        }
    }

    // 当日编辑弹层
    editingDate?.let { dateStr ->
        runCatching { LocalDate.parse(dateStr) }.getOrNull()?.let { date ->
            DayEditorSheet(
                date = date,
                container = container,
                onDismiss = { editingDate = null },
                onSaved = { editingDate = null }
            )
        }
    }
}

@Composable
private fun MonthHeader(
    yearMonth: YearMonth,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上月")
        }
        Text(
            text = "${yearMonth.year}年${yearMonth.monthValue}月",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下月")
        }
        // 日/夜切换:日间显示月亮(点击进入夜间),夜间显示太阳(点击回到日间)
        IconButton(onClick = onToggleTheme) {
            Icon(
                imageVector = if (darkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                contentDescription = if (darkTheme) "切换到日间模式" else "切换到夜间模式"
            )
        }
    }
}

@Composable
private fun TodayCard(repository: ShiftRepository, today: LocalDate, onJumpToday: () -> Unit) {
    val version by repository.dataVersion.collectAsStateWithLifecycle()
    val detail by produceState<DayDetail?>(initialValue = null, version) {
        value = repository.dayDetail(Defaults.PERSON_ID, today)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(onClick = onJumpToday),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("今日班次", style = MaterialTheme.typography.labelMedium)
                val shift = detail?.shift
                if (shift != null) {
                    Text(
                        text = "${shift.name} ${TimeFormat.formatMinute(shift.startMinute)}-${TimeFormat.formatMinute(shift.endMinute)}(${TimeFormat.durationText(shift.startMinute, shift.endMinute)})",
                        style = MaterialTheme.typography.titleSmall
                    )
                    val location = detail?.location
                    if (location != null) {
                        Text("地点:${location.name}", style = MaterialTheme.typography.bodySmall)
                    }
                    detail?.entry?.note?.let {
                        Text("备注:$it", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Text("休", style = MaterialTheme.typography.titleSmall, color = DayOffColor)
                }
            }
            Text(
                text = "${today.monthValue}/${today.dayOfMonth}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun MonthView(
    yearMonth: YearMonth,
    repository: ShiftRepository,
    shifts: List<ShiftTypeEntity>,
    today: LocalDate,
    onDayClick: (LocalDate) -> Unit
) {
    val version by repository.dataVersion.collectAsStateWithLifecycle()
    val entries by produceState<Map<LocalDate, ScheduleEntryEntity>>(
        initialValue = emptyMap(),
        yearMonth, version
    ) {
        value = repository.getMonthEntries(Defaults.PERSON_ID, yearMonth)
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        MonthGrid.weeks(yearMonth).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    DayCell(
                        date = date,
                        isCurrentMonth = YearMonth.from(date) == yearMonth,
                        entry = entries[date],
                        shifts = shifts,
                        today = today,
                        onClick = { onDayClick(date) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isCurrentMonth: Boolean,
    entry: ScheduleEntryEntity?,
    shifts: List<ShiftTypeEntity>,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shift = entry?.shiftTypeId?.let { id -> shifts.firstOrNull { it.id == id } }
    val isToday = date == today
    val bgColor = shift?.color?.let { Color(it) } ?: DayOffColor.copy(alpha = 0.25f)
    val fgColor = if (shift != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val hasTrace = entry?.originalShiftTypeId != null

    Box(
        modifier = modifier
            .padding(2.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .then(
                if (isToday) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(vertical = 4.dp)) {
            Text(
                text = date.dayOfMonth.toString(),
                fontSize = 11.sp,
                color = if (shift != null) Color.White.copy(alpha = 0.9f) else fgColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = shift?.shortName ?: "休",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (shift != null) Color.White else fgColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        // 换班角标
        if (hasTrace) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(ChangeBadgeColor),
                contentAlignment = Alignment.Center
            ) {
                Text("调", color = Color.White, fontSize = 8.sp)
            }
        }
        // 非当月日期压暗
        if (!isCurrentMonth) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.White.copy(alpha = 0.55f))
            )
        }
    }
}
