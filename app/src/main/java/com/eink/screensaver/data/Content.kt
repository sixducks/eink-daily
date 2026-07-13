package com.eink.screensaver.data

import org.json.JSONArray
import org.json.JSONObject

/** 一屏要显示的全部内容，可序列化成 JSON 存本地做缓存。 */
data class Content(
    val poemContent: String = "",
    val poemTitle: String = "",
    val poemAuthor: String = "",
    val history: List<String> = emptyList(),
    val bingCopyright: String = "",
    val bingImageUrl: String = "",
    val updatedAt: Long = 0L
) {
    fun toJson(): String = JSONObject().apply {
        put("poemContent", poemContent)
        put("poemTitle", poemTitle)
        put("poemAuthor", poemAuthor)
        put("history", JSONArray(history))
        put("bingCopyright", bingCopyright)
        put("bingImageUrl", bingImageUrl)
        put("updatedAt", updatedAt)
    }.toString()

    companion object {
        fun fromJson(s: String?): Content? {
            if (s.isNullOrBlank()) return null
            return try {
                val o = JSONObject(s)
                val arr = o.optJSONArray("history") ?: JSONArray()
                val list = ArrayList<String>()
                for (i in 0 until arr.length()) list.add(arr.getString(i))
                Content(
                    poemContent = o.optString("poemContent"),
                    poemTitle = o.optString("poemTitle"),
                    poemAuthor = o.optString("poemAuthor"),
                    history = list,
                    bingCopyright = o.optString("bingCopyright"),
                    bingImageUrl = o.optString("bingImageUrl"),
                    updatedAt = o.optLong("updatedAt")
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
