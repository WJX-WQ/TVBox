# TVBox

基于 [takagen99/Box](https://github.com/takagen99/Box) 的 Fork，TV 视频聚合播放器。

## 与原版的差异

| 改进项 | 说明 |
|--------|------|
| **动态 versionCode** | 基于时间戳自动递增，支持 OTA 版本管理 |
| **分支感知更新** | `BUILD_BRANCH` 构建参数，支持多 channel 分发 |
| **Android 14+ 兼容** | 修复冷启动 UI 缩放异常 |
| **CI 自动构建** | 推送 main 分支自动打包 APK |

## 构建方法

### 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高
- JDK 17+
- Gradle 8.0+ (Wrapper 自带)

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

### 自定义默认设置

编辑 `app/src/main/java/com/github/tvbox/osc/base/App.java` 中的 `initParams()` 方法：

```java
putDefault(HawkConfig.HOME_REC, 1);    // 首页推荐: 0=豆瓣, 1=站点推荐, 2=历史
putDefault(HawkConfig.PLAY_TYPE, 1);   // 播放器: 0=系统, 1=IJK, 2=Exo
putDefault(HawkConfig.SEARCH_VIEW, 1); // 搜索展示: 0=文字列表, 1=缩略图
```

## 免责声明

- 本软件仅作为视频播放工具，不提供任何受版权保护的内容
- 所有内容来自用户自行配置的第三方数据源
- 使用时请遵守当地法律法规

## License

[AGPL-3.0](LICENSE)
