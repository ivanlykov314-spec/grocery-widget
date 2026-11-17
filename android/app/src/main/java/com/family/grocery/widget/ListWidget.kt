package com.family.grocery.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.family.grocery.CategoryTab
import com.family.grocery.Diag
import com.family.grocery.Group
import com.family.grocery.Item
import com.family.grocery.ListState
import com.family.grocery.R
import com.family.grocery.Repo
import com.family.grocery.Sync
import com.family.grocery.TAB_ALL
import com.family.grocery.WidgetSettings
import com.family.grocery.ui.AddItemActivity
import com.family.grocery.ui.MainActivity
import com.family.grocery.ui.PickTabActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/*
 * Виджет на классических RemoteViews: рисуется сразу, без фоновых задач WorkManager,
 * поэтому не «зависает» на загрузке даже на Xiaomi/Samsung с жёсткой экономией батареи.
 */

private const val ACTION_TOGGLE = "com.family.grocery.TOGGLE"
private const val ACTION_TAB = "com.family.grocery.TAB"
private const val ACTION_FILTER = "com.family.grocery.FILTER"
private const val ACTION_REFRESH = "com.family.grocery.REFRESH"
private const val ACTION_PAGE = "com.family.grocery.PAGE"
private const val ACTION_NOTE = "com.family.grocery.NOTE"
private const val EXTRA_PAGE = "page"
private const val EXTRA_ITEM = "itemId"
private const val EXTRA_TAB = "tab"

/** Цвета виджета (ARGB) с учётом темы и прозрачности. */
internal class Palette(
    val night: Boolean,
    val bg: Int,
    val bgAlpha: Int,
    val text: Int,
    val muted: Int,
    val accent: Int,
    val needed: Int,
    val chipBg: Int,
    val chipText: Int,
    val onAccent: Int,
)

internal fun palette(ctx: Context, ws: WidgetSettings): Palette {
    val night = when (ws.theme) {
        "light" -> false
        "dark" -> true
        else -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }
    val dyn = Build.VERSION.SDK_INT >= 31
    fun sys(id: Int, fallback: Long): Int = if (dyn) ctx.getColor(id) else fallback.toInt()
    val accent = if (night) sys(android.R.color.system_accent1_200, 0xFFA5D6A7)
    else sys(android.R.color.system_accent1_600, 0xFF2E7D32)
    val needed = when (ws.neededColor) {
        "orange" -> if (night) 0xFFFFB74D.toInt() else 0xFFE65100.toInt()
        "accent" -> accent
        else -> if (night) 0xFFFF8A80.toInt() else 0xFFC62828.toInt()
    }
    return Palette(
        night = night,
        bg = if (night) sys(android.R.color.system_neutral1_900, 0xFF1C1B1F)
        else sys(android.R.color.system_neutral1_50, 0xFFF7F7F2),
        bgAlpha = (ws.opacity.coerceIn(0, 100) * 255) / 100,
        text = if (night) 0xFFE6E1E5.toInt() else 0xFF1B1B1F.toInt(),
        muted = if (night) 0xFFB0ADB3.toInt() else 0xFF5F5E62.toInt(),
        accent = accent,
        needed = needed,
        chipBg = if (night) sys(android.R.color.system_accent2_700, 0xFF3F4A3F)
        else sys(android.R.color.system_accent2_100, 0xFFDDE5DB),
        chipText = if (night) 0xFFE6E1E5.toInt() else 0xFF1B1B1F.toInt(),
        onAccent = if (night) 0xFF1B1B1F.toInt() else 0xFFFFFFFF.toInt(),
    )
}

internal fun bold(text: String): CharSequence =
    SpannableString(text).apply { setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }

/** Какие группы показывать с учётом вкладки и фильтра. Порядок товаров — по алфавиту (без прыжков). */
internal fun visibleGroups(s: ListState, onlyNeeded: Boolean, ws: WidgetSettings, tab: Int): List<Group> {
    fun sort(list: List<Item>) = if (ws.neededFirst) list.sortedBy { it.inStock } else list
    if (tab != TAB_ALL) {
        val list = s.itemsForTab(tab).filter { !onlyNeeded || !it.inStock }.sortedBy { it.name.lowercase() }
        return if (list.isEmpty()) emptyList() else listOf(Group(null, sort(list)))
    }
    return if (ws.showCategories) {
        s.groups(onlyNeeded = onlyNeeded, includeEmpty = false).map { it.copy(items = sort(it.items)) }
    } else {
        val all = s.items.filter { !onlyNeeded || !it.inStock }.sortedBy { it.name.lowercase() }
        if (all.isEmpty()) emptyList() else listOf(Group(null, sort(all)))
    }
}

object WidgetRenderer {

    private fun component(ctx: Context) = ComponentName(ctx, ListWidgetReceiver::class.java)

    /** Следующая отрисовка будет полной (система попросила обновить виджет — например, после перезапуска лаунчера). */
    fun forceFull() = fullyRendered.clear()

    fun renderAll(context: Context) {
        val ctx = context.applicationContext
        Repo.init(ctx)
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = mgr.getAppWidgetIds(component(ctx))
        if (ids.isEmpty()) return
        ids.forEach { id -> render(ctx, mgr, id, full = id !in fullyRendered) }
        @Suppress("DEPRECATION")
        mgr.notifyAppWidgetViewDataChanged(ids, R.id.list)
    }

    /** Виджеты, которые в этом процессе уже получили полное обновление (со списком). */
    private val fullyRendered = java.util.Collections.synchronizedSet(mutableSetOf<Int>())

    /**
     * Полное обновление пересоздаёт список (он на миг мелькает «загрузкой»), поэтому делаем его
     * один раз, а дальше — частичное: только заголовок, вкладки и цвета; список обновляет notify.
     * Поэтому в макете нельзя использовать addView — только заготовленные элементы.
     */
    fun render(ctx: Context, mgr: AppWidgetManager, appWidgetId: Int, full: Boolean = true) {
        try {
            val needFull = full
            val rv = build(ctx, mgr, appWidgetId, withList = needFull)
            if (needFull) {
                mgr.updateAppWidget(appWidgetId, rv)
                fullyRendered += appWidgetId
            } else {
                mgr.partiallyUpdateAppWidget(appWidgetId, rv)
            }
        } catch (e: Exception) {
            Diag.record(ctx, "widget render", e)
        }
    }

    private fun broadcast(ctx: Context, action: String, requestCode: Int, extras: Intent.() -> Unit = {}): PendingIntent {
        val intent = Intent(ctx, ListWidgetReceiver::class.java).setAction(action).apply(extras)
        return PendingIntent.getBroadcast(
            ctx, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun activity(ctx: Context, cls: Class<*>, requestCode: Int): PendingIntent {
        val intent = Intent(ctx, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            ctx, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun build(ctx: Context, mgr: AppWidgetManager, appWidgetId: Int, withList: Boolean): RemoteViews {
        val s = Repo.state.value
        val ws = Repo.widgetSettings.value
        // Купить нечего — режим «только нужное» выключается сам (кнопка «Купить» скрыта).
        if (Repo.onlyNeeded.value && s.neededCount == 0) Repo.setOnlyNeeded(false)
        val only = Repo.onlyNeeded.value
        val configured = Repo.isConfigured
        val tab = s.validTab(Repo.selectedTab.value)
        val p = palette(ctx, ws)
        val rv = RemoteViews(ctx.packageName, R.layout.widget_root)

        // Фон и прозрачность
        rv.setInt(R.id.bg, "setColorFilter", p.bg)
        rv.setInt(R.id.bg, "setImageAlpha", p.bgAlpha)
        val pad = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, if (ws.compact) 6f else 10f, ctx.resources.displayMetrics,
        ).toInt()
        rv.setViewPadding(R.id.content, pad, pad / 2 + 2, pad, pad / 2 + 2)

        // Заголовок
        rv.setViewVisibility(R.id.title, if (ws.showTitle) View.VISIBLE else View.INVISIBLE)
        rv.setTextViewText(R.id.title, "Покупки")
        rv.setTextColor(R.id.title, p.text)
        rv.setTextViewTextSize(R.id.title, TypedValue.COMPLEX_UNIT_SP, (ws.textSize + 2).toFloat())
        rv.setOnClickPendingIntent(R.id.title, activity(ctx, MainActivity::class.java, 10))

        // Кнопка «Купить» — только когда есть что купить; нажата = показываем только нужное.
        if (configured && s.neededCount > 0) {
            rv.setViewVisibility(R.id.chip, View.VISIBLE)
            showChip(rv, intArrayOf(R.id.chip_o, R.id.chip_l, R.id.chip_d), if (only) bold("Купить") else "Купить", only, p)
            rv.setOnClickPendingIntent(R.id.chip, broadcast(ctx, ACTION_FILTER, 20))
        } else {
            rv.setViewVisibility(R.id.chip, View.GONE)
        }

        rv.setInt(R.id.btn_refresh, "setColorFilter", p.text)
        rv.setInt(R.id.btn_add, "setColorFilter", p.text)
        rv.setOnClickPendingIntent(R.id.btn_refresh, broadcast(ctx, ACTION_REFRESH, 21))
        rv.setOnClickPendingIntent(R.id.btn_add, activity(ctx, AddItemActivity::class.java, 11))

        // Строка ошибки (например, «Нет связи — отправлю позже»)
        val error = Repo.lastError.value
        if (error != null && configured) {
            rv.setViewVisibility(R.id.error, View.VISIBLE)
            rv.setTextViewText(R.id.error, error)
            rv.setTextColor(R.id.error, p.needed)
        } else {
            rv.setViewVisibility(R.id.error, View.GONE)
        }

        // Вкладки категорий: «все сразу» (чипы, как в старой версии) или ‹ одна строка ›
        val showTabs = configured && ws.tabs && s.tabs().isNotEmpty()
        val chips = ws.tabStyle == "chips"
        if (showTabs && chips) {
            val widthDp = mgr.getAppWidgetOptions(appWidgetId)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250).takeIf { it > 0 } ?: 250
            // ширина строки чипов: минус отступы виджета и две стрелки по 28dp
            fillTabChips(ctx, rv, s.tabs(), tab, p, ws, (widthDp - (if (ws.compact) 12 else 20) - 48 - 2).toFloat())
            rv.setViewVisibility(R.id.tabs, View.VISIBLE)
        } else {
            rv.setViewVisibility(R.id.tabs, View.GONE)
        }
        if (showTabs && !chips) {
            fillTabSwitcher(ctx, rv, s.tabs(), tab, p, ws)
            rv.setViewVisibility(R.id.tab_switch, View.VISIBLE)
        } else {
            rv.setViewVisibility(R.id.tab_switch, View.GONE)
        }

        // Список товаров
        // Строки списка отдаёт сервис (ListWidgetService). НЕ встраивать их в само обновление:
        // при сотнях товаров посылка превышает лимит Binder, система считает лаунчер «мёртвым»
        // и перестаёт доставлять ему ВСЕ обновления виджета до перезапуска лаунчера/телефона.
        if (withList) {
            val svc = Intent(ctx, ListWidgetService::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            svc.data = Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME))
            @Suppress("DEPRECATION")
            rv.setRemoteAdapter(R.id.list, svc)
            rv.setEmptyView(R.id.list, R.id.empty)
            val template = PendingIntent.getBroadcast(
                // Без action: его подставляет fill-in строки (галочка — TOGGLE, название — NOTE).
                ctx, 31, Intent(ctx, ListWidgetReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            rv.setPendingIntentTemplate(R.id.list, template)
        }

        val emptyText = when {
            !configured -> "Нажмите, чтобы подключить список"
            only -> "Всё есть дома 👍"
            else -> "Список пуст — нажмите +"
        }
        rv.setTextViewText(R.id.empty, emptyText)
        rv.setTextColor(R.id.empty, p.muted)
        rv.setTextViewTextSize(R.id.empty, TypedValue.COMPLEX_UNIT_SP, ws.textSize.toFloat())
        rv.setOnClickPendingIntent(R.id.empty, activity(ctx, MainActivity::class.java, 12))
        return rv
    }

    /** Компактные вкладки: ‹ Категория ›, нажатие на название — список всех категорий. Элементы прямо в widget_root. */
    private fun fillTabSwitcher(
        ctx: Context, rv: RemoteViews, tabs: List<CategoryTab>, selected: Int, p: Palette, ws: WidgetSettings,
    ) {
        val idx = tabs.indexOfFirst { it.id == selected }.coerceAtLeast(0)
        val cur = tabs[idx]
        val prev = tabs[(idx - 1 + tabs.size) % tabs.size]
        val next = tabs[(idx + 1) % tabs.size]
        val label = if (cur.needed > 0) "${cur.name} · купить ${cur.needed}" else cur.name
        rv.setTextViewText(R.id.tab_current, label)
        rv.setTextColor(R.id.tab_current, p.onAccent)
        rv.setTextViewTextSize(R.id.tab_current, TypedValue.COMPLEX_UNIT_SP, (if (ws.compact) 14 else 15).toFloat())
        rv.setInt(R.id.tab_current_bg, "setColorFilter", p.accent)
        rv.setInt(R.id.tab_prev, "setColorFilter", p.text)
        rv.setInt(R.id.tab_next, "setColorFilter", p.text)
        rv.setOnClickPendingIntent(R.id.tab_prev, broadcast(ctx, ACTION_TAB, 40) { putExtra(EXTRA_TAB, prev.id) })
        rv.setOnClickPendingIntent(R.id.tab_next, broadcast(ctx, ACTION_TAB, 41) { putExtra(EXTRA_TAB, next.id) })
        rv.setOnClickPendingIntent(R.id.tab_current_root, activity(ctx, PickTabActivity::class.java, 42))
    }

    /**
     * Чип = три TextView с фоном из макета: рамка (o), заливка светлой (l) и тёмной (d) темы.
     * Показываем один. (Фон-картинка под текстом на некоторых лаунчерах не рисуется.)
     */
    private fun showChip(rv: RemoteViews, variants: IntArray, text: CharSequence, selected: Boolean, p: Palette) {
        val visible = when {
            !selected -> 0
            p.night -> 2
            else -> 1
        }
        variants.forEachIndexed { i, id ->
            if (i == visible) {
                rv.setViewVisibility(id, View.VISIBLE)
                rv.setTextViewText(id, text)
                rv.setTextColor(id, if (selected) p.onAccent else p.chipText)
            } else {
                rv.setViewVisibility(id, View.GONE)
            }
        }
    }

    /** Чипы-вкладки в одну строку, по страницам: ‹ › листают страницы, нажатие на чип выбирает категорию. */
    private fun fillTabChips(
        ctx: Context, rv: RemoteViews, tabs: List<CategoryTab>, selected: Int,
        p: Palette, ws: WidgetSettings, available: Float,
    ) {
        val fontSp = if (ws.compact) 12f else 13f
        fun label(t: CategoryTab) = if (t.needed > 0) "${t.name} ${t.needed}" else t.name
        // Точная ширина текста (жирным — с запасом) + отступы чипа 8+8 dp + промежуток 4 dp.
        val dm = ctx.resources.displayMetrics
        val paint = android.graphics.Paint().apply {
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, fontSp, dm)
            typeface = Typeface.DEFAULT_BOLD
        }
        val pages = mutableListOf<MutableList<Pair<Int, CategoryTab>>>()
        var used = 0f
        tabs.forEachIndexed { index, t ->
            val w = paint.measureText(label(t)) / dm.density + 16f + 4f + 2f
            if (pages.isEmpty() || used + w > available || pages.last().size >= TAB_CHIP_IDS.size) {
                pages += mutableListOf<Pair<Int, CategoryTab>>()
                used = 0f
            }
            used += w
            pages.last() += index to t
        }
        val selPage = pages.indexOfFirst { pg -> pg.any { it.second.id == selected } }.coerceAtLeast(0)
        val page = (if (Repo.tabPage < 0) selPage else Repo.tabPage).coerceIn(0, pages.lastIndex)
        val cur = pages[page]
        for (c in TAB_CHIP_IDS.indices) {
            val slot = TAB_CHIP_IDS[c]
            val entry = cur.getOrNull(c)
            if (entry == null) {
                rv.setViewVisibility(slot, View.GONE)
                continue
            }
            val (index, t) = entry
            val isSel = t.id == selected
            rv.setViewVisibility(slot, View.VISIBLE)
            showChip(rv, TAB_CHIP_VARIANTS[c], if (isSel) bold(label(t)) else label(t), isSel, p)
            TAB_CHIP_VARIANTS[c].forEach { rv.setTextViewTextSize(it, TypedValue.COMPLEX_UNIT_SP, fontSp) }
            rv.setOnClickPendingIntent(slot, broadcast(ctx, ACTION_TAB, 1000 + index) { putExtra(EXTRA_TAB, t.id) })
        }
        // Стрелки: невидимые (а не скрытые), чтобы строка не прыгала.
        rv.setViewVisibility(R.id.page_prev, if (page > 0) View.VISIBLE else View.INVISIBLE)
        rv.setViewVisibility(R.id.page_next, if (page < pages.lastIndex) View.VISIBLE else View.INVISIBLE)
        rv.setInt(R.id.page_prev, "setColorFilter", p.text)
        rv.setInt(R.id.page_next, "setColorFilter", p.text)
        rv.setOnClickPendingIntent(R.id.page_prev, broadcast(ctx, ACTION_PAGE, 50) { putExtra(EXTRA_PAGE, page - 1) })
        rv.setOnClickPendingIntent(R.id.page_next, broadcast(ctx, ACTION_PAGE, 51) { putExtra(EXTRA_PAGE, page + 1) })
    }
}

class ListWidgetReceiver : AppWidgetProvider() {

    private companion object {
        val lastWidth = java.util.concurrent.ConcurrentHashMap<Int, Int>()
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetRenderer.forceFull()
        WidgetRenderer.renderAll(context)
        Sync.schedulePeriodic(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        Repo.init(context)
        // Ширина важна только для раскладки чипов-вкладок. Перерисовываем лишь при реальном изменении
        // ширины: лаунчер может слать это событие часто, а частые обновления ломают связь с ним.
        val ws = Repo.widgetSettings.value
        val w = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        if (ws.tabs && ws.tabStyle == "chips" && lastWidth.put(appWidgetId, w) != w) {
            WidgetRenderer.render(context, mgr, appWidgetId, full = false)
        }
    }

    override fun onEnabled(context: Context) {
        Sync.schedulePeriodic(context)
        Sync.syncSoon(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val ctx = context.applicationContext
        Diag.event(ctx, "получено: ${intent.action?.substringAfterLast('.')} item=${intent.getIntExtra(EXTRA_ITEM, -1)} tab=${intent.getIntExtra(EXTRA_TAB, -99)}")
        Repo.init(ctx)
        when (intent.action) {
            ACTION_TOGGLE -> {
                val id = intent.getIntExtra(EXTRA_ITEM, -1)
                if (id < 0) return
                val current = Repo.state.value.items.firstOrNull { it.id == id } ?: return
                runAsync(ctx, "toggle") { Repo.setInStock(ctx, id, !current.inStock) }
            }
            ACTION_TAB -> {
                Repo.setSelectedTab(intent.getIntExtra(EXTRA_TAB, TAB_ALL), keepPage = true)
                WidgetRenderer.renderAll(ctx)
            }
            ACTION_NOTE -> {
                val id = intent.getIntExtra(EXTRA_ITEM, -1)
                if (id < 0) return
                try {
                    ctx.startActivity(
                        Intent(ctx, com.family.grocery.ui.NoteActivity::class.java)
                            .putExtra(com.family.grocery.ui.NoteActivity.EXTRA_ITEM_ID, id)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                    )
                } catch (e: Exception) {
                    Diag.record(ctx, "widget note", e)
                }
            }
            ACTION_PAGE -> {
                Repo.tabPage = intent.getIntExtra(EXTRA_PAGE, 0).coerceAtLeast(0)
                WidgetRenderer.renderAll(ctx)
            }
            ACTION_FILTER -> {
                val on = !Repo.onlyNeeded.value
                Repo.setOnlyNeeded(on)
                // «Купить» = всё, что нужно купить, из всех категорий — сразу, без перехода на «Все».
                if (on) Repo.setSelectedTab(TAB_ALL)
                WidgetRenderer.renderAll(ctx)
            }
            ACTION_REFRESH -> runAsync(ctx, "refresh") {
                val ok = withTimeoutOrNull(9_000) { runCatching { Repo.refresh(ctx) }.isSuccess } == true
                if (!ok) Sync.syncSoon(ctx)
            }
        }
    }

    private fun runAsync(ctx: Context, what: String, block: suspend () -> Unit) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                block()
            } catch (e: Exception) {
                Diag.record(ctx, "widget $what", e)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Строки списка виджета: заголовки категорий и строки с 1–2 товарами. Общие для обоих способов показа списка. */
internal object WidgetRows {
    class Row(val header: String?, val items: List<Item>)
    class Result(val rows: List<Row>, val ws: WidgetSettings, val pal: Palette)

    fun build(ctx: Context): Result {
        Repo.init(ctx)
        val ws = Repo.widgetSettings.value
        val pal = palette(ctx, ws)
        return try {
            val s = Repo.state.value
            if (!Repo.isConfigured) return Result(emptyList(), ws, pal)
            val tab = s.validTab(Repo.selectedTab.value)
            val effectiveTab = if (ws.tabs) tab else TAB_ALL
            val groups = visibleGroups(s, Repo.onlyNeeded.value, ws, effectiveTab)
            val showHeaders = ws.showCategories && effectiveTab == TAB_ALL
            val result = mutableListOf<Row>()
            for (g in groups) {
                if (showHeaders) result += Row(g.category?.name ?: "Без категории", emptyList())
                g.items.chunked(if (ws.columns == 2) 2 else 1).forEach { result += Row(null, it) }
            }
            Result(result, ws, pal)
        } catch (e: Exception) {
            Diag.record(ctx, "widget list", e)
            Result(emptyList(), ws, pal)
        }
    }

    fun view(ctx: Context, row: Row?, ws: WidgetSettings, pal: Palette): RemoteViews {
        if (row == null) return RemoteViews(ctx.packageName, R.layout.widget_header)
        if (row.header != null) {
            val rv = RemoteViews(ctx.packageName, R.layout.widget_header)
            rv.setTextViewText(R.id.header_text, row.header.uppercase())
            rv.setTextColor(R.id.header_text, pal.accent)
            rv.setTextViewTextSize(R.id.header_text, TypedValue.COMPLEX_UNIT_SP, (ws.textSize - 4).coerceAtLeast(10).toFloat())
            return rv
        }
        val two = ws.columns == 2
        val rv = RemoteViews(ctx.packageName, if (two) R.layout.widget_item2 else R.layout.widget_item1)
        fillCell(ctx, rv, row.items.getOrNull(0), R.id.cell1, R.id.box1, R.id.name1, ws, pal)
        if (two) fillCell(ctx, rv, row.items.getOrNull(1), R.id.cell2, R.id.box2, R.id.name2, ws, pal)
        return rv
    }

    /** Название (жирное, если нужно купить) и мелкая пометка с комментарием: «Яйца  ✎ 10 штук». */
    private fun itemLabel(item: Item, pal: Palette): CharSequence {
        val sb = android.text.SpannableStringBuilder(item.name)
        if (!item.inStock) sb.setSpan(StyleSpan(Typeface.BOLD), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (item.note.isNotBlank()) {
            val start = sb.length
            // Длинное название переносится на следующую строку, а комментарий идёт следом
            // (✎ не отрывается от текста комментария), поэтому он больше не прячется за «…».
            sb.append("  ✎ ").append(item.note)
            sb.setSpan(android.text.style.RelativeSizeSpan(0.8f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(android.text.style.ForegroundColorSpan(pal.muted), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sb
    }

    private fun fillCell(ctx: Context, rv: RemoteViews, item: Item?, cell: Int, box: Int, name: Int, ws: WidgetSettings, pal: Palette) {
        if (item == null) {
            rv.setViewVisibility(cell, View.INVISIBLE)
            return
        }
        rv.setViewVisibility(cell, View.VISIBLE)
        rv.setImageViewResource(box, if (item.inStock) R.drawable.ic_box_checked else R.drawable.ic_box_unchecked)
        rv.setInt(box, "setColorFilter", if (item.inStock) pal.accent else pal.needed)
        // Без галочки = нужно купить: цветом и жирным, но на том же месте в списке.
        rv.setTextViewText(name, itemLabel(item, pal))
        rv.setTextColor(name, if (item.inStock) pal.text else pal.needed)
        rv.setTextViewTextSize(name, TypedValue.COMPLEX_UNIT_SP, ws.textSize.toFloat())
        val pad = if (ws.compact) 3 else 6
        val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, pad.toFloat(), ctx.resources.displayMetrics).toInt()
        rv.setViewPadding(cell, px / 2, px, px, px)
        // Галочка — поставить/снять; название — окно с комментарием.
        rv.setOnClickFillInIntent(box, Intent().setAction(ACTION_TOGGLE).putExtra(EXTRA_ITEM, item.id))
        rv.setOnClickFillInIntent(name, Intent().setAction(ACTION_NOTE).putExtra(EXTRA_ITEM, item.id))
    }
}

/** Отдаёт строки списка виджету. */
class ListWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    private class Factory(private val ctx: Context) : RemoteViewsFactory {
        private var data: WidgetRows.Result? = null

        override fun onCreate() {}
        override fun onDataSetChanged() {
            data = WidgetRows.build(ctx)
        }
        override fun onDestroy() {}
        override fun getCount(): Int = data?.rows?.size ?: 0
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount(): Int = 3
        override fun getItemId(position: Int): Long = position.toLong()
        override fun hasStableIds(): Boolean = false
        override fun getViewAt(position: Int): RemoteViews {
            val d = data ?: WidgetRows.build(ctx).also { data = it }
            return WidgetRows.view(ctx, d.rows.getOrNull(position), d.ws, d.pal)
        }
    }
}
