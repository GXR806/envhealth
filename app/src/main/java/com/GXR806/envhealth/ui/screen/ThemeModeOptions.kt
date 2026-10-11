package com.GXR806.envhealth.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.GXR806.envhealth.data.local.datastore.SettingsSnapshot

@Composable
fun ThemeModeOptions(mode: String, enabled: Boolean, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().selectableGroup()) {
        listOf(SettingsSnapshot.THEME_MODE_SYSTEM to "跟随系统", SettingsSnapshot.THEME_MODE_LIGHT to "浅色", SettingsSnapshot.THEME_MODE_DARK to "深色").forEach { (value, label) ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(mode == value, enabled, Role.RadioButton) { onSelect(value) }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RadioButton(selected = mode == value, onClick = null, enabled = enabled)
                Text(label)
            }
        }
    }
}
