@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.family.grocery.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.family.grocery.Category
import com.family.grocery.Group
import com.family.grocery.TAB_ALL
import com.family.grocery.Item
import com.family.grocery.Push
import com.family.grocery.Repo
import com.family.grocery.widget.ListWidgetReceiver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        setContent { AppTheme { Root() } }
    }
}

/** Окно «Добавить товар» поверх рабочего стола (кнопка + на виджете). */
class AddItemActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        if (!Repo.isConfigured) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        setContent { AppTheme { AddItemDialog(onDismiss = { finish() }) } }
    }
}

@Composable
private fun Root() {
    var configured by remember { mutableStateOf(Repo.isConfigured) }
    Surface(Modifier.fillMaxSize()) {
        if (configured) ListScreen(onDisconnect = { configured = false })
        else SetupScreen(onDone = { configured = true })
    }
}

@Composable
private fun SetupScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text("Подключение к общему списку", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Впишите адрес сервера из Railway и семейный ключ (FAMILY_KEY). " +
                "На телефоне жены — те же самые значения.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = url, onValueChange = { url = it },
            label = { Text("Адрес сервера") },
            placeholder = { Text("grocery-xxxx.up.railway.app") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
        )
        OutlinedTextField(
            value = key, onValueChange = { key = it },
            label = { Text("Семейный ключ") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
                capitalization = KeyboardCapitalization.None,
            ),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                scope.launch {
                    busy = true; error = null
                    try {
                        Repo.connect(ctx, url, key)
                        Push.init(ctx)
                        onDone()
                    } catch (e: Exception) {
                        error = "Не получилось подключиться: ${e.message ?: "нет связи"}"
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy && url.isNotBlank() && key.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "Подключаюсь…" else "Подключить") }
    }
}

private fun widgetComponent(ctx: Context) = ComponentName(ctx, ListWidgetReceiver::class.java)

private fun hasWidget(ctx: Context): Boolean =
    AppWidgetManager.getInstance(ctx).getAppWidgetIds(widgetComponent(ctx)).isNotEmpty()

/** Попросить лаунчер добавить виджет на рабочий стол (появится системное окно). */
private fun requestPinWidget(ctx: Context) {
    val mgr = AppWidgetManager.getInstance(ctx)
    val ok = mgr.isRequestPinAppWidgetSupported &&
        runCatching { mgr.requestPinAppWidget(widgetComponent(ctx), null, null) }.getOrDefault(false)
    if (ok) {
        Toast.makeText(ctx, "Подтвердите добавление виджета", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(
            ctx,
            "Лаунчер не разрешил. Проверьте разрешение «Ярлыки рабочего стола» для приложения.",
            Toast.LENGTH_LONG,
        ).show()
    }
}

@Composable
private fun ListScreen(onDisconnect: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by Repo.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    var showAdd by remember { mutableStateOf(false) }
    var showNewCategory by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<Item?>(null) }
    var noteItem by remember { mutableStateOf<Item?>(null) }
    var editCategory by remember { mutableStateOf<Category?>(null) }
    var menu by remember { mutableStateOf(false) }
    var widgetPlaced by remember { mutableStateOf(hasWidget(ctx)) }
    LaunchedEffect(Unit) {
        while (true) { widgetPlaced = hasWidget(ctx); delay(2_000) }
    }

    fun refresh() {
        scope.launch {
            try { Repo.refresh(ctx) } catch (e: Exception) { snackbar.showSnackbar("Нет связи с сервером") }
        }
    }
    LaunchedEffect(Unit) { refresh() }

    val currentTab by Repo.selectedTab.collectAsState()
    var onlyNeededApp by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    if (settingsOpen) {
        SettingsScreen(onBack = { settingsOpen = false })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Покупки · купить ${s.neededCount}") },
                actions = {
                    IconButton(onClick = { refresh() }) { Icon(Icons.Filled.Refresh, "Обновить") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Меню") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Настройки виджета") },
                            onClick = { menu = false; settingsOpen = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Добавить виджет на рабочий стол") },
                            onClick = { menu = false; requestPinWidget(ctx) },
                        )
                        DropdownMenuItem(
                            text = { Text("Новая категория") },
                            onClick = { menu = false; showNewCategory = true },
                        )
                        DropdownMenuItem(
                            text = { Text(if (Repo.pushEnabled) "Мгновенная синхронизация: вкл" else "Синхронизация: раз в 15 мин") },
                            onClick = { menu = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Отключиться от сервера") },
                            onClick = { menu = false; Repo.disconnect(); onDisconnect() },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, "Добавить товар") }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        val tabs = s.tabs()
        val selectedTab = s.validTab(currentTab)
        val groups = if (selectedTab == TAB_ALL) {
            s.groups(onlyNeeded = onlyNeededApp, includeEmpty = !onlyNeededApp)
        } else {
            val cat = s.categories.firstOrNull { it.id == selectedTab }
            listOf(Group(cat, s.itemsForTab(selectedTab).filter { !onlyNeededApp || !it.inStock }))
        }
        Column(Modifier.fillMaxSize().padding(pad)) {
        if (tabs.size > 1) {
            ScrollableTabRow(
                selectedTabIndex = tabs.indexOfFirst { it.id == selectedTab }.coerceAtLeast(0),
                edgePadding = 8.dp,
            ) {
                tabs.forEach { t ->
                    Tab(
                        selected = t.id == selectedTab,
                        onClick = { scope.launch { Repo.setSelectedTab(t.id); Repo.updateWidgets(ctx) } },
                        text = { Text(if (t.needed > 0) "${t.name} (${t.needed})" else t.name) },
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            FilterChip(
                selected = onlyNeededApp,
                onClick = { onlyNeededApp = !onlyNeededApp },
                label = { Text("Только нужно купить (${s.neededCount})") },
            )
        }
        if (!widgetPlaced) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Виджета пока нет на рабочем столе", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { requestPinWidget(ctx) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Добавить виджет на рабочий стол")
                    }
                }
            }
        }
        if (groups.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (onlyNeededApp) "Всё есть дома 👍" else "Добавьте первый товар кнопкой +")
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                groups.forEach { g ->
                    item(key = "c${g.category?.id ?: -1}") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = g.category != null) { editCategory = g.category }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                g.category?.name ?: "Без категории",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            if (g.category != null) {
                                Icon(Icons.Filled.Edit, "Изменить категорию", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    items(g.items, key = { "i${it.id}" }) { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                // Нажатие — изменить товар, долгое нажатие — комментарий («10 штук»).
                                .combinedClickable(onClick = { editItem = item }, onLongClick = { noteItem = item })
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = item.inStock,
                                onCheckedChange = { v -> scope.launch { Repo.setInStock(ctx, item.id, v) } },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.name,
                                    fontWeight = if (item.inStock) FontWeight.Normal else FontWeight.Bold,
                                    color = if (item.inStock) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                                )
                                if (item.note.isNotBlank()) {
                                    Text(
                                        "✎ ${item.note}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }

    if (showAdd) AddItemDialog(onDismiss = { showAdd = false })
    if (showNewCategory) CategoryDialog(category = null, onDismiss = { showNewCategory = false })
    editItem?.let { EditItemDialog(it, onDismiss = { editItem = null }) }
    noteItem?.let { NoteDialog(it, onDismiss = { noteItem = null }) }
    editCategory?.let { CategoryDialog(it, onDismiss = { editCategory = null }) }
}
