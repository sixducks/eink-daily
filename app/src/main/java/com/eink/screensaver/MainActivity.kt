package com.eink.screensaver

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.eink.screensaver.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 入口页：
 *  - 「授权存储」申请"所有文件访问"权限（写掌阅公共文件夹需要）。
 *  - 「立即生成」当场拉数据、渲染并写入 iReader/skins/本地屏保/daily.png。
 *  - 下方用同款排版实时预览。
 */
class MainActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var repo: Repository
    private lateinit var preview: View
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repo = Repository(this)
        preview = findViewById(R.id.preview)
        status = findViewById(R.id.tvStatus)

        findViewById<Button>(R.id.btnPerm).setOnClickListener { requestAllFilesAccess() }
        findViewById<Button>(R.id.btnGenerate).setOnClickListener { generate() }

        // 历史来源单选：先按已保存值勾选，再挂监听（避免初始化就触发刷新）
        val rg = findViewById<RadioGroup>(R.id.rgSource)
        rg.check(
            if (repo.getHistorySource() == Repository.SOURCE_WIKI) R.id.rbWiki else R.id.rbBaidu
        )
        rg.setOnCheckedChangeListener { _, id ->
            repo.setHistorySource(
                if (id == R.id.rbWiki) Repository.SOURCE_WIKI else Repository.SOURCE_BAIDU
            )
            refreshPreview()
        }

        // 每天刷新时间：点按弹出时间选择器
        val btnTime = findViewById<Button>(R.id.btnTime)
        renderRefreshTime(btnTime)
        btnTime.setOnClickListener {
            TimePickerDialog(this, { _, hh, mm ->
                Scheduler.setTime(this, hh, mm)
                renderRefreshTime(btnTime)
                status.text = "已设为每天 %02d:%02d 刷新".format(hh, mm)
            }, Scheduler.getHour(this), Scheduler.getMinute(this), true).show()
        }

        // 打开先显示缓存，没有就拉一次填充预览
        val cached = repo.cached()
        if (cached != null) Renderer.bindText(preview, cached) else refreshPreview()

        // 顺手把每日定时任务排上
        Scheduler.scheduleDaily(this)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else true // Android 10 及以下用 WRITE_EXTERNAL_STORAGE（清单已声明）

    private fun updateStatus() {
        status.text = if (hasAllFilesAccess())
            "已授权。点「立即生成」写入目录：${ImageGenerator.targetDir().absolutePath}（每次新文件名）"
        else
            "未授权存储：先点「授权存储」开启「所有文件访问」权限。"
    }

    private fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                i.data = Uri.parse("package:$packageName")
                startActivity(i)
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            status.text = "当前系统无需该权限。"
        }
    }

    private fun generate() {
        if (!hasAllFilesAccess()) {
            status.text = "请先「授权存储」，否则无法写入掌阅目录。"
            return
        }
        status.text = "正在生成…"
        scope.launch {
            val file = ImageGenerator.generate(this@MainActivity)
            status.text = if (file != null)
                "已生成：${file.name}\n去掌阅：设置→设备→屏幕显示→屏保→本地屏保→选「轮播」。以后每天自动换新图。"
            else
                "生成失败：检查网络与存储权限后重试。"
            // 同步刷新预览
            repo.cached()?.let { c ->
                Renderer.bindText(preview, c)
                withContext(Dispatchers.IO) {
                    Renderer.loadBing(preview.findViewById<ImageView>(R.id.ivBing), c.bingImageUrl)
                }
            }
        }
    }

    private fun renderRefreshTime(btn: Button) {
        btn.text = "%02d:%02d".format(Scheduler.getHour(this), Scheduler.getMinute(this))
    }

    private fun refreshPreview() {
        scope.launch {
            val c = withContext(Dispatchers.IO) { repo.refresh() }
            Renderer.bindText(preview, c)
            withContext(Dispatchers.IO) {
                Renderer.loadBing(preview.findViewById<ImageView>(R.id.ivBing), c.bingImageUrl)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
