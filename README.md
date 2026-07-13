# 每日一屏 · 掌阅墨水屏待机图

给掌阅 iReader **Ocean5 Pro** 用的「息屏待机图」自动生成器。它每天把一张排版好的图片写进掌阅的「本地屏保」文件夹，按电源键休眠后就常驻显示（墨水屏零耗电）。

一张图包含：

- **今日诗词**（jinrishici，主视觉大字）
- **历史上的今天**（维基百科精选，6 条，可调）
- **Bing 每日壁纸**（转灰度做顶部横幅）
- 日期 + 更新时间

## 为什么是"生成图片"，不是"运行 App"

掌阅的「本地屏保」= 息屏后一直挂在屏上的静态图。第三方 App 不能直接接管这个系统机制，但可以**每天生成一张 PNG 覆盖到它读取的文件夹**，让系统自己去显示。所以本 App 是个**后台生成器**：定时拉数据 → 用布局离屏渲染成 1264×1680 灰度 PNG → 写入 `iReader/skins/本地屏保/daily.png`（同名覆盖，<1MB）。

> ⚠️ **一个待验证点**：掌阅选中 `daily.png` 后，App 覆盖同名文件，休眠图是否自动跟着变、还是需要重新选一次——取决于掌阅固件。装好用「立即生成」测一下就知道。若不自动刷新，我们再换策略。

## 目标设备：掌阅 iReader Ocean5 Pro

- 7 英寸竖屏，**1264 × 1680（300PPI）**，Carta 1300，**256 级灰阶**
- 全开放安卓系统，可安装 APK、可授权"所有文件访问"
- 输出严格按 1264×1680 出图，灰度，避免设备缩放糊掉

## 数据源

| 内容 | 接口 | 备注 |
|---|---|---|
| 诗词 | `https://v2.jinrishici.com/one.json` | 首次自动申请 token 并永久缓存，请求头带 `X-User-Token` |
| 历史今天 | `https://zh.wikipedia.org/api/rest_v1/feed/onthisday/selected/MM/DD` | 按当天日期拼接，取年份最近 6 条（`Repository.HISTORY_COUNT` 可调） |
| Bing 壁纸 | `https://cn.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1` | `images[0].url` 前拼 `https://cn.bing.com`，下载后转灰度 |

**图片选型**：用 Bing 而非维基每日图片。Bing 是稳定的风景大片、横构图、256 灰阶下层次好；维基 POTD 题材/比例/对比度都不稳定，做顶部横幅是抽盲盒。

**容错**：任一接口失败，该字段自动退回上次缓存值，绝不白屏。

## 编译（不用装 Android Studio）

推 GitHub 后由 `.github/workflows/build.yml` 云端自动出 APK，从 Actions 或 Release 下载。详见仓库 Actions 页。

> 本地要编译的话：用 Android Studio 打开根目录 → Gradle Sync → Build APK。

## 安装 + 使用

1. 把 APK 装到 Ocean5 Pro。
2. 打开「每日一屏」→ 点「**授权存储**」→ 开启「所有文件访问」权限（写系统公共目录必须）。
3. 点「**立即生成**」→ 成功后会提示写入路径 `iReader/skins/本地屏保/daily.png`。
4. 进掌阅 **设置 → 设备 → 屏幕显示 → 屏保 → 本地屏保 → 选中 daily.png**。
5. 按电源键休眠，确认待机图就是它。
6. 之后每 6 小时后台自动刷新覆盖（`Scheduler` 可调），开机自启。

## 可调项

- 刷新频率：`Scheduler.scheduleDaily()` 里的 `6, TimeUnit.HOURS`。
- 历史条数 / 单条长度：`Repository.HISTORY_COUNT`、`MAX_EVENT_LEN`。
- 版式/字号/颜色：`res/layout/view_daily.xml`。
- Bing 横幅高度：`view_daily.xml` 里 `ivBing` 的 `layout_height`（默认 260dp）；不想要图就删掉这个 `ImageView`。

## 结构

```
app/src/main/
├─ AndroidManifest.xml            清单：入口页 + 开机接收器 + 存储权限
├─ java/com/eink/screensaver/
│  ├─ MainActivity.kt             入口页：授权 + 立即生成 + 预览
│  ├─ ImageGenerator.kt           核心：布局离屏渲染成 <1MB 灰度 PNG 并写入
│  ├─ DailyWorker.kt              WorkManager 定时任务
│  ├─ Scheduler.kt                定时/立即执行调度
│  ├─ BootReceiver.kt             开机自启
│  ├─ Renderer.kt                 把数据绑到布局（生成/预览共用）
│  ├─ data/Content.kt             数据模型 + JSON 缓存
│  ├─ data/Repository.kt          3 个接口抓取 + 容错
│  └─ net/Http.kt                 OkHttp 封装
└─ res/
   ├─ layout/view_daily.xml       一整屏的排版（1264×1680）
   └─ layout/activity_main.xml    入口页
```
