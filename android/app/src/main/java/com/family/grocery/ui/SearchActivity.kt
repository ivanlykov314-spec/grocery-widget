package com.family.grocery.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.family.grocery.Item
import com.family.grocery.Repo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Поиск товара поверх рабочего стола (лупа на виджете). */
class SearchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        if (!Repo.isConfigured) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        setContent {
            AppTheme {
                var addName by remember { mutableStateOf<String?>(null) }
                val n = addName
                if (n == null) {
                    SearchDialog(onDismiss = { finish() }, onAdd = { addName = it })
                } else {
                    AddItemDialog(onDismiss = { finish() }, initialName = n)
                }
            }
        }
    }
}

/** Для поиска: без регистра, «ё» = «е», лишние пробелы не мешают. */
internal fun normalize(s: String) = s.lowercase().replace('ё', 'е').trim().replace(Regex("\\s+"), " ")

/**
 * Совпадение по всем словам запроса в любом порядке («лук крас» найдёт «Лук красный»).
 * Сначала товары, название которых начинается с запроса, потом — где слово начинается с него, потом остальные.
 */
internal fun searchItems(items: List<Item>, query: String): List<Item> {
    val q = normalize(query)
    if (q.isEmpty()) return emptyList()
    val words = q.split(' ')
    return items
        .filter { item ->
            val hay = normalize(item.name) + " " + normalize(item.note)
            words.all { it in hay }
        }
        .sortedWith(
            compareBy<Item>(
                { item ->
                    val n = normalize(item.name)
                    when {
                        n.startsWith(q) -> 0
                        n.split(' ').any { it.startsWith(words.first()) } -> 1
                        else -> 2
                    }
                },
                { normalize(it.name) },
            ),
        )
}

@Composable
fun SearchDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Repo.state.collectAsState()
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { focus.requestFocus() }
    }

    val results = remember(query, state) { searchItems(state.items, query) }
    val catName = remember(state) { state.categories.associate { it.id to it.name } }
    val exact = results.any { normalize(it.name) == normalize(query) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Поиск") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Например, «молоко»") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Search,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                when {
                    query.isBlank() -> Text(
                        "Галочка — есть дома, без галочки — купить.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    results.isEmpty() -> Text(
                        "Ничего не найдено",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 340.dp)) {
                        items(results, key = { it.id }) { item ->
                            ResultRow(
                                item = item,
                                category = item.categoryId?.let { catName[it] },
                                onToggle = { scope.launch { runCatching { Repo.setInStock(ctx, item.id, !item.inStock) } } },
                            )
                        }
                    }
                }
                if (query.isNotBlank() && !exact) {
                    TextButton(onClick = { onAdd(query.trim().replaceFirstChar { it.uppercase() }) }) {
                        Text("+ Добавить «${query.trim()}»", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
    )
}

@Composable
private fun ResultRow(item: Item, category: String?, onToggle: () -> Unit) {
    val needed = !item.inStock
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Checkbox(
            checked = item.inStock,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(uncheckedColor = MaterialTheme.colorScheme.error),
        )
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                fontWeight = if (needed) FontWeight.Bold else FontWeight.Normal,
                color = if (needed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            val sub = listOfNotNull(category, item.note.takeIf { it.isNotBlank() }?.let { "✎ $it" }).joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
