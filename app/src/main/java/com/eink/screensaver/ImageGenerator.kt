package com.eink.screensaver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
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
 * 写入掌阅本地屏保目录 iReader/skins/本地屏保/daily.png（同名覆盖），并保证 <1MB。
 */
object ImageGenerator {

    const val W = 1264
    const val H = 1680
    private const val MAX_BYTES = 1_000_000
    private const val FILE_NAME = "daily.png"

    /** 掌阅本地屏保目录。 */
    fun targetDir(): File =
        File(Environment.getExternalStorageDirectory(), "iReader/skins/本地屏保")

    fun targetFile(): File = File(targetDir(), FILE_NAME)

    /** 拉数据 → 渲染 → 写文件。返回最终写入的文件；失败返回 null。 */
    suspend fun generate(context: Context): File? {
        val content = withContext(Dispatchers.IO) { Repository(context).refresh() }
        val bing = withContext(Dispatchers.IO) { downloadBitmap(content.bingImageUrl) }
        val bmp = withContext(Dispatchers.Main) { renderToBitmap(context, content, bing) }
        val file = withContext(Dispatchers.IO) { save(bmp) }
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

    /**
     * 写 PNG，保证 <1MB：PNG 无损，若超限就整体等比缩小重试（掌阅会自适应放大）。
     * 始终用同一文件名 daily.png，避免掌阅里选中的图失效。
     */
    private fun save(src: Bitmap): File? {
        val dir = targetDir()
        if (!dir.exists() && !dir.mkdirs()) return null
        val file = targetFile()

        var scale = 1.0
        repeat(6) {
            val bmp = if (scale >= 1.0) src
            else Bitmap.createScaledBitmap(src, (W * scale).toInt(), (H * scale).toInt(), true)
            val bos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
            val size = bos.size()
            if (bmp !== src) bmp.recycle()
            if (size <= MAX_BYTES) {
                file.writeBytes(bos.toByteArray())
                return file
            }
            scale *= 0.85
        }
        return null
    }
}
