package com.eink.screensaver

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import android.util.TypedValue
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

        bindPoem(root.findViewById(R.id.tvPoem), c.poemContent)
        root.findViewById<TextView>(R.id.tvPoemMeta).text =
            listOf(c.poemTitle, c.poemAuthor).filter { it.isNotBlank() }.joinToString("　·　")

        bindHistory(root.findViewById(R.id.tvHistory), c.history)

        root.findViewById<TextView>(R.id.tvBingCaption).text = c.bingCopyright

        root.findViewById<TextView>(R.id.tvUpdated).text =
            if (c.updatedAt == 0L) ""
            else "更新于 " + SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(c.updatedAt))
    }

    /** 诗词：按字数自动缩放字号——短诗大而醒目，长词自动缩小，保证放得下不顶掉历史。 */
    private fun bindPoem(tv: TextView, poem: String) {
        val text = poem.ifBlank { "—" }
        tv.text = text
        val len = text.replace("\n", "").length
        val sizeSp = when {
            len <= 16 -> 38f   // 短句/五绝一联
            len <= 28 -> 34f   // 七绝
            len <= 44 -> 29f
            len <= 64 -> 25f   // 七律级
            len <= 90 -> 21f
            else -> 18f        // 超长慢词
        }
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
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
