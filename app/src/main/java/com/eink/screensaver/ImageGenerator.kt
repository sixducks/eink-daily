package com.eink.screensaver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import com.eink.screensaver.data.Content
import com.eink.screensaver.data.Repository
import com.eink.screensaver.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 核心：把 view_daily 布局离屏渲染成 1264×1680 的灰度 PNG，
 * 同名覆盖写入掌阅本地屏保目录 iReader/skins/本地屏保/daily.png，并保证 <1MB。
 */
object ImageGenerator {

    const val W = 1264
    const val H = 1680
    private const val MAX_BYTES = 1_000_000
    // 复用掌阅“系统日历”屏保：它本就每天 00:00 自动重写这个文件、并重新读取显示。
    // 我们同名覆盖它，掌阅就会把我们的图当成当天日历屏保——有机会实现全自动刷新。
    private const val FILE_NAME = "系统日历zh.jpg"

    /** 掌阅“系统日历”屏保目录。 */
    fun targetDir(): File =
        File(Environment.getExternalStorageDirectory(), "iReader/skins/系统日历")

    fun targetFile(): File = File(targetDir(), FILE_NAME)

    /** 拉数据 → 渲染 → 同名覆盖写文件。返回文件；失败返回 null。 */
    suspend fun generate(context: Context): File? {
        val content = withContext(Dispatchers.IO) { Repository(context).refresh() }
        val bing = withContext(Dispatchers.IO) { downloadBitmap(content.bingImageUrl) }
        val bmp = withContext(Dispatchers.Main) { renderToBitmap(context, content, bing) }
        val file = withContext(Dispatchers.IO) { save(context, bmp) }
        bmp.recycle()
        bing?.recycle()
        return file
    }

    private fun downloadBitmap(url: String): Bitmap? {
        if (url.isBlank()) return null
        return try {
            val bytes = Http.getBytes(url)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    /** 离屏渲染：inflate 布局、绑数据、精确按屏幕尺寸 measure/layout，再画到 Bitmap。 */
    private fun renderToBitmap(context: Context, content: Content, bing: Bitmap?): Bitmap {
        val root: View = LayoutInflater.from(context).inflate(R.layout.view_daily, null)
        Renderer.bindText(root, content)
        Renderer.bindBing(root.findViewById<ImageView>(R.id.ivBing), bing)

        val wSpec = View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY)
        root.measure(wSpec, hSpec)
        root.layout(0, 0, W, H)

        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        root.draw(canvas)
        return out
    }

    /** 写 JPG（同名覆盖 系统日历zh.jpg），保证 <1MB：从高质量起，超限就降质量重试。 */
    private fun save(context: Context, src: Bitmap): File? {
        val dir = targetDir()
        if (!dir.exists() && !dir.mkdirs()) return null
        val file = targetFile()

        for (quality in intArrayOf(92, 85, 78, 70, 60)) {
            val bos = ByteArrayOutputStream()
            src.compress(Bitmap.CompressFormat.JPEG, quality, bos)
            if (bos.size() <= MAX_BYTES) {
                file.writeBytes(bos.toByteArray())
                // 通知系统媒体库，图库/文件管理器立即刷新
                MediaScannerConnection.scanFile(
                    context, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null
                )
                return file
            }
        }
        return null
    }
}
