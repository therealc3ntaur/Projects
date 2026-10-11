package com.smartsiddur.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartsiddur.R
import com.smartsiddur.data.MenuEntry
import com.smartsiddur.data.PrayerRepository
import com.smartsiddur.data.Settings
import com.smartsiddur.data.DeviceLocation
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.smartsiddur.logic.Place
import com.smartsiddur.logic.PrayerTime
import java.time.ZoneId
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/** What every screen gets from the app shell. */
class ScreenContext(
    val repository: PrayerRepository,
    val settings: Settings,
    val openMenu: () -> Unit,
)

/**
 * The kinds of screens a menu entry in assets/siddur/index.json can open, keyed by its "type".
 * Adding a new prayer only needs a JSON file and a menu entry; a new *kind* of screen
 * (e.g. a zmanim page) is registered here.
 */
private val screens: Map<String, @Composable (MenuEntry, ScreenContext) -> Unit> = mapOf(
    "prayer" to { entry, context -> PrayerScreen(entry, context) },
    "calendar" to { entry, context -> CalendarScreen(entry, context) },
)

private val icons: Map<String, ImageVector> = mapOf(
    "calendar" to Icons.Filled.DateRange,
)

private const val SETTINGS_ID = "settings"

@Composable
fun SiddurApp(repository: PrayerRepository, settings: Settings) {
    val catalog = repository.catalog
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    // Open on the prayer that fits the time of day
    var currentId by rememberSaveable {
        val place = Place.of(settings.latitude, settings.longitude, settings.inIsrael, ZoneId.systemDefault())
        mutableStateOf(PrayerTime.suggestedPrayerId(ZonedDateTime.now(), place))
    }
    LocationPermission(settings)
    val context = ScreenContext(repository, settings, openMenu = { scope.launch { drawerState.open() } })

    fun select(id: String) {
        currentId = id
        scope.launch { drawerState.close() }
    }

    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 12.dp),
                    )
                    catalog.menu.forEach { group ->
                        Text(
                            group.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                        )
                        group.entries.forEach { entry ->
                            NavigationDrawerItem(
                                label = { Text(entry.title, style = MaterialTheme.typography.titleMedium) },
                                icon = icons[entry.type]?.let { icon -> { Icon(icon, contentDescription = null) } },
                                selected = entry.id == currentId,
                                onClick = { select(entry.id) },
                            )
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.settings)) },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        selected = currentId == SETTINGS_ID,
                        onClick = { select(SETTINGS_ID) },
                        modifier = Modifier.padding(bottom = 24.dp),
                    )
                }
            }
        },
    ) {
        key(currentId) {
            if (currentId == SETTINGS_ID) {
                SettingsScreen(context)
            } else {
                val entry = catalog.entry(currentId) ?: catalog.entries.first()
                val screen = screens[entry.type]
                if (screen != null) {
                    screen(entry, context)
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(entry.title)
                    }
                }
            }
        }
    }
}

/**
 * Asks once for approximate location (for zmanim), and refreshes the stored location on each
 * launch when allowed. Without it, zmanim are computed for an approximate place.
 */
@Composable
private fun LocationPermission(settings: Settings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scope.launch { DeviceLocation.refresh(context, settings) }
    }
    LaunchedEffect(Unit) {
        when {
            DeviceLocation.hasPermission(context) -> DeviceLocation.refresh(context, settings)
            !settings.locationAsked -> {
                settings.locationAsked = true
                launcher.launch(DeviceLocation.PERMISSION)
            }
        }
    }
}
