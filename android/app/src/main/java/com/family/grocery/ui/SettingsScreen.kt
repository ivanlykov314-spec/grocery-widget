@file:OptIn(ExperimentalMaterial3Api::class)

package com.family.grocery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.family.grocery.Diag
import com.family.grocery.Repo
import com.family.grocery.WidgetSettings
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Экран «Настройки виджета». Всё применяется к виджету сразу. */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ws by Repo.widgetSettings.collectAsState()
    val onlyNeeded by Repo.onlyNeeded.collectAsState()

    BackHandler(onBack = onBack)

    fun save(n: WidgetSettings) {
        scope.launch { Repo.saveWidgetSettings(ctx, n) }
    }

    // Слайдеры двигаются плавно, а сохраняются, когда палец отпущен.
    var transparency by remember(ws.opacity) { mutableFloatStateOf((100 - ws.opacity).toFloat()) }
    var textSize by remember(ws.textSize) { mutableFloatStateOf(ws.textSize.toFloat()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки виджета") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Изменения сразу появляются на виджете. Чтобы растянуть виджет, удерживайте его на " +
                    "рабочем столе и потяните за рамку.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Section("Внешний вид")

            Label("Прозрачность фона: ${transparency.roundToInt()}%")
            Slider(
                value = transparency,
                onValueChange = { transparency = it },
                onValueChangeFinished = { save(ws.copy(opacity = 100 - transparency.roundToInt())) },
                valueRange = 0f..100f,
                steps = 19,
            )

            Label("Тема")
            Choice(
                listOf("system" to "Как в системе", "light" to "Светлая", "dark" to "Тёмная"),
                ws.theme,
            ) { save(ws.copy(theme = it)) }

            Label("Размер текста: ${textSize.roundToInt()}")
            Slider(
                value = textSize,
                onValueChange = { textSize = it },
                onValueChangeFinished = { save(ws.copy(textSize = textSize.roundToInt())) },
                valueRange = 12f..24f,
                steps = 11,
            )

            Label("Цвет товаров, которые нужно купить")
            Choice(
                listOf("red" to "Красный", "orange" to "Оранжевый", "accent" to "Цвет темы"),
                ws.neededColor,
            ) { save(ws.copy(neededColor = it)) }

            Label("Колонок товаров")
            Choice(listOf("1" to "Одна", "2" to "Две"), ws.columns.toString()) {
                save(ws.copy(columns = it.toInt()))
            }

            SwitchRow("Компактный режим", "Меньше отступов — больше товаров на экране", ws.compact) {
                save(ws.copy(compact = it))
            }
            SwitchRow("Заголовок «Покупки»", null, ws.showTitle) { save(ws.copy(showTitle = it)) }

            HorizontalDivider()
            Section("Список")

            SwitchRow("Категории вкладками", "Вкладки «Все», «Овощи», «Фрукты»… вверху виджета", ws.tabs) {
                save(ws.copy(tabs = it))
            }
            if (ws.tabs) {
                Label("Вид вкладок")
                Choice(
                    listOf("chips" to "Все сразу", "arrows" to "‹ Одна строка ›"),
                    ws.tabStyle,
                ) { save(ws.copy(tabStyle = it)) }
            }
            SwitchRow("Показывать категории", "На вкладке «Все»: если выключить — один общий список по алфавиту", ws.showCategories) {
                save(ws.copy(showCategories = it))
            }
            SwitchRow("Сначала то, что нужно купить", "Товары без галочки поднимаются наверх (при снятии галочки товар переместится)", ws.neededFirst) {
                save(ws.copy(neededFirst = it))
            }
            SwitchRow(
                "Только то, что нужно купить",
                "То же, что переключатель «Купить: N» на виджете",
                onlyNeeded,
            ) {
                scope.launch { Repo.setOnlyNeeded(it); Repo.updateWidgets(ctx) }
            }

            HorizontalDivider()
            OutlinedButton(onClick = { save(WidgetSettings()) }, modifier = Modifier.fillMaxWidth()) {
                Text("Сбросить настройки")
            }

            HorizontalDivider()
            Section("Диагностика")
            var lastError by remember { mutableStateOf(Diag.read(ctx)) }
            SelectionContainer {
                Text(
                    lastError ?: "Ошибок нет",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
            var events by remember { mutableStateOf(Diag.readEvents(ctx)) }
            Label("Журнал виджета (последние события)")
            SelectionContainer {
                Text(
                    events ?: "Пусто — виджет ещё ничего не получал",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { lastError = Diag.read(ctx); events = Diag.readEvents(ctx) }, modifier = Modifier.weight(1f)) {
                    Text("Обновить")
                }
                OutlinedButton(onClick = { Diag.clear(ctx); lastError = null; events = null }, modifier = Modifier.weight(1f)) {
                    Text("Очистить")
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun Choice(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (key, label) ->
            FilterChip(selected = key == selected, onClick = { onSelect(key) }, label = { Text(label) })
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
