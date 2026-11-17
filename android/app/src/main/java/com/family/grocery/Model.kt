package com.family.grocery

import org.json.JSONArray
import org.json.JSONObject

data class Category(val id: Int, val name: String)

/** inStock = true — галочка стоит (товар есть дома). false — нужно купить. */
data class Item(val id: Int, val categoryId: Int?, val name: String, val inStock: Boolean, val note: String = "")

data class Group(val category: Category?, val items: List<Item>)

/** Вкладка категории: id = категория, TAB_ALL = «Все», TAB_NONE = «Без категории». */
data class CategoryTab(val id: Int, val name: String, val needed: Int)

const val TAB_ALL = -2
const val TAB_NONE = -1

data class ListState(val categories: List<Category>, val items: List<Item>) {

    val neededCount: Int get() = items.count { !it.inStock }

    /** Товары, сгруппированные по категориям (в порядке создания категорий). */
    fun groups(onlyNeeded: Boolean, includeEmpty: Boolean): List<Group> {
        val visible = if (onlyNeeded) items.filter { !it.inStock } else items
        val byCat = visible.groupBy { it.categoryId }
        val knownIds = categories.map { it.id }.toSet()
        val result = mutableListOf<Group>()
        for (c in categories) {
            val list = byCat[c.id].orEmpty()
            if (list.isNotEmpty() || includeEmpty) result += Group(c, list)
        }
        val orphans = visible.filter { it.categoryId == null || it.categoryId !in knownIds }
        if (orphans.isNotEmpty()) result += Group(null, orphans)
        return result
    }

    fun tabs(): List<CategoryTab> {
        val knownIds = categories.map { it.id }.toSet()
        val result = mutableListOf(CategoryTab(TAB_ALL, "Все", neededCount))
        categories.forEach { c ->
            result += CategoryTab(c.id, c.name, items.count { it.categoryId == c.id && !it.inStock })
        }
        val orphans = items.filter { it.categoryId == null || it.categoryId !in knownIds }
        if (orphans.isNotEmpty()) result += CategoryTab(TAB_NONE, "Без категории", orphans.count { !it.inStock })
        return result
    }

    fun itemsForTab(tab: Int): List<Item> {
        val knownIds = categories.map { it.id }.toSet()
        return when (tab) {
            TAB_ALL -> items
            TAB_NONE -> items.filter { it.categoryId == null || it.categoryId !in knownIds }
            else -> items.filter { it.categoryId == tab }
        }
    }

    /** Выбранная вкладка, если она ещё существует; иначе «Все». */
    fun validTab(tab: Int): Int = if (tabs().any { it.id == tab }) tab else TAB_ALL

    fun toJson(): JSONObject {
        val cats = JSONArray()
        categories.forEach { cats.put(JSONObject().put("id", it.id).put("name", it.name)) }
        val its = JSONArray()
        items.forEach {
            its.put(
                JSONObject()
                    .put("id", it.id)
                    .put("categoryId", it.categoryId ?: JSONObject.NULL)
                    .put("name", it.name)
                    .put("inStock", it.inStock)
                    .put("note", it.note)
            )
        }
        return JSONObject().put("categories", cats).put("items", its)
    }

    companion object {
        val EMPTY = ListState(emptyList(), emptyList())

        fun fromJson(o: JSONObject): ListState {
            val cats = o.optJSONArray("categories") ?: JSONArray()
            val its = o.optJSONArray("items") ?: JSONArray()
            return ListState(
                categories = (0 until cats.length()).map {
                    val c = cats.getJSONObject(it)
                    Category(c.getInt("id"), c.getString("name"))
                },
                items = (0 until its.length()).map {
                    val i = its.getJSONObject(it)
                    Item(
                        id = i.getInt("id"),
                        categoryId = if (i.isNull("categoryId")) null else i.getInt("categoryId"),
                        name = i.getString("name"),
                        inStock = i.getBoolean("inStock"),
                        note = i.optString("note", ""),
                    )
                },
            )
        }
    }
}

/** Настройки внешнего вида виджета (меняются в приложении: ⋮ → Настройки виджета). */
data class WidgetSettings(
    val opacity: Int = 85,           // непрозрачность фона, 0..100 %
    val theme: String = "system",    // system | light | dark
    val textSize: Int = 16,          // sp
    val columns: Int = 1,            // 1 или 2 колонки товаров
    val showTitle: Boolean = true,   // заголовок «Покупки» и счётчик
    val showCategories: Boolean = true,
    val neededFirst: Boolean = false, // внутри категории сначала то, что нужно купить (товары «прыгают»)
    val compact: Boolean = false,    // меньше отступы
    val neededColor: String = "red", // red | orange | accent
    val tabs: Boolean = true,        // категории вкладками
    val tabStyle: String = "chips",  // chips (все вкладки сразу, как в старой версии) | arrows (‹ Категория ›)
) {
    fun toJson(): String = JSONObject()
        .put("opacity", opacity).put("theme", theme).put("textSize", textSize)
        .put("columns", columns).put("showTitle", showTitle).put("showCategories", showCategories)
        .put("sortNeeded", neededFirst).put("compact", compact).put("neededColor", neededColor).put("tabs", tabs).put("tabsView", tabStyle)
        .toString()

    companion object {
        fun fromJson(s: String?): WidgetSettings {
            if (s.isNullOrBlank()) return WidgetSettings()
            return runCatching {
                val o = JSONObject(s)
                val d = WidgetSettings()
                WidgetSettings(
                    opacity = o.optInt("opacity", d.opacity).coerceIn(0, 100),
                    theme = o.optString("theme", d.theme),
                    textSize = o.optInt("textSize", d.textSize).coerceIn(10, 28),
                    columns = o.optInt("columns", d.columns).coerceIn(1, 2),
                    showTitle = o.optBoolean("showTitle", d.showTitle),
                    showCategories = o.optBoolean("showCategories", d.showCategories),
                    neededFirst = o.optBoolean("sortNeeded", d.neededFirst),
                    compact = o.optBoolean("compact", d.compact),
                    neededColor = o.optString("neededColor", d.neededColor),
                    tabs = o.optBoolean("tabs", d.tabs),
                    tabStyle = o.optString("tabsView", d.tabStyle),
                )
            }.getOrDefault(WidgetSettings())
        }
    }
}
