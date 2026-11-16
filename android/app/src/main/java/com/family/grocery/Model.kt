package com.family.grocery

import org.json.JSONArray
import org.json.JSONObject

data class Category(val id: Int, val name: String)

/** inStock = true — галочка стоит (товар есть дома). false — нужно купить. */
data class Item(val id: Int, val categoryId: Int?, val name: String, val inStock: Boolean)

data class Group(val category: Category?, val items: List<Item>)

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
                    )
                },
            )
        }
    }
}
