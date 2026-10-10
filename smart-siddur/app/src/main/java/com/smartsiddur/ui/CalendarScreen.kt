package com.smartsiddur.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartsiddur.R
import com.smartsiddur.data.MenuEntry
import com.smartsiddur.logic.CalendarDay
import com.smartsiddur.logic.DayKind
import com.smartsiddur.logic.HebrewMonth
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val WEEKDAYS = listOf("א׳", "ב׳", "ג׳", "ד׳", "ה׳", "ו׳", "ש׳")
private val fullDate = DateTimeFormatter.ofPattern("EEEE, d בMMMM yyyy", Locale.forLanguageTag("he"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(entry: MenuEntry, context: ScreenContext) {
    val inIsrael = context.settings.inIsrael
    val today = remember { LocalDate.now() }
    var selectedEpochDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    val selected = LocalDate.ofEpochDay(selectedEpochDay)
    var month by remember(inIsrael) { mutableStateOf(HebrewMonth.containing(selected, inIsrael)) }

    fun showMonth(newMonth: HebrewMonth) {
        month = newMonth
        if (selected !in newMonth) selectedEpochDay = newMonth.days.first().date.toEpochDay()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(entry.title) },
                navigationIcon = {
                    IconButton(onClick = context.openMenu) {
                        Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.menu))
                    }
                },
                actions = {
                    TextButton(onClick = {
                        selectedEpochDay = today.toEpochDay()
                        month = HebrewMonth.containing(today, inIsrael)
                    }) { Text(stringResource(R.string.calendar_today)) }
                },
            )
        },
    ) { padding ->
        val monthEvents = month.days.filter { it.events.isNotEmpty() }
        val selectedDay = month.days.firstOrNull { it.date == selected }
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                MonthHeader(month, onPrevious = { showMonth(month.previous()) }, onNext = { showMonth(month.next()) })
            }
            item {
                MonthGrid(month, today, selected) { selectedEpochDay = it.date.toEpochDay() }
            }
            item { Legend() }
            if (selectedDay != null) {
                item { DayDetails(selectedDay) }
            }
            item {
                Text(
                    stringResource(R.string.calendar_month_events),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (monthEvents.isEmpty()) {
                item { Text(stringResource(R.string.calendar_no_events)) }
            }
            items(monthEvents, key = { it.date.toEpochDay() }) { day ->
                EventRow(day) { selectedEpochDay = day.date.toEpochDay() }
            }
        }
    }
}

@Composable
private fun MonthHeader(month: HebrewMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.calendar_prev))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(month.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                month.gregorianRange,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.calendar_next))
        }
    }
}

@Composable
private fun MonthGrid(month: HebrewMonth, today: LocalDate, selected: LocalDate, onSelect: (CalendarDay) -> Unit) {
    val cells: List<CalendarDay?> = List(month.leadingBlanks) { null } + month.days
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            WEEKDAYS.forEach { name ->
                Text(
                    name,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (name == WEEKDAYS.last()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until 7) {
                    val day = week.getOrNull(i)
                    Box(Modifier.weight(1f)) {
                        if (day != null) {
                            DayCell(day, isToday = day.date == today, isSelected = day.date == selected) { onSelect(day) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun kindColor(kind: DayKind, isShabbat: Boolean): Color {
    val colors = MaterialTheme.colorScheme
    return when (kind) {
        DayKind.YOM_TOV -> colors.primaryContainer
        DayKind.FAST -> colors.errorContainer
        DayKind.ROSH_CHODESH -> colors.secondaryContainer
        DayKind.MINOR -> colors.tertiaryContainer
        DayKind.NONE -> if (isShabbat) colors.surfaceVariant else colors.surfaceContainer
    }
}

@Composable
private fun DayCell(day: CalendarDay, isToday: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    var modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(0.78f)
        .background(kindColor(day.kind, day.dayOfWeek == 7), shape)
    if (isSelected || isToday) {
        modifier = modifier.border(
            BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) colors.onSurface else colors.primary),
            shape,
        )
    }
    Column(
        modifier = modifier.clickable(onClick = onClick).padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            day.hebrewDay,
            fontSize = 17.sp,
            fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (isToday) colors.primary else colors.onSurface,
        )
        Text(day.date.dayOfMonth.toString(), fontSize = 10.sp, color = colors.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        day.events.firstOrNull()?.let {
            Text(
                it,
                fontSize = 8.sp,
                lineHeight = 9.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(
            DayKind.YOM_TOV to "יום טוב",
            DayKind.ROSH_CHODESH to "ראש חודש",
            DayKind.MINOR to "מועד",
            DayKind.FAST to "תענית",
        ).forEach { (kind, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(kindColor(kind, false), CircleShape))
                Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

@Composable
private fun DayDetails(day: CalendarDay) {
    Card(
        colors = CardDefaults.cardColors(containerColor = kindColor(day.kind, day.dayOfWeek == 7)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(day.hebrewDate, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(fullDate.format(day.date), style = MaterialTheme.typography.bodyMedium)
            day.events.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

@Composable
private fun EventRow(day: CalendarDay, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).background(kindColor(day.kind, day.dayOfWeek == 7), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(day.hebrewDay, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(day.events.joinToString(" · "), style = MaterialTheme.typography.bodyLarge)
            Text(
                "יום ${WEEKDAYS[day.dayOfWeek - 1]} · ${day.date.dayOfMonth}/${day.date.monthValue}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
