package com.family.grocery.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.CheckboxDefaults
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
import androidx.glance.color.ColorProvider as DayNightColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
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
import androidx.glance.unit.ColorProvider
import com.family.grocery.Group
import com.family.grocery.Item
import com.family.grocery.ListState
import com.family.grocery.R
import com.family.grocery.Repo
import com.family.grocery.Sync
import com.family.grocery.WidgetSettings
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
            val settings by Repo.widgetSettings.collectAsState()
            WidgetBody(s, only, Repo.isConfigured, error, palette(context, settings), settings)
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

/** Цвета виджета с учётом темы и прозрачности из настроек. */
private class Palette(
    val background: ColorProvider,
    val text: ColorProvider,
    val muted: ColorProvider,
    val accent: ColorProvider,
    val needed: ColorProvider,
    val chipBg: ColorProvider,
    val chipText: ColorProvider,
)

private fun palette(ctx: Context, ws: WidgetSettings): Palette {
    val dyn = Build.VERSION.SDK_INT >= 31
    fun sys(id: Int, fallback: Long) = if (dyn) Color(ctx.getColor(id)) else Color(fallback)
    fun cp(light: Color, dark: Color): ColorProvider = when (ws.theme) {
        "light" -> ColorProvider(light)
        "dark" -> ColorProvider(dark)
        else -> DayNightColorProvider(day = light, night = dark)
    }
    val alpha = ws.opacity / 100f
    val bgLight = sys(android.R.color.system_neutral1_50, 0xFFF7F7F2)
    val bgDark = sys(android.R.color.system_neutral1_900, 0xFF1C1B1F)
    val accentLight = sys(android.R.color.system_accent1_600, 0xFF2E7D32)
    val accentDark = sys(android.R.color.system_accent1_200, 0xFFA5D6A7)
    val (neededLight, neededDark) = when (ws.neededColor) {
        "orange" -> Color(0xFFE65100) to Color(0xFFFFB74D)
        "accent" -> accentLight to accentDark
        else -> Color(0xFFC62828) to Color(0xFFFF8A80)
    }
    return Palette(
        background = cp(bgLight.copy(alpha = alpha), bgDark.copy(alpha = alpha)),
        text = cp(Color(0xFF1B1B1F), Color(0xFFE6E1E5)),
        muted = cp(Color(0xFF5F5E62), Color(0xFFB0ADB3)),
        accent = cp(accentLight, accentDark),
        needed = cp(neededLight, neededDark),
        chipBg = cp(
            sys(android.R.color.system_accent2_100, 0xFFDDE5DB),
            sys(android.R.color.system_accent2_700, 0xFF3F4A3F),
        ),
        chipText = cp(Color(0xFF1B1B1F), Color(0xFFE6E1E5)),
    )
}

@Composable
private fun WidgetBody(
    s: ListState,
    onlyNeeded: Boolean,
    configured: Boolean,
    error: String?,
    p: Palette,
    ws: WidgetSettings,
) {
    val pad = if (ws.compact) 6.dp else 10.dp
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(p.background)
            .cornerRadius(20.dp)
            .padding(horizontal = pad, vertical = if (ws.compact) 4.dp else 8.dp)
    ) {
        Header(s.neededCount, onlyNeeded, p, ws)
        if (error != null) {
            Text(
                text = error,
                style = TextStyle(color = p.needed, fontSize = 12.sp),
                modifier = GlanceModifier.padding(start = 4.dp, bottom = 4.dp),
            )
        }
        if (!configured) {
            Text(
                text = "Нажмите, чтобы подключить список",
                style = TextStyle(color = p.text, fontSize = ws.textSize.sp),
                modifier = GlanceModifier.fillMaxWidth().padding(12.dp)
                    .clickable(actionStartActivity<MainActivity>()),
            )
        } else {
            val groups = visibleGroups(s, onlyNeeded, ws)
            if (groups.isEmpty()) {
                Text(
                    text = if (onlyNeeded) "Всё есть дома 👍" else "Список пуст — нажмите +",
                    style = TextStyle(color = p.muted, fontSize = ws.textSize.sp),
                    modifier = GlanceModifier.fillMaxWidth().padding(12.dp),
                )
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    groups.forEach { g ->
                        if (ws.showCategories) {
                            item(itemId = 1_000_000_000L + (g.category?.id ?: 0)) {
                                CategoryHeader(g.category?.name ?: "Без категории", p, ws)
                            }
                        }
                        if (ws.columns == 2) {
                            items(g.items.chunked(2), itemId = { it.first().id.toLong() }) { pair ->
                                Row(modifier = GlanceModifier.fillMaxWidth()) {
                                    pair.forEach { item ->
                                        Box(modifier = GlanceModifier.defaultWeight()) { ItemRow(item, p, ws) }
                                    }
                                    if (pair.size == 1) Spacer(GlanceModifier.defaultWeight())
                                }
                            }
                        } else {
                            items(g.items, itemId = { it.id.toLong() }) { item -> ItemRow(item, p, ws) }
                        }
                    }
                }
            }
        }
    }
}

private fun visibleGroups(s: ListState, onlyNeeded: Boolean, ws: WidgetSettings): List<Group> {
    fun sort(list: List<Item>) = if (ws.neededFirst) list.sortedBy { it.inStock } else list
    return if (ws.showCategories) {
        s.groups(onlyNeeded = onlyNeeded, includeEmpty = false).map { it.copy(items = sort(it.items)) }
    } else {
        val all = s.items.filter { !onlyNeeded || !it.inStock }.sortedBy { it.name.lowercase() }
        if (all.isEmpty()) emptyList() else listOf(Group(null, sort(all)))
    }
}

@Composable
private fun Header(needed: Int, onlyNeeded: Boolean, p: Palette, ws: WidgetSettings) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(bottom = if (ws.compact) 0.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (ws.showTitle) "Покупки" else "",
            style = TextStyle(color = p.text, fontSize = (ws.textSize + 2).sp, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>()),
        )
        // Переключатель: «все товары» / «только то, что нужно купить»
        Text(
            text = if (onlyNeeded) "Купить: $needed" else "Все · купить $needed",
            style = TextStyle(color = p.chipText, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            modifier = GlanceModifier
                .background(p.chipBg)
                .cornerRadius(14.dp)
                .padding(horizontal = 10.dp, vertical = 5.dp)
                .clickable(actionRunCallback<FilterAction>()),
        )
        Spacer(GlanceModifier.width(4.dp))
        IconBtn(R.drawable.ic_refresh, "Обновить", actionRunCallback<RefreshAction>(), p)
        IconBtn(R.drawable.ic_add, "Добавить", actionStartActivity<AddItemActivity>(), p)
    }
}

@Composable
private fun IconBtn(res: Int, description: String, action: Action, p: Palette) {
    Image(
        provider = ImageProvider(res),
        contentDescription = description,
        colorFilter = ColorFilter.tint(p.text),
        modifier = GlanceModifier.size(38.dp).padding(7.dp).clickable(action),
    )
}

@Composable
private fun CategoryHeader(name: String, p: Palette, ws: WidgetSettings) {
    Text(
        text = name.uppercase(),
        style = TextStyle(color = p.accent, fontSize = (ws.textSize - 4).coerceAtLeast(10).sp, fontWeight = FontWeight.Bold),
        modifier = GlanceModifier.fillMaxWidth()
            .padding(start = 4.dp, top = if (ws.compact) 4.dp else 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun ItemRow(item: Item, p: Palette, ws: WidgetSettings) {
    CheckBox(
        checked = item.inStock,
        onCheckedChange = actionRunCallback<ToggleAction>(actionParametersOf(ItemIdKey to item.id)),
        text = item.name,
        style = TextStyle(
            // Без галочки = нужно купить: выделяем цветом и жирным.
            color = if (item.inStock) p.text else p.needed,
            fontSize = ws.textSize.sp,
            fontWeight = if (item.inStock) FontWeight.Normal else FontWeight.Bold,
        ),
        colors = CheckboxDefaults.colors(checkedColor = p.accent, uncheckedColor = p.needed),
        maxLines = 1,
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
