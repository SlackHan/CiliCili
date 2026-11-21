# CiliCili

**CiliCili** 是一款面向 Android 平台的多源磁力与 BT 聚合搜索工具，采用 **Kotlin + Jetpack Compose** 构建，融合博朗（Braun）与 Teenage Engineering 的精密硬件仪表设计语言，提供高速流式并发检索、多协议索引源兼容、内置 Cloudflare 过盾凭证复用与每日自动化规则测活订阅。

- **GitHub 仓库**：[https://github.com/SlackHan/CiliCili](https://github.com/SlackHan/CiliCili)
- **最新 APK 下载（Releases）**：[https://github.com/SlackHan/CiliCili/releases](https://github.com/SlackHan/CiliCili/releases)

---

## 视觉与交互设计理念

CiliCili 延续 **博朗（Braun）/ Teenage Engineering 精密硬件仪表风格**，将搜索、信号测活与过滤操作还原为高对比度、高响应度的实体仪器交互体验：

- **色彩体系**：
  - 日间陶瓷浅色面板（`#EDEEF0` / `#F9FAF8`）与夜间石墨深色机壳（`#101216` / `#181B21`）
  - 高辨识度朱砂橙主控强调色（`#FF4F1F`）
  - 无痕模式专属石板蓝（`stealth*` 语义色彩令牌）
- **零纵向布局跳变（No Layout Shift）**：
  - 搜索进度展开、无痕模式切换、筛选栏联动与站点状态指示均采用色彩导轨、微距徽标与 `Crossfade` 原位过渡，杜绝界面跳动。

---

## 核心特性

1. **多源并发流式聚合**
   - 基于 Kotlin Coroutines 与 `Flow` 实现多站点并发检索、边搜边出。
   - 按 `InfoHash` 自动归一化去重与多源合并，实时展示各站点响应耗时与命中计数。
2. **多协议索引源支持**
   - **`HTML`**：支持 XPath 与 `css:` 选择器提取列表与详情页延迟解析磁力链接。
   - **`JSON_API`**：直接解析 JSON 接口字段并自动拼接磁力 URI。
   - **`RSS` / Atom**：原生兼容 Nyaa、动漫花园等标准 RSS/Atom 订阅搜索源。
   - **`TORZNAB`（Prowlarr / Jackett）**：支持对接自建 Prowlarr 或 Jackett 的 Torznab 接口（配置 `ApiKey`），也可通过内置脚本直接转换 Prowlarr 公开 Cardigann YAML 规则。
3. **内置 Cloudflare 过盾凭证复用**
   - 遇到 Cloudflare 5 秒盾 / Turnstile 拦截时，支持应用内一键唤起过盾认证，自动提取并按域名持久化复用 `cf_clearance` Cookie 与匹配的 `User-Agent`。
4. **DoH（DNS-over-HTTPS）与无痕模式**
   - 内置 Cloudflare / Google / 阿里 / 腾讯等 DoH 安全域名解析，降低 DNS 污染导致的站点不可达问题。
   - 支持一键切换无痕搜索模式，不留存本地搜索历史。
5. **结果二次过滤、排除词与按站筛选**
   - 在已聚合结果列表中支持实时二次关键词过滤，支持以 `-排除词` 或 `!排除词` 剔除干扰项（多个词以空格分隔）。
   - 点击搜索进度栏中的站点胶囊即可快速切换「仅看该站」，配合最小体积门槛（如 `100MB` 至 `4GB`）与多维排序迅速定位目标资源。
6. **本地稍后下载收藏夹**
   - 搜索结果卡片与详情弹窗均支持一键加入本地 Room 收藏夹（稍后下载清单）。
   - 支持单条复制/打开、一键批量复制全部收藏磁力链接（换行分隔）与一键清空；无痕模式下添加收藏会贴心提示用完可一键清理。
7. **规则健康度管理与自动停用**
   - 内置站点并发测活与按状态分组筛选（全部 / 已启用 / 连续失败 / Prowlarr·Torznab）。
   - 连续 3 次检索失败的内置公开源将自动停用并提示，避免失效节点拖慢整体搜索速度（用户自建 `TORZNAB` 源不受自动停用影响）。
8. **应用内检查更新**
   - 设置页支持直连 GitHub Releases 接口检查新版本，自动进行 `MAJOR.MINOR.PATCH` 语义化版本比对、展示版本更新日志并一键跳转下载最新 APK。

---

## 官方每日自动测活订阅源

仓库通过 GitHub Actions 每日定时运行 `scripts/sync_rules.py`，自动完成域名发布页探测、Prowlarr 上游公开规则同步与全量节点实测探活，仅将可用规则发布至 `rules/sites_live.json`。

可在应用 **「站点管理 -> 远端订阅」** 中点击快捷芯片一键填入以下任一官方订阅地址：

- **GitHub Raw 官方源**：
  ```text
  https://raw.githubusercontent.com/SlackHan/CiliCili/main/rules/sites_live.json
  ```
- **jsDelivr CDN 镜像源（国内推荐）**：
  ```text
  https://fastly.jsdelivr.net/gh/SlackHan/CiliCili@main/rules/sites_live.json
  ```

最新一次自动测活明细可查看仓库内的 [rules/sync_report.md](rules/sync_report.md)。

---

## 下载与安装

前往 [GitHub Releases](https://github.com/SlackHan/CiliCili/releases) 页面下载最新版本的 `app-release.apk` 安装包（Android 8.0 / API 26 及以上设备均可安装）。

---

## 工程架构与目录说明

```text
CiliCili/
├── app/
│   ├── src/main/
│   │   ├── assets/
│   │   │   └── sites_default.json        # 内置默认站点规则库
│   │   ├── java/com/lanhan/cilicili/
│   │   │   ├── data/                     # Room 数据库（站点、历史、收藏夹）、DataStore 与网络抓取层
│   │   │   ├── domain/                   # 多协议解析引擎（HTML/JSON/RSS/Torznab）、搜索聚合器与版本检查器
│   │   │   └── ui/                       # Jetpack Compose 界面（搜索、收藏夹、站点管理、过盾页、设置）
│   │   └── res/                          # 图标、主题与字符串资源
│   └── src/test/                         # Kotlin 单元测试（解析器、二次过滤、收藏夹、版本比对等）
├── rules/
│   ├── sites_live.json                   # 每日自动测活产出的在线可用订阅源
│   └── sync_report.md                    # 每日站点同步与测活状态报告
└── scripts/
    ├── sync_rules.py                     # 域名发现、Prowlarr 公开规则转换与并发测活脚本
    ├── domain_sources.json               # 多镜像站点域名发布页正则提取配置
    └── test_sync_rules.py                # 规则同步脚本 Python 单元测试
```

---

## 本地构建与规则同步

### 1. Android 单元测试与 APK 构建

请确保本地已配置 JDK 17 与 Android SDK（Android API 35）：

```powershell
# 运行全部 Kotlin 单元测试并构建 Debug APK
.\gradlew.bat testDebugUnitTest assembleDebug
```

构建产物位于 `app/build/outputs/apk/debug/app-debug.apk`。

### 2. 站点规则同步与 Python 单元测试

需要 Python 3.10 及以上环境（可选安装 `PyYAML`，脚本亦内置轻量级 Cardigann YAML 解析回退支持）：

```powershell
# 运行规则同步脚本单元测试
python -m unittest discover -s scripts -p "test_*.py"

# 执行域名探测、Prowlarr 公开规则转换与在线测活，更新 rules/sites_live.json 与 rules/sync_report.md
python scripts/sync_rules.py
```
