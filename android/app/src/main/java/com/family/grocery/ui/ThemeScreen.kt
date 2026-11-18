@file:OptIn(ExperimentalMaterial3Api::class)

package com.family.grocery.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.family.grocery.Repo

/** «Оформление»: цветовая тема + светлая/тёмная/чёрная. Применяется сразу, к приложению и окнам над виджетом. */
@Composable
fun ThemeScreen(onBack: () -> Unit) {
    val themeId by Repo.appTheme.collectAsState()
    val mode by Repo.appMode.collectAsState()
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Оформление") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Режим", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                LOOK_MODES.forEach { (key, label) ->
                    FilterChip(
                        selected = key == mode,
                        onClick = { Repo.setAppLook(mode = key) },
                        label = { Text(label, maxLines = 1) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
            Text(
                "«Чёрная» — тёмная тема с чистым чёрным фоном: красиво на тёмных обоях и бережёт батарею.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))
            Text("Цвет", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            LOOK_THEMES.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { t ->
                        ThemeCard(
                            title = t.title,
                            subtitle = if (t.id == "system") {
                                if (Build.VERSION.SDK_INT >= 31) "цвета из обоев" else "нужен Android 12"
                            } else null,
                            scheme = schemeFor(t.id, mode),
                            selected = t.id == themeId,
                            onClick = { Repo.setAppLook(theme = t.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Text(
                "Виджет по-прежнему берёт цвета из темы телефона — его вид настраивается в «Настройках виджета».",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Карточка-превью: маленький «экран» в цветах темы — сводка, чип и два товара. */
@Composable
private fun ThemeCard(
    title: String,
    subtitle: String?,
    scheme: ColorScheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(shape)
            .border(
                BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                shape,
            )
            .background(scheme.surface)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Мини-сводка
        Box(
            Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(12.dp)).background(scheme.primaryContainer),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(36.dp).height(6.dp).clip(CircleShape).background(scheme.onPrimaryContainer.copy(alpha = 0.6f)))
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(18.dp).clip(CircleShape).background(scheme.primary))
            }
        }
        // Чипы
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.width(30.dp).height(12.dp).clip(CircleShape).background(scheme.primary))
            Box(Modifier.width(24.dp).height(12.dp).clip(CircleShape).border(1.dp, scheme.outline, CircleShape))
            Box(Modifier.width(20.dp).height(12.dp).clip(CircleShape).border(1.dp, scheme.outline, CircleShape))
        }
        // Карточка категории с двумя товарами
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(scheme.surfaceContainerLow).padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            PreviewItem(checked = true, scheme = scheme)
            PreviewItem(checked = false, scheme = scheme)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = scheme.onSurface, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                if (subtitle != null) Text(subtitle, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            if (selected) {
                Surface(shape = CircleShape, color = scheme.primary, contentColor = scheme.onPrimary, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Filled.Check, null, modifier = Modifier.padding(3.dp))
                }
            }
        }
    }
}

@Composable
private fun PreviewItem(checked: Boolean, scheme: ColorScheme) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val box = Modifier.size(11.dp).clip(RoundedCornerShape(3.dp))
        if (checked) Box(box.background(scheme.primary))
        else Box(box.border(1.5.dp, scheme.error, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier.width(if (checked) 48.dp else 38.dp).height(6.dp).clip(CircleShape)
                .background(if (checked) scheme.onSurface.copy(alpha = 0.55f) else scheme.error),
        )
    }
}

