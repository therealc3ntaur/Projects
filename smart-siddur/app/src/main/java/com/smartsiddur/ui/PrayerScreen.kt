package com.smartsiddur.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartsiddur.R
import com.smartsiddur.data.ItemType
import com.smartsiddur.data.MenuEntry
import com.smartsiddur.data.Prayer
import com.smartsiddur.data.Rollover
import com.smartsiddur.data.Settings
import com.smartsiddur.data.ThemeMode
import com.smartsiddur.ui.theme.SiddurIcons
import com.smartsiddur.ui.theme.SiddurSerif
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.isSystemInDarkTheme
import com.smartsiddur.logic.Condition
import com.smartsiddur.logic.DayContext
import androidx.compose.ui.text.SpanStyle
import com.smartsiddur.logic.PrayerRow
import com.smartsiddur.logic.PrayerTime
import com.smartsiddur.logic.buildRows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerScreen(entry: MenuEntry, context: ScreenContext) {
    val settings = context.settings
    var prayer by remember { mutableStateOf<Result<Prayer>?>(null) }
    LaunchedEffect(entry.id, settings.nusach) {
        prayer = withContext(Dispatchers.IO) {
            runCatching { context.repository.load(settings.nusach, entry.id) }
        }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val loaded = prayer?.getOrNull()

    var afterNightfall by remember(loaded, settings.inIsrael) {
        mutableStateOf(
            loaded != null &&
                PrayerTime.isAfterNightfall(loaded.rollover, ZonedDateTime.now(), settings.inIsrael),
        )
    }
    val day = remember(loaded, afterNightfall, settings.inIsrael) {
        DayContext.of(
            PrayerTime.hebrewDayDate(ZonedDateTime.now(), afterNightfall),
            settings.inIsrael,
            loaded?.service,
        )
    }
    val rows = remember(loaded, day, settings.smartMode) {
        loaded?.let { buildRows(it, day.flags, settings.smartMode) }.orEmpty()
    }

    // Options embedded in a line: today's are highlighted; others are hidden (smart mode) or dimmed
    val colors = MaterialTheme.colorScheme
    val optionStyle: (String) -> SpanStyle? = remember(day, settings.smartMode, colors) {
        { condition ->
            when {
                Condition.isSatisfied(condition, day.flags) ->
                    SpanStyle(background = colors.secondaryContainer.copy(alpha = 0.8f))
                settings.smartMode -> null
                else -> SpanStyle(color = colors.onSurface.copy(alpha = 0.4f))
            }
        }
    }

    // Live font size while pinching; persisted when the gesture ends
    var fontSize by remember { mutableFloatStateOf(settings.fontSize) }
    LaunchedEffect(settings.fontSize) { fontSize = settings.fontSize }
    var zooming by remember { mutableStateOf(false) }

    KeepScreenOn()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // Tapping the prayer's name opens the jump-to-section menu
                    SectionJumpTitle(loaded?.title ?: entry.title, rows) { index ->
                        scope.launch { listState.scrollToItem(index) }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = context.openMenu) {
                        Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.menu))
                    }
                },
                actions = {
                    ThemeToggle(settings)
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (loaded != null) {
                DayBanner(
                    day = day,
                    smartMode = settings.smartMode,
                    showNightfallToggle = loaded.rollover != Rollover.NONE,
                    afterNightfall = afterNightfall,
                    onNightfallChange = { afterNightfall = it },
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .pinchToZoom(
                        onZoom = { zoom ->
                            zooming = true
                            fontSize = (fontSize * zoom).coerceIn(Settings.MIN_FONT_SIZE, Settings.MAX_FONT_SIZE)
                        },
                        onZoomEnd = {
                            zooming = false
                            settings.fontSize = fontSize
                        },
                    ),
            ) {
                when {
                    prayer == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    loaded == null -> Text(
                        stringResource(R.string.load_error),
                        Modifier.align(Alignment.Center).padding(24.dp),
                    )
                    else -> LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 48.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(rows, key = { _, row -> row.key }) { _, row ->
                            when (row) {
                                is PrayerRow.Header -> SectionHeader(row, fontSize)
                                is PrayerRow.Text -> PrayerText(row, fontSize, optionStyle)
                            }
                        }
                    }
                }
                ZoomIndicator(zooming, fontSize, Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
            }
        }
    }
}

@Composable
private fun ThemeToggle(settings: Settings) {
    val dark = settings.isDark(isSystemInDarkTheme())
    IconButton(onClick = { settings.themeMode = if (dark) ThemeMode.LIGHT else ThemeMode.DARK }) {
        Icon(
            if (dark) SiddurIcons.Sun else SiddurIcons.Moon,
            contentDescription = stringResource(if (dark) R.string.theme_to_light else R.string.theme_to_dark),
        )
    }
}

@Composable
private fun ZoomIndicator(visible: Boolean, fontSize: Float, modifier: Modifier) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        ) {
            Text(
                stringResource(R.string.font_size_indicator, fontSize.toInt()),
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SectionJumpTitle(title: String, rows: List<PrayerRow>, onJump: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val headers = remember(rows) {
        rows.withIndex().filter { (it.value as? PrayerRow.Header)?.title?.isNotBlank() == true }
    }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    enabled = headers.isNotEmpty(),
                    onClickLabel = stringResource(R.string.jump_to_section),
                ) { expanded = true }
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Text(title)
            if (headers.isNotEmpty()) Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            headers.forEach { (index, row) ->
                val header = row as PrayerRow.Header
                DropdownMenuItem(
                    text = {
                        Text(
                            header.title,
                            fontWeight = if (header.level == 1) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(start = if (header.level == 1) 0.dp else 16.dp),
                        )
                    },
                    onClick = {
                        expanded = false
                        onJump(index)
                    },
                )
            }
        }
    }
}

@Composable
private fun DayBanner(
    day: DayContext,
    smartMode: Boolean,
    showNightfallToggle: Boolean,
    afterNightfall: Boolean,
    onNightfallChange: (Boolean) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    day.hebrewDate,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (showNightfallToggle) {
                    FilterChip(
                        selected = afterNightfall,
                        onClick = { onNightfallChange(!afterNightfall) },
                        label = { Text(stringResource(R.string.evening_chip)) },
                    )
                }
            }
            val highlights = day.highlights
            val summary = when {
                !smartMode -> stringResource(R.string.showing_all)
                highlights.isNotEmpty() -> stringResource(R.string.today_inserts, highlights.joinToString(" · "))
                else -> null
            }
            if (summary != null) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun SectionHeader(row: PrayerRow.Header, fontSize: Float) {
    if (row.level == 1) {
        Column(Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 8.dp)) {
            Text(
                row.title,
                fontSize = (fontSize * 1.1f).sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            HorizontalDivider(Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    } else if (row.title.isBlank()) {
        Spacer(Modifier.height(24.dp))
    } else {
        Text(
            row.title,
            fontSize = (fontSize * 0.8f).sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(top = 32.dp, bottom = 6.dp),
        )
    }
}

@Composable
private fun PrayerText(row: PrayerRow.Text, fontSize: Float, optionStyle: (String) -> SpanStyle?) {
    val colors = MaterialTheme.colorScheme
    val text = remember(row.item.text, colors.onSurfaceVariant, optionStyle) {
        parseMarkup(row.item.text, colors.onSurfaceVariant, optionStyle)
    }
    val isText = row.item.type == ItemType.TEXT
    val scale = if (isText) 1f else 0.72f
    val color = if (isText) colors.onSurface else colors.onSurfaceVariant
    var modifier = Modifier.fillMaxWidth()
    if (row.inactive) modifier = modifier.alpha(0.4f)
    modifier = if (row.highlighted) {
        // Consecutive highlighted rows (label + text) share one continuous background
        val corner = 10.dp
        val shape = RoundedCornerShape(
            topStart = if (row.block.isStart) corner else 0.dp,
            topEnd = if (row.block.isStart) corner else 0.dp,
            bottomStart = if (row.block.isEnd) corner else 0.dp,
            bottomEnd = if (row.block.isEnd) corner else 0.dp,
        )
        modifier
            .padding(top = if (row.block.isStart) 4.dp else 0.dp, bottom = if (row.block.isEnd) 4.dp else 0.dp)
            .background(colors.secondaryContainer.copy(alpha = 0.55f), shape)
            .padding(
                start = 10.dp,
                end = 10.dp,
                top = if (row.block.isStart) 8.dp else 3.dp,
                bottom = if (row.block.isEnd) 8.dp else 3.dp,
            )
    } else {
        modifier.padding(vertical = 3.dp)
    }
    Text(
        text = text,
        modifier = modifier,
        fontSize = (fontSize * scale).sp,
        lineHeight = (fontSize * scale * 1.6f).sp,
        fontFamily = SiddurSerif,
        color = color,
        textAlign = if (isText) TextAlign.Justify else TextAlign.Start,
    )
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
