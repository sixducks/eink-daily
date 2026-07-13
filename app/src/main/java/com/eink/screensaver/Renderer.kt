package com.eink.screensaver

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.eink.screensaver.data.Content
import com.eink.screensaver.net.Http
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 把 Content 绑定到 view_daily 布局上。生成图片和入口页预览共用这一套。 */
object Renderer {

    val grayscale = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })

    /** 纯文字部分，必须在主线程调用。 */
    fun bindText(root: View, c: Content) {
        val now = Date()
        root.findViewById<TextView>(R.id.tvDate).text =
            SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINA).format(now)

        root.findViewById<TextView>(R.id.tvPoem).text = c.poemContent.ifBlank { "—" }
        root.findViewById<TextView>(R.id.tvPoemMeta).text =
            listOf(c.poemTitle, c.poemAuthor).filter { it.isNotBlank() }.joinToString("　·　")

        bindHistory(root.findViewById(R.id.tvHistory), c.history)

        root.findViewById<TextView>(R.id.tvBingCaption).text = c.bingCopyright

        root.findViewById<TextView>(R.id.tvUpdated).text =
            if (c.updatedAt == 0L) ""
            else "更新于 " + SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(c.updatedAt))
    }

    /** 历史条目：圆点 + 悬挂缩进，换行的第二行对齐在正文下、不顶到圆点。 */
    private fun bindHistory(tv: TextView, history: List<String>) {
        if (history.isEmpty()) {
            tv.text = ""
            return
        }
        val bullet = "· "
        val indent = tv.paint.measureText(bullet).toInt()
        val sb = SpannableStringBuilder()
        history.forEachIndexed { i, ev ->
            val start = sb.length
            sb.append(bullet).append(ev)
            sb.setSpan(
                LeadingMarginSpan.Standard(0, indent),
                start, sb.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE
            )
            if (i < history.lastIndex) sb.append("\n\n")
        }
        tv.text = sb
    }

    /** 把已下载的 Bing 位图以灰度设置到 ImageView（主线程调用）。 */
    fun bindBing(iv: ImageView, bmp: Bitmap?) {
        if (bmp == null) return
        iv.colorFilter = grayscale
        iv.setImageBitmap(bmp)
    }

    /** 入口页预览用：异步下载 Bing 图并显示。IO 线程调用。 */
    fun loadBing(iv: ImageView, url: String) {
        if (url.isBlank()) return
        try {
            val bytes = Http.getBytes(url)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
            iv.post { bindBing(iv, bmp) }
        } catch (_: Exception) {
        }
    }
}
