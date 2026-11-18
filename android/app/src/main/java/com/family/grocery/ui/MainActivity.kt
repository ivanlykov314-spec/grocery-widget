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
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.imePadding
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
        enableEdgeToEdge()
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
        Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(24.dp),
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

internal fun hasWidget(ctx: Context): Boolean =
    AppWidgetManager.getInstance(ctx).getAppWidgetIds(widgetComponent(ctx)).isNotEmpty()

/** Попросить лаунчер добавить виджет на рабочий стол (появится системное окно). */
internal fun requestPinWidget(ctx: Context) {
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

