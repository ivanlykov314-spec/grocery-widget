@file:OptIn(ExperimentalMaterial3Api::class)

package com.family.grocery.ui

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.family.grocery.Category
import com.family.grocery.Item
import com.family.grocery.Repo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(ctx)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

private fun friendly(e: Exception): String =
    e.message?.takeIf { it.isNotBlank() && !it.contains("Exception") }
        ?: "Нет связи с сервером"

/** Выбор категории: «Без категории», существующие и «+ Новая». */
@Composable
private fun CategoryPicker(
    categories: List<Category>,
    selectedId: Int?,
    newMode: Boolean,
    onSelect: (Int?) -> Unit,
    onNew: () -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            FilterChip(
                selected = !newMode && selectedId == null,
                onClick = { onSelect(null) },
                label = { Text("Без категории") },
            )
        }
        items(categories, key = { it.id }) { c ->
            FilterChip(
                selected = !newMode && selectedId == c.id,
                onClick = { onSelect(c.id) },
                label = { Text(c.name) },
            )
        }
        item {
            FilterChip(selected = newMode, onClick = onNew, label = { Text("+ Новая") })
        }
    }
}

/** Окно добавления: можно добавлять товары подряд, не закрывая его. */
@Composable
fun AddItemDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Repo.state.collectAsState()

    var name by remember { mutableStateOf("") }
    var catId by remember {
        // Если открыта вкладка категории — новый товар сразу попадёт в неё.
        val tab = Repo.selectedTab.value
        val fromTab = tab.takeIf { id -> id >= 0 && state.categories.any { it.id == id } }
        mutableStateOf(fromTab ?: Repo.lastCategoryId?.takeIf { id -> state.categories.any { it.id == id } })
    }
    var newCatMode by remember { mutableStateOf(false) }
    var newCatName by remember { mutableStateOf("") }
    var inStock by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { focus.requestFocus() }
    }

    fun submit() {
        val n = name.trim()
        if (n.isEmpty() || busy) return
        scope.launch {
            busy = true; error = null
            try {
                var cid = catId
                if (newCatMode) {
                    val cn = newCatName.trim()
                    if (cn.isEmpty()) { error = "Впишите название категории"; return@launch }
                    cid = Repo.addCategory(ctx, cn)
                    catId = cid; newCatMode = false; newCatName = ""
                }
                Repo.addItem(ctx, n, cid, inStock)
                Repo.lastCategoryId = cid
                info = "Добавлено: $n"
                name = ""
                runCatching { focus.requestFocus() }
            } catch (e: Exception) {
                error = friendly(e)
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить товар") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; info = null },
                    label = { Text("Название, например «Бананы»") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
                Spacer(Modifier.height(12.dp))
                Text("Категория", style = MaterialTheme.typography.labelLarge)
                CategoryPicker(
                    categories = state.categories,
                    selectedId = catId,
                    newMode = newCatMode,
                    onSelect = { catId = it; newCatMode = false },
                    onNew = { newCatMode = true },
                )
                if (newCatMode) {
                    OutlinedTextField(
                        value = newCatName,
                        onValueChange = { newCatName = it },
                        label = { Text("Новая категория, например «Овощи»") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { inStock = !inStock },
                ) {
                    Checkbox(checked = inStock, onCheckedChange = { inStock = it })
                    Text("Сейчас есть дома (галочка)")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                info?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }, enabled = !busy && name.isNotBlank()) { Text("Добавить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
    )
}

/** Изменить название/категорию товара или удалить его. */
@Composable
fun EditItemDialog(item: Item, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Repo.state.collectAsState()

    var name by remember { mutableStateOf(item.name) }
    var catId by remember { mutableStateOf(item.categoryId) }
    var confirmDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun run(block: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true; error = null
            try { block(); onDismiss() } catch (e: Exception) { error = friendly(e) } finally { busy = false }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Товар") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("Категория", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(selected = catId == null, onClick = { catId = null }, label = { Text("Без категории") })
                    }
                    items(state.categories, key = { it.id }) { c ->
                        FilterChip(selected = catId == c.id, onClick = { catId = c.id }, label = { Text(c.name) })
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { run { Repo.updateItem(ctx, item.id, name.trim(), catId) } },
                enabled = !busy && name.isNotBlank(),
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(
                onClick = { if (confirmDelete) run { Repo.deleteItem(ctx, item.id) } else confirmDelete = true },
                enabled = !busy,
            ) {
                Text(
                    if (confirmDelete) "Точно удалить?" else "Удалить",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}

/** Новая категория (category == null) или переименование/удаление существующей. */
@Composable
fun CategoryDialog(category: Category?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(category?.name ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun run(block: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true; error = null
            try { block(); onDismiss() } catch (e: Exception) { error = friendly(e) } finally { busy = false }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category == null) "Новая категория" else "Категория") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                if (category != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "При удалении категории товары останутся — в группе «Без категории».",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val n = name.trim()
                    run {
                        if (category == null) Repo.addCategory(ctx, n) else Repo.renameCategory(ctx, category.id, n)
                    }
                },
                enabled = !busy && name.isNotBlank(),
            ) { Text("Сохранить") }
        },
        dismissButton = {
            if (category != null) {
                TextButton(
                    onClick = { if (confirmDelete) run { Repo.deleteCategory(ctx, category.id) } else confirmDelete = true },
                    enabled = !busy,
                ) {
                    Text(if (confirmDelete) "Точно удалить?" else "Удалить", color = MaterialTheme.colorScheme.error)
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}
