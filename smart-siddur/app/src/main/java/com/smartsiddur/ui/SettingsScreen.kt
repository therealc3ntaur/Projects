package com.smartsiddur.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.smartsiddur.ui.theme.SiddurSerif
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartsiddur.R
import com.smartsiddur.data.Settings
import com.smartsiddur.data.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(context: ScreenContext) {
    val settings = context.settings
    val catalog = context.repository.catalog
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = context.openMenu) {
                        Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.menu))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsHeader(stringResource(R.string.settings_nusach))
            catalog.nuschaot.forEach { nusach ->
                ListItem(
                    headlineContent = { Text(nusach.title) },
                    leadingContent = { RadioButton(selected = settings.nusach == nusach.id, onClick = null) },
                    modifier = Modifier.selectable(
                        selected = settings.nusach == nusach.id,
                        role = Role.RadioButton,
                        onClick = { settings.nusach = nusach.id },
                    ),
                )
            }

            SettingsHeader(stringResource(R.string.settings_theme))
            listOf(
                ThemeMode.SYSTEM to R.string.theme_system,
                ThemeMode.LIGHT to R.string.theme_light,
                ThemeMode.DARK to R.string.theme_dark,
            ).forEach { (mode, label) ->
                ListItem(
                    headlineContent = { Text(stringResource(label)) },
                    leadingContent = { RadioButton(selected = settings.themeMode == mode, onClick = null) },
                    modifier = Modifier.selectable(
                        selected = settings.themeMode == mode,
                        role = Role.RadioButton,
                        onClick = { settings.themeMode = mode },
                    ),
                )
            }

            SettingsHeader(stringResource(R.string.settings_location))
            SwitchRow(
                title = stringResource(R.string.settings_in_israel),
                description = stringResource(R.string.settings_in_israel_desc),
                checked = settings.inIsrael,
                onChange = { settings.inIsrael = it },
            )

            SettingsHeader(stringResource(R.string.settings_display))
            SwitchRow(
                title = stringResource(R.string.settings_smart),
                description = stringResource(R.string.settings_smart_desc),
                checked = settings.smartMode,
                onChange = { settings.smartMode = it },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_font_size)) },
                supportingContent = {
                    Column {
                        Text(stringResource(R.string.settings_font_size_desc))
                        Slider(
                            value = settings.fontSize,
                            onValueChange = { settings.fontSize = it },
                            valueRange = Settings.MIN_FONT_SIZE..Settings.MAX_FONT_SIZE,
                        )
                        Text(
                            "בָּרוּךְ אַתָּה יְהֹוָה",
                            fontSize = settings.fontSize.sp,
                            fontFamily = SiddurSerif,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SettingsHeader(stringResource(R.string.settings_about))
            Text(
                stringResource(R.string.settings_about_text),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SettingsHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}
