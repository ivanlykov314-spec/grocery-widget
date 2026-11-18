@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.family.grocery.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.family.grocery.Category
import com.family.grocery.Group
import com.family.grocery.Item
import com.family.grocery.Repo
import com.family.grocery.TAB_ALL
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Главный экран: сводка, категории-чипы (как на виджете), категории карточками. */
@Composable
internal fun ListScreen(onDisconnect: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by Repo.state.collectAsState()
    val onlyNeeded by Repo.onlyNeeded.collectAsState()
    val currentTab by Repo.selectedTab.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    var showAdd by remember { mutableStateOf<String?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var showNewCategory by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<Item?>(null) }
    var noteItem by remember { mutableStateOf<Item?>(null) }
    var editCategory by remember { mutableStateOf<Category?>(null) }
    var menu by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf("list") }
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

    // «Только купить» — общий с виджетом: в магазине открыли приложение, и там то же самое.
    fun setOnly(v: Boolean) {
        Repo.setOnlyNeeded(v)
        if (v) Repo.setSelectedTab(TAB_ALL)
        scope.launch { Repo.updateWidgets(ctx) }
    }

    when (screen) {
        "settings" -> { SettingsScreen(onBack = { screen = "list" }); return }
        "theme" -> { ThemeScreen(onBack = { screen = "list" }); return }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val only = onlyNeeded && s.neededCount > 0

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Покупки", fontWeight = FontWeight.SemiBold) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                actions = {
                    IconButton(onClick = { showSearch = true }) { Icon(Icons.Filled.Search, "Поиск") }
                    IconButton(onClick = { refresh() }) { Icon(Icons.Filled.Refresh, "Обновить") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Меню") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Оформление") }, onClick = { menu = false; screen = "theme" })
                        DropdownMenuItem(text = { Text("Настройки виджета") }, onClick = { menu = false; screen = "settings" })
                        DropdownMenuItem(text = { Text("Новая категория") }, onClick = { menu = false; showNewCategory = true })
                        DropdownMenuItem(
                            text = { Text("Добавить виджет на рабочий стол") },
                            onClick = { menu = false; requestPinWidget(ctx) },
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
            ExtendedFloatingActionButton(
                onClick = { showAdd = "" },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Добавить") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { pad ->
        val tabs = s.tabs()
        val selectedTab = s.validTab(currentTab)
        val groups = if (selectedTab == TAB_ALL) {
            s.groups(onlyNeeded = only, includeEmpty = !only)
        } else {
            val cat = s.categories.firstOrNull { it.id == selectedTab }
            listOf(Group(cat, s.itemsForTab(selectedTab).filter { !only || !it.inStock }.sortedBy { it.name.lowercase() }))
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = pad.calculateTopPadding(),
                bottom = pad.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "summary") {
                SummaryCard(
                    needed = s.neededCount,
                    total = s.items.size,
                    only = only,
                    onToggle = { setOnly(!only) },
                )
            }
            if (!widgetPlaced) {
                item(key = "widget") {
                    Surface(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Виджета пока нет на рабочем столе", style = MaterialTheme.typography.titleMedium)
                            Button(onClick = { requestPinWidget(ctx) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Добавить виджет")
                            }
                        }
                    }
                }
            }
            if (tabs.size > 1) {
                item(key = "tabs") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(tabs, key = { it.id }) { t ->
                            val sel = t.id == selectedTab
                            FilterChip(
                                selected = sel,
                                onClick = { Repo.setSelectedTab(t.id); scope.launch { Repo.updateWidgets(ctx) } },
                                label = { Text(t.name) },
                                trailingIcon = if (t.needed > 0) {
                                    { CountPill(t.needed, sel) }
                                } else null,
                                shape = RoundedCornerShape(50),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            )
                        }
                    }
                }
            }
            if (groups.isEmpty() || groups.all { it.items.isEmpty() && selectedTab != TAB_ALL }) {
                item(key = "empty") {
                    EmptyState(if (only) "Всё есть дома" else "Здесь пока пусто", if (only) "🎉" else "🛒",
                        if (only) "Купить ничего не нужно" else "Добавьте товар кнопкой «Добавить»")
                }
            } else {
                groups.forEach { g ->
                    item(key = "c${g.category?.id ?: -1}") {
                        CategoryCard(
                            group = g,
                            onEditCategory = { editCategory = it },
                            onToggle = { item -> scope.launch { Repo.setInStock(ctx, item.id, !item.inStock) } },
                            onEditItem = { editItem = it },
                            onNote = { noteItem = it },
                        )
                    }
                }
            }
        }
    }

    showAdd?.let { AddItemDialog(onDismiss = { showAdd = null }, initialName = it) }
    if (showSearch) SearchDialog(onDismiss = { showSearch = false }, onAdd = { showSearch = false; showAdd = it })
    if (showNewCategory) CategoryDialog(category = null, onDismiss = { showNewCategory = false })
    editItem?.let { EditItemDialog(it, onDismiss = { editItem = null }) }
    noteItem?.let { NoteDialog(it, onDismiss = { noteItem = null }) }
    editCategory?.let { CategoryDialog(it, onDismiss = { editCategory = null }) }
}

@Composable
private fun SummaryCard(needed: Int, total: Int, only: Boolean, onToggle: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = c.primaryContainer,
        contentColor = c.onPrimaryContainer,
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (needed == 0) "Всё есть дома" else "Нужно купить",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (needed > 0) {
                        Text(
                            plural(needed, "товар", "товара", "товаров"),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Surface(shape = CircleShape, color = c.primary, contentColor = c.onPrimary, modifier = Modifier.size(52.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (needed == 0) Icons.Filled.Check else Icons.Filled.ShoppingCart, null)
                    }
                }
            }
            if (total > 0) {
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { (total - needed).toFloat() / total },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = c.primary,
                    trackColor = c.primary.copy(alpha = 0.18f),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Дома есть ${total - needed} из $total",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.onPrimaryContainer.copy(alpha = 0.75f),
                )
            }
            if (needed > 0) {
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
                    Text(if (only) "Показать весь список" else "Показать только что купить")
                }
            }
        }
    }
}

@Composable
private fun CountPill(n: Int, onPrimary: Boolean) {
    val c = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = if (onPrimary) c.onPrimary else c.errorContainer,
        contentColor = if (onPrimary) c.primary else c.onErrorContainer,
    ) {
        Text(
            "$n",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun CategoryCard(
    group: Group,
    onEditCategory: (Category) -> Unit,
    onToggle: (Item) -> Unit,
    onEditItem: (Item) -> Unit,
    onNote: (Item) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val needed = group.items.count { !it.inStock }
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = c.surfaceContainerLow,
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(enabled = group.category != null, onClick = { group.category?.let(onEditCategory) })
                    .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    group.category?.name ?: "Без категории",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = c.primary,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (needed > 0) {
                    Spacer(Modifier.width(8.dp))
                    CountPill(needed, false)
                }
                Spacer(Modifier.weight(1f))
                if (group.category != null) {
                    IconButton(onClick = { onEditCategory(group.category) }) {
                        Icon(Icons.Filled.Edit, "Изменить категорию", tint = c.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (group.items.isEmpty()) {
                Text(
                    "Пусто",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.onSurfaceVariant,
                    modifier = Modifier.padding(start = 18.dp, bottom = 10.dp),
                )
            }
            group.items.forEachIndexed { i, item ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = 60.dp, end = 16.dp), color = c.outlineVariant.copy(alpha = 0.5f))
                ItemRow(item, onToggle = { onToggle(item) }, onClick = { onEditItem(item) }, onLong = { onNote(item) })
            }
        }
    }
}

@Composable
private fun ItemRow(item: Item, onToggle: () -> Unit, onClick: () -> Unit, onLong: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val needed = !item.inStock
    val textColor by animateColorAsState(if (needed) c.error else c.onSurface, label = "itemColor")
    Row(
        Modifier
            .fillMaxWidth()
            // Нажатие — изменить товар, долгое нажатие — комментарий («10 штук»).
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(start = 6.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = item.inStock,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = c.primary,
                checkmarkColor = c.onPrimary,
                uncheckedColor = c.error,
            ),
        )
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (needed) FontWeight.SemiBold else FontWeight.Normal,
                color = textColor,
            )
            if (item.note.isNotBlank()) {
                Text("✎ ${item.note}", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, emoji: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp, start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(emoji, style = MaterialTheme.typography.displayMedium)
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 1 товар, 2 товара, 5 товаров. */
internal fun plural(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10
    val m100 = n % 100
    val w = when {
        m10 == 1 && m100 != 11 -> one
        m10 in 2..4 && m100 !in 12..14 -> few
        else -> many
    }
    return "$n $w"
}
