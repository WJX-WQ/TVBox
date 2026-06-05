# TVBox

基于 [takagen99/Box](https://github.com/takagen99/Box) 深度优化的 Fork，Android TV 视频聚合播放器。

支持多引擎爬虫（JAR/JS/Python）、多播放内核（IJK/ExoPlayer/系统）、直播 EPG、字幕、远程投屏等。

---

## 与原版的差异

| 改进项 | 说明 |
|--------|------|
| **OkHttp 全面替代 OkGo** | 移除 OkGo 依赖（22 个文件 / 56 个 import），统一使用 OkHttp |
| **Android 14+ 兼容** | 修复冷启动 UI 缩放异常 |
| **动态 versionCode** | 基于时间戳自动递增，支持 OTA 版本管理 |
| **分支感知更新** | `BUILD_BRANCH` 构建参数，支持多 channel 分发 |
| **CI 自动构建** | 推送 main 分支自动打包 APK |
| **构建工具升级** | AGP 8.2.2 + Kotlin 2.0.21 + Gradle 8.5 |
| **性能优化** | 替换废弃 `lifecycle-extensions` 为精确依赖，并行构建启用 |

### 代码清理（15 轮优化）

| 清理项 | 规模 |
|--------|------|
| XWalk 遗留代码 | 5 个文件 ~750 行 + 66 MB 二进制 |
| urlhttp 死代码 | 4 个文件 ~945 行（无人引用） |
| 循环依赖 | `catvod/net/OkHttp` ↔ `OkGoHelper` 已打破 |
| 冗余注解 | 35+ 文件的 JetBrains `@NotNull` & `@TargetApi` |
| 依赖升级 | okhttp, okio, gson, jsoup, commons-io/text 等 9 个库 |

---

## 构建方法

### 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高
- JDK 17+
- Gradle 8.5+ (Wrapper 自带)

### 快速构建

```bash
# 克隆
git clone https://github.com/zyqfork/TVBox.git
cd TVBox

# 全部 Flavor 构建
./gradlew assemblerelease

# 指定 Flavor（arm64 + generic + normal = Java 引擎）
./gradlew assembleArm64GenericNormalRelease

# 指定 Flavor（arm64 + generic + python = Python 引擎支持）
./gradlew assembleArm64GenericPythonRelease
```

构建产物在 `app/build/outputs/apk/` 下。

### Flavor 矩阵

| 维度 | 取值 | 说明 |
|------|------|------|
| **abi** | `armeabi` / `arm64` | CPU 架构 |
| **brand** | `generic` / `hisense` | 设备适配（hisense 包名不同） |
| **mode** | `normal` (java) / `python` | 爬虫引擎 — normal 有 JS+JAR，python 额外支持 Python 蜘蛛 |

---

## 架构概览

### 三层爬虫引擎

```
远程 JSON 配置 → ApiConfig.parse()
       │
       ├── Spider (JAR)    → DexClassLoader 热加载
       ├── Spider (JS)     → QuickJS 引擎执行
       └── Spider (Python) → Chaquopy 运行（仅 python flavor）
```

接口统一：`homeContent()` / `categoryContent()` / `detailContent()` / `searchContent()` / `playerContent()` / `liveContent()`

### 播放引擎

| 内核 | 说明 |
|------|------|
| **IJKPlayer** | ffmpeg 软解 + 硬解 |
| **ExoPlayer (Media3)** | Dash / HLS / SmoothStreaming |
| **AndroidMediaPlayer** | 系统原生播放器 |
| **第三方** | MX Player / Kodi / Reex Player |

### HTTP 层

```
OkHttp (统一客户端)
  ├── catvod/net/OkHttp.java   — 爬虫引擎 HTTP 工具
  ├── OkGoHelper.java          — 初始化 DNS/SSL/Timeout 配置
  ├── SpiderExecutor.java      — 搜索线程池管理
  └── SpiderParser.java        — XML/JSON 结果解析
```

### 目录结构

```
TVBox/
├── app/
│   ├── src/main/java/com/github/tvbox/osc/
│   │   ├── api/            — ApiConfig 远程配置解析
│   │   ├── base/           — App 入口, BaseActivity
│   │   ├── bean/           — 数据模型
│   │   ├── data/           — Room 数据库
│   │   ├── player/         — 播放器控制层
│   │   ├── server/         — NanoHTTPD 远程控制
│   │   ├── subtitle/       — 字幕引擎 (SRT/ASS/SCC/STL/TTML)
│   │   ├── ui/             — Activity / Fragment / Dialog / Adapter
│   │   ├── util/           — 工具类 (含 SpiderExecutor, SpiderParser, WebViewManager)
│   │   └── viewmodel/      — ViewModel 层
│   ├── src/main/java/com/github/catvod/
│   │   ├── crawler/        — Spider 抽象 + JAR/JS/Python 加载器
│   │   └── net/            — OkHttp 统一客户端
│   └── src/{normal,python}/— 双 Flavor 源集
├── quickjs/                — QuickJS JavaScript 引擎 JNI 封装
└── pyramid/                — Python 爬虫模块 (Chaquopy)
```

---

## 自定义默认设置

编辑 `app/src/main/java/com/github/tvbox/osc/base/App.java` 中的 `initParams()` 方法修改应用默认值：

```java
putDefault(HawkConfig.HOME_REC, 1);    // 首页推荐: 0=豆瓣, 1=站点推荐, 2=历史
putDefault(HawkConfig.PLAY_TYPE, 1);   // 播放器: 0=系统, 1=IJK, 2=Exo
putDefault(HawkConfig.SEARCH_VIEW, 1); // 搜索展示: 0=文字列表, 1=缩略图
putDefault(HawkConfig.DOH_URL, 0);     // 安全DNS: 0=关闭, 1-6=各服务商
```

---

## 技术栈

| 类别 | 库 |
|------|-----|
| **HTTP** | OkHttp 3.14.9, Okio 3.10.2 |
| **解析** | Gson 2.11.0, Jsoup 1.18.3, XStream, SimpleXML |
| **播放** | IJKPlayer, Media3-ExoPlayer 1.3.1, DKPlayer |
| **图片** | Glide 4.16.0 |
| **数据库** | Room 2.6.2 |
| **JS 引擎** | QuickJS (自编译 JNI) |
| **Python** | Chaquopy 12.0.1 (python flavor) |
| **UI** | AndroidX Leanback, RecyclerView, ViewPager2 |
| **构建** | AGP 8.2.2, Kotlin 2.0.21, Gradle 8.5 |

---

## 免责声明

- 本软件仅作为视频播放工具，不提供任何受版权保护的内容
- 所有内容来自用户自行配置的第三方数据源
- 使用时请遵守当地法律法规

## License

[AGPL-3.0](LICENSE)
