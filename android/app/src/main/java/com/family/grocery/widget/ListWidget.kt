package com.family.grocery.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.ToggleableStateKey
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.family.grocery.Item
import com.family.grocery.ListState
import com.family.grocery.R
import com.family.grocery.Repo
import com.family.grocery.Sync
import com.family.grocery.ui.AddItemActivity
import com.family.grocery.ui.MainActivity
import kotlinx.coroutines.withTimeoutOrNull

class ListWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Repo.init(context)
        provideContent {
            val s by Repo.state.collectAsState()
            val only by Repo.onlyNeeded.collectAsState()
            val error by Repo.lastError.collectAsState()
            GlanceTheme {
                WidgetBody(s, only, Repo.isConfigured, error)
            }
        }
    }
}

class ListWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ListWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Sync.schedulePeriodic(context)
        Sync.syncSoon(context)
    }
}

private val ItemIdKey = ActionParameters.Key<Int>("itemId")

@Composable
private fun WidgetBody(s: ListState, onlyNeeded: Boolean, configured: Boolean, error: String?) {
    val colors = GlanceTheme.colors
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.widgetBackground)
            .cornerRadius(20.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Header(s.neededCount, onlyNeeded)
        if (error != null) {
            Text(
                text = error,
                style = TextStyle(color = colors.error, fontSize = 12.sp),
                modifier = GlanceModifier.padding(start = 4.dp, bottom = 4.dp),
            )
        }
        if (!configured) {
            Text(
                text = "Нажмите, чтобы подключить список",
                style = TextStyle(color = colors.onSurface, fontSize = 15.sp),
                modifier = GlanceModifier.fillMaxWidth().padding(12.dp)
                    .clickable(actionStartActivity<MainActivity>()),
            )
        } else {
            val groups = s.groups(onlyNeeded = onlyNeeded, includeEmpty = false)
            if (groups.isEmpty()) {
                Text(
                    text = if (onlyNeeded) "Всё есть дома 👍" else "Список пуст — нажмите +",
                    style = TextStyle(color = colors.onSurfaceVariant, fontSize = 15.sp),
                    modifier = GlanceModifier.fillMaxWidth().padding(12.dp),
                )
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    groups.forEach { g ->
                        item(itemId = 1_000_000_000L + (g.category?.id ?: 0)) {
                            CategoryHeader(g.category?.name ?: "Без категории")
                        }
                        items(g.items, itemId = { it.id.toLong() }) { item ->
                            ItemRow(item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(needed: Int, onlyNeeded: Boolean) {
    val colors = GlanceTheme.colors
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Покупки",
            style = TextStyle(color = colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>()),
        )
        // Переключатель: «все товары» / «только то, что нужно купить»
        Text(
            text = if (onlyNeeded) "Купить: $needed" else "Все · купить $needed",
            style = TextStyle(color = colors.onSecondaryContainer, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            modifier = GlanceModifier
                .background(colors.secondaryContainer)
                .cornerRadius(14.dp)
                .padding(horizontal = 10.dp, vertical = 5.dp)
                .clickable(actionRunCallback<FilterAction>()),
        )
        Spacer(GlanceModifier.width(4.dp))
        IconBtn(R.drawable.ic_refresh, "Обновить", actionRunCallback<RefreshAction>())
        IconBtn(R.drawable.ic_add, "Добавить", actionStartActivity<AddItemActivity>())
    }
}

@Composable
private fun IconBtn(res: Int, description: String, action: Action) {
    Image(
        provider = ImageProvider(res),
        contentDescription = description,
        colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
        modifier = GlanceModifier.size(38.dp).padding(7.dp).clickable(action),
    )
}

@Composable
private fun CategoryHeader(name: String) {
    Text(
        text = name.uppercase(),
        style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold),
        modifier = GlanceModifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun ItemRow(item: Item) {
    val colors = GlanceTheme.colors
    CheckBox(
        checked = item.inStock,
        onCheckedChange = actionRunCallback<ToggleAction>(actionParametersOf(ItemIdKey to item.id)),
        text = item.name,
        style = TextStyle(
            // Без галочки = нужно купить: выделяем цветом и жирным.
            color = if (item.inStock) colors.onSurface else colors.error,
            fontSize = 16.sp,
            fontWeight = if (item.inStock) FontWeight.Normal else FontWeight.Bold,
        ),
        modifier = GlanceModifier.fillMaxWidth(),
    )
}

class ToggleAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Repo.init(context)
        val id = parameters[ItemIdKey] ?: return
        val newValue = parameters[ToggleableStateKey]
            ?: !(Repo.state.value.items.firstOrNull { it.id == id }?.inStock ?: return)
        Repo.setInStock(context, id, newValue)
    }
}

class FilterAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Repo.init(context)
        Repo.setOnlyNeeded(!Repo.onlyNeeded.value)
        ListWidget().updateAll(context)
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Repo.init(context)
        val ok = withTimeoutOrNull(9_000) { runCatching { Repo.refresh(context) }.isSuccess } == true
        if (!ok) Sync.syncSoon(context)
    }
}
