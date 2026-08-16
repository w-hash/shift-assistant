package com.lh.shiftassistant.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.lh.shiftassistant.MainActivity
import com.lh.shiftassistant.ShiftAssistantApp
import com.lh.shiftassistant.data.WidgetDay

/** ActionParameters 的 Key 名会作为 Intent extra 键写入启动 Activity 的 Intent */
private val DATE_KEY = ActionParameters.Key<String>(MainActivity.EXTRA_DATE)

// 主题色:跟随 App 内手动日/夜设置(设置页/首页切换后立即 refreshAll)
private val LightBg = Color(0xFFF5F5F5)
private val DarkBg = Color(0xFF1C1C1C)
private val LightDateText = Color(0xFF212121)
private val DarkDateText = Color(0xFFE0E0E0)
private val LightSubText = Color(0xFF757575)
private val DarkSubText = Color(0xFF9E9E9E)

/**
 * 桌面小组件:显示今天 + 未来 N 天(默认4格,设置可切7格)。
 * 每格:星期、日期(今天显示"今")、班次简称 + 颜色;无班显示"休"。
 */
class ShiftWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as ShiftAssistantApp
        val days = runCatching {
            app.container.shiftRepository.widgetDays()
        }.getOrDefault(emptyList())
        val dark = runCatching {
            app.container.settingsRepository.darkThemeOnce()
        }.getOrDefault(false)

        provideContent {
            Row(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(if (dark) DarkBg else LightBg),
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                // defaultWeight 是 RowScope 成员扩展,需在 Row 内容作用域内调用(1.1.0 无参数,均分宽度)
                days.forEach { day ->
                    DayCell(day, dark = dark, modifier = GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: WidgetDay, dark: Boolean, modifier: GlanceModifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 2.dp, vertical = 6.dp)
            .clickable(
                actionStartActivity<MainActivity>(
                    parameters = actionParametersOf(DATE_KEY to day.date.toString())
                )
            ),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text(
            text = WEEKDAYS[day.date.dayOfWeek.value - 1],
            style = TextStyle(
                color = ColorProvider(color = if (dark) DarkSubText else LightSubText),
                fontSize = 10.sp
            )
        )
        Text(
            text = if (day.isToday) "今" else day.date.dayOfMonth.toString(),
            style = TextStyle(
                color = ColorProvider(color = if (dark) DarkDateText else LightDateText),
                fontSize = 14.sp,
                fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal
            )
        )
        Row(
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            val color = day.shiftColor
            if (color != null) {
                Column(
                    modifier = GlanceModifier
                        .size(6.dp)
                        .background(ColorProvider(color = Color(color)))
                ) {}
            }
            Text(
                text = day.shiftShortName ?: "休",
                style = TextStyle(
                    color = ColorProvider(
                        color = if (day.shiftShortName == null) {
                            if (dark) DarkSubText else LightSubText
                        } else {
                            Color(day.shiftColor?.toLong() ?: 0xFF212121L)
                        }
                    ),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

private val WEEKDAYS = arrayOf("一", "二", "三", "四", "五", "六", "日")
