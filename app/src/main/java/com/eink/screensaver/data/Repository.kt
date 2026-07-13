package com.eink.screensaver.data

import android.content.Context
import com.eink.screensaver.net.Http
import org.json.JSONObject
import java.util.Calendar

/**
 * 负责拉取 3 个数据源（诗词 / 历史今天 / Bing 壁纸）并做缓存。
 * 关键原则：任何单个接口失败，都退回上一次缓存的对应字段——绝不让画面白屏。
 */
class Repository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("eink_daily", Context.MODE_PRIVATE)

    fun cached(): Content? = Content.fromJson(prefs.getString("content", null))

    /** 全量刷新（阻塞，务必在 IO 线程调用）。 */
    fun refresh(): Content {
        val old = cached() ?: Content()

        val poem = runCatching { fetchPoem() }.getOrNull()
        val hist = runCatching { fetchHistory() }.getOrNull()
        val bing = runCatching { fetchBing() }.getOrNull()

        val merged = Content(
            poemContent = poem?.content ?: old.poemContent,
            poemTitle = poem?.title ?: old.poemTitle,
            poemAuthor = poem?.author ?: old.poemAuthor,
            history = hist ?: old.history,
            bingCopyright = bing?.first ?: old.bingCopyright,
            bingImageUrl = bing?.second ?: old.bingImageUrl,
            updatedAt = System.currentTimeMillis()
        )
        prefs.edit().putString("content", merged.toJson()).apply()
        return merged
    }

    // ---------- 今日诗词 ----------
    private data class Poem(val content: String, val title: String, val author: String)

    /** token 申请一次后永久复用。 */
    private fun jinrishiciToken(): String {
        prefs.getString("jrsc_token", null)?.let { if (it.isNotBlank()) return it }
        val res = Http.getString("https://v2.jinrishici.com/token")
        val token = JSONObject(res).optString("data")
        if (token.isNotBlank()) prefs.edit().putString("jrsc_token", token).apply()
        return token
    }

    private fun fetchPoem(): Poem {
        val token = runCatching { jinrishiciToken() }.getOrDefault("")
        val headers = if (token.isNotBlank()) mapOf("X-User-Token" to token) else emptyMap()
        val res = Http.getString("https://v2.jinrishici.com/one.json", headers)
        val data = JSONObject(res).getJSONObject("data")
        val featured = data.optString("content")
        val origin = data.optJSONObject("origin")
        // 全诗：origin.content 是逐句数组，每句一行；缺失时退回精选联
        val lines = origin?.optJSONArray("content")
        val fullPoem = if (lines != null && lines.length() > 0)
            (0 until lines.length()).joinToString("\n") { lines.getString(it) }
        else featured
        val title = origin?.optString("title") ?: ""
        val dynasty = origin?.optString("dynasty") ?: ""
        val author = origin?.optString("author") ?: ""
        val meta = listOf(dynasty, author).filter { it.isNotBlank() }.joinToString("·")
        return Poem(fullPoem, title, meta)
    }

    // ---------- 历史上的今天（可切换来源：维基 / 百度） ----------
    fun getHistorySource(): String = prefs.getString(KEY_SOURCE, SOURCE_BAIDU) ?: SOURCE_BAIDU

    fun setHistorySource(source: String) {
        prefs.edit().putString(KEY_SOURCE, source).apply()
    }

    private fun fetchHistory(): List<String> {
        val cal = Calendar.getInstance()
        val mm = String.format("%02d", cal.get(Calendar.MONTH) + 1)
        val dd = String.format("%02d", cal.get(Calendar.DAY_OF_MONTH))
        return if (getHistorySource() == SOURCE_WIKI) fetchHistoryWiki(mm, dd)
        else fetchHistoryBaidu(mm, dd)
    }

    /** 维基百科精选（境外源，国内需梯子）。Accept-Language: zh-Hans 转简体且更完整。 */
    private fun fetchHistoryWiki(mm: String, dd: String): List<String> {
        val url = "https://zh.wikipedia.org/api/rest_v1/feed/onthisday/selected/$mm/$dd"
        val res = Http.getString(url, mapOf("Accept-Language" to "zh-Hans"))
        val arr = JSONObject(res).optJSONArray("selected") ?: return emptyList()
        val items = ArrayList<Pair<Int, String>>()
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            val text = e.optString("text")
            if (text.isNotBlank()) items.add(e.optInt("year") to truncate(text))
        }
        return items.sortedByDescending { it.first }
            .take(HISTORY_COUNT)
            .map { "${it.first}　${it.second}" }
    }

    /** 百度百科历史上的今天（国内可直连）。只取“事件”类，剔除出生/逝世，标题去 HTML 标签。 */
    private fun fetchHistoryBaidu(mm: String, dd: String): List<String> {
        val url = "https://baike.baidu.com/cms/home/eventsOnHistory/$mm.json"
        val res = Http.getString(url, mapOf("User-Agent" to "Mozilla/5.0"))
        val day = JSONObject(res).optJSONObject(mm)?.optJSONArray(mm + dd) ?: return emptyList()
        val items = ArrayList<Pair<Int, String>>()
        for (i in 0 until day.length()) {
            val e = day.getJSONObject(i)
            if (e.optString("type") != "event") continue
            val year = e.optString("year").toIntOrNull() ?: continue
            val title = stripHtml(e.optString("title"))
            if (title.isNotBlank()) items.add(year to truncate(title))
        }
        return items.sortedByDescending { it.first }
            .take(HISTORY_COUNT)
            .map { "${it.first}　${it.second}" }
    }

    private fun stripHtml(s: String): String =
        s.replace(Regex("<[^>]*>"), "").replace("&nbsp;", " ").trim()

    /** 事件太长会撑破静态图排版，超过 MAX_EVENT_LEN 截断。 */
    private fun truncate(s: String): String =
        if (s.length <= MAX_EVENT_LEN) s else s.substring(0, MAX_EVENT_LEN) + "…"

    // ---------- Bing 每日壁纸 ----------
    private fun fetchBing(): Pair<String, String> {
        val res = Http.getString("https://cn.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1")
        val first = JSONObject(res).getJSONArray("images").getJSONObject(0)
        val url = "https://cn.bing.com" + first.optString("url")
        val copyright = first.optString("copyright")
        return Pair(copyright, url)
    }

    companion object {
        // 历史来源可选值；默认百度（国内可直连）
        const val SOURCE_WIKI = "wiki"
        const val SOURCE_BAIDU = "baidu"
        private const val KEY_SOURCE = "history_source"

        // 历史上的今天显示条数（真实事件每条约 30~40 字、在屏上换成 2 行）
        const val HISTORY_COUNT = 3

        // 单条事件最大字数（安全阀，真实数据一般不触发）
        const val MAX_EVENT_LEN = 60
    }
}
