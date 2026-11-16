package com.family.grocery

import android.content.Context
import android.content.SharedPreferences
import androidx.glance.appwidget.updateAll
import com.family.grocery.widget.ListWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File

/**
 * Единое хранилище для приложения и виджета.
 * - serverState: последний список с сервера (кэшируется в файл, виджет работает и без сети);
 * - pending: галочки, которые ещё не отправлены (например, в магазине без связи).
 */
object Repo {
    private lateinit var prefs: SharedPreferences
    private lateinit var cacheFile: File
    private var initialized = false
    private val flushMutex = Mutex()

    private var serverState = ListState.EMPTY
    private val pending = LinkedHashMap<Int, Boolean>()

    val state = MutableStateFlow(ListState.EMPTY)
    val onlyNeeded = MutableStateFlow(false)
    val lastError = MutableStateFlow<String?>(null)
    val widgetSettings = MutableStateFlow(WidgetSettings())

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        prefs = app.getSharedPreferences("grocery", Context.MODE_PRIVATE)
        cacheFile = File(app.filesDir, "state.json")
        serverState = runCatching { ListState.fromJson(JSONObject(cacheFile.readText())) }
            .getOrDefault(ListState.EMPTY)
        runCatching {
            val o = JSONObject(prefs.getString("pending", "{}") ?: "{}")
            o.keys().forEach { k -> pending[k.toInt()] = o.getBoolean(k) }
        }
        onlyNeeded.value = prefs.getBoolean("onlyNeeded", false)
        widgetSettings.value = WidgetSettings.fromJson(prefs.getString("widgetSettings", null))
        initialized = true
        publish()
    }

    // ---------- Настройки ----------

    val isConfigured: Boolean
        get() = !prefs.getString("url", null).isNullOrBlank() && !prefs.getString("key", null).isNullOrBlank()

    val topic: String? get() = prefs.getString("topic", null)
    val firebaseJson: String? get() = prefs.getString("firebase", null)
    val pushEnabled: Boolean get() = firebaseJson != null

    var lastCategoryId: Int?
        get() = prefs.getInt("lastCategory", -1).takeIf { it >= 0 }
        set(v) = prefs.edit().putInt("lastCategory", v ?: -1).apply()

    fun setOnlyNeeded(value: Boolean) {
        onlyNeeded.value = value
        prefs.edit().putBoolean("onlyNeeded", value).apply()
    }

    suspend fun saveWidgetSettings(context: Context, s: WidgetSettings) {
        widgetSettings.value = s
        prefs.edit().putString("widgetSettings", s.toJson()).apply()
        updateWidgets(context)
    }

    private fun api() = Api(prefs.getString("url", "")!!, prefs.getString("key", "")!!)

    /** Первое подключение к серверу: проверяем ключ и забираем настройки push. */
    suspend fun connect(context: Context, rawUrl: String, key: String) {
        var url = rawUrl.trim().trimEnd('/')
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        val cfg = withContext(Dispatchers.IO) { Api(url, key.trim()).request("GET", "/api/config") }
        val firebase = if (cfg.isNull("firebase")) null else cfg.getJSONObject("firebase").toString()
        prefs.edit()
            .putString("url", url)
            .putString("key", key.trim())
            .putString("topic", cfg.optString("topic"))
            .putString("firebase", firebase)
            .apply()
        synchronized(this) { pending.clear(); savePending() }
        refresh(context)
    }

    fun disconnect() {
        val only = onlyNeeded.value
        val ws = widgetSettings.value.toJson()
        prefs.edit().clear().putBoolean("onlyNeeded", only).putString("widgetSettings", ws).apply()
        synchronized(this) { pending.clear() }
        cacheFile.delete()
        serverState = ListState.EMPTY
        publish()
    }

    // ---------- Состояние ----------

    private fun publish() {
        val p = synchronized(this) { HashMap(pending) }
        state.value = if (p.isEmpty()) serverState else serverState.copy(
            items = serverState.items.map { item -> p[item.id]?.let { item.copy(inStock = it) } ?: item }
        )
    }

    private fun setServerState(s: ListState) {
        serverState = s
        runCatching { cacheFile.writeText(s.toJson().toString()) }
        publish()
    }

    private fun savePending() {
        val o = JSONObject()
        pending.forEach { (k, v) -> o.put(k.toString(), v) }
        prefs.edit().putString("pending", o.toString()).apply()
    }

    suspend fun updateWidgets(context: Context) {
        runCatching { ListWidget().updateAll(context.applicationContext) }
    }

    // ---------- Синхронизация ----------

    /** Поставить/снять галочку. Сразу меняется на экране, на сервер уходит как получится. */
    suspend fun setInStock(context: Context, itemId: Int, value: Boolean) {
        synchronized(this) { pending[itemId] = value; savePending() }
        publish()
        updateWidgets(context)
        val ok = withTimeoutOrNull(8_000) { runCatching { flushPending() }.isSuccess } == true
        if (ok) {
            lastError.value = null
            updateWidgets(context)
        } else {
            lastError.value = "Нет связи — отправлю позже"
            Sync.syncSoon(context)
        }
    }

    private suspend fun flushPending() = flushMutex.withLock {
        withContext(Dispatchers.IO) {
            val snapshot = synchronized(this@Repo) { LinkedHashMap(pending) }
            var last: ListState? = null
            for ((id, value) in snapshot) {
                val r = api().request("PATCH", "/api/items/$id", JSONObject().put("inStock", value))
                last = ListState.fromJson(r.getJSONObject("state"))
                synchronized(this@Repo) {
                    if (pending[id] == value) pending.remove(id)
                    savePending()
                }
            }
            last?.let { setServerState(it) }
        }
    }

    /** Отправить неотправленное и скачать свежий список. Бросает исключение, если нет связи. */
    suspend fun refresh(context: Context) {
        if (!isConfigured) return
        try {
            flushPending()
            val s = withContext(Dispatchers.IO) { ListState.fromJson(api().request("GET", "/api/state")) }
            setServerState(s)
            lastError.value = null
        } catch (e: Exception) {
            lastError.value = "Нет связи с сервером"
            throw e
        } finally {
            updateWidgets(context)
        }
    }

    // ---------- Изменения списка (нужна сеть) ----------

    private suspend fun mutate(context: Context, method: String, path: String, body: JSONObject? = null): JSONObject {
        val r = withContext(Dispatchers.IO) { api().request(method, path, body) }
        if (r.has("state")) setServerState(ListState.fromJson(r.getJSONObject("state")))
        updateWidgets(context)
        return r
    }

    suspend fun addCategory(context: Context, name: String): Int =
        mutate(context, "POST", "/api/categories", JSONObject().put("name", name)).getInt("id")

    suspend fun renameCategory(context: Context, id: Int, name: String) {
        mutate(context, "PATCH", "/api/categories/$id", JSONObject().put("name", name))
    }

    suspend fun deleteCategory(context: Context, id: Int) {
        mutate(context, "DELETE", "/api/categories/$id")
    }

    suspend fun addItem(context: Context, name: String, categoryId: Int?, inStock: Boolean) {
        mutate(
            context, "POST", "/api/items",
            JSONObject()
                .put("name", name)
                .put("categoryId", categoryId ?: JSONObject.NULL)
                .put("inStock", inStock)
        )
    }

    suspend fun updateItem(context: Context, id: Int, name: String, categoryId: Int?) {
        mutate(
            context, "PATCH", "/api/items/$id",
            JSONObject().put("name", name).put("categoryId", categoryId ?: JSONObject.NULL)
        )
    }

    suspend fun deleteItem(context: Context, id: Int) {
        synchronized(this) { pending.remove(id); savePending() }
        mutate(context, "DELETE", "/api/items/$id")
    }
}
