# 班表助手 (Shift Assistant)

> 个人自用 Android 原生应用:按月手动记录三班倒排班,通过桌面小组件 / 系统日历卡片随时查看今天和未来的班次。纯本地数据,不上传任何信息。

- **类型**:Android 原生 APK(侧载安装,不上架)
- **技术栈**:Kotlin + Jetpack Compose(Material 3)+ Room + Jetpack Glance + DataStore
- **目标机型**:真我 Neo7 SE(Android 15 / API 35),minSdk 26 / targetSdk 35
- **当前版本**:1.0.0

---

## 功能特性

| 功能 | 说明 |
|---|---|
| 月历手动排班 | 打开 App 默认月历视图,点击某天选班次/休并保存;周一为周起点,左右滑动切换月份;跨天班(如晚班 16:00-24:00)按开始日期显示 |
| 班次管理 | 增删改班次:名称、简称、开始/结束时间、时长自动计算、颜色;首次安装预置 早班 00:00-08:00 / 中班 08:00-16:00 / 晚班 16:00-24:00 |
| 地点管理 | 维护地点库,排班时从地点库点选当天地点 |
| 换班痕迹 | 修改某天班次时记录「原班 → 新班」,月历格子显示角标,点击可查看原班次 |
| 无班显示"休" | 未排班日期统一显示"休"(中性灰,非班次) |
| 班次着色 | 不同班次不同底色,月历与小组件一致着色(早蓝 / 中绿 / 晚橙) |
| 桌面小组件 | Jetpack Glance 实现,显示今天 + 未来 3 天(默认)或 7 天,点击格子直达 App 对应日期;底色跟随 App 日/夜主题 |
| 系统日历同步 | 把今天及未来的班次写入系统自带可写日历(全天事件,标题为「地点 + 时间」,无地点时回退班次名),桌面「日程」卡片即可显示班次,支持自动/手动同步与一键清除 |
| 数据备份 | 导出/导入 JSON 备份文件(班次、地点、排班、换班痕迹全量),换机或重装后一键还原 |
| 日/夜主题 | 首页一键浅色/深色切换,选择持久保存;班次颜色日夜一致 |
| 卡片化 UI | 圆角卡片 + 虚线分隔的现代风格,公共组件统一复用 |

## 截图

(可自行补充真机截图)

## 技术要点

- **数据层**:Room 单数据库 `schedule.db`(Person / ShiftType / Location / ScheduleEntry),时间统一存「本地日期 + 分钟数(0-1439)」,结束时间允许 1440(显示为 24:00)
- **响应式刷新**:Room KSP 在部分环境无法解析 Flow 返回类型,DAO 采用 suspend + 一次性查询,写入后通过 `dataVersion` 版本号机制驱动 UI 与小组件刷新(详见《开发方案.md》§11.2)
- **主题**:手动浅/深两档,DataStore 键 `dark_theme` 持久化;小组件读取同一设置(`ColorProvider(day, night)` 跟随系统的方式已弃用)
- **日历同步**:写入**系统自带可写日历**(自动挑选本地日历,设置页可更换),单向同步(班表 → 日历),只写今天及未来的有班日期,改休自动删除对应事件;ColorOS 桌面日程卡片只显示全天事件,故统一写为全天事件;事件标题优先「地点 + 时间」(如"进口 16:00-24:00"),未选地点时回退「班次名 + 时间」,详情只留备注;全天事件按 UTC 午夜写入/读回(避免日期偏移一天)
- **UI 组件**:`ui/components/CommonComponents.kt` 提供 AppCard / AppListCard / DashedDivider / ListRow / SettingsCard 等公共组件,新页面直接复用

## 构建

### 环境要求

- JDK 21
- Android SDK(compileSdk 35 / build-tools 35.0.0)
- 本机路径配置见 `local.properties`(不入库,自行创建)

### 构建命令

```bash
# Debug 版(可直接安装调试)
./gradlew :app:assembleDebug

# Release 版(正式签名)
./gradlew :app:assembleRelease
```

- Release 签名:根目录 `keystore.properties`(storeFile=keystore.jks + 密码,不入库);文件缺失时自动回退 debug 签名
- 产物位置:`app/build/outputs/apk/debug/app-debug.apk`、`app/build/outputs/apk/release/app-release.apk`

## 真机注意事项(realme UI 7 / ColorOS)

- ⚠️ realme UI 7 桌面**系统级阉割了第三方 AppWidget 添加入口**(长按桌面只有系统卡片,CONFIRM_PIN 流程不可用);桌面小组件可通过 Nova Launcher 等替代桌面添加,或改用「同步到系统日历」+ 桌面「日程」卡片方案
- ⚠️ ColorOS 日历应用对第三方日历的删除有界面缓存延迟:改休后旧条目可能在日历界面残留一段时间,日历应用会自行刷新,数据层已实时删除
- 首次开启日历同步需授予日历权限(realme 弹窗有「仅允许创建/允许/不允许」三选项,选「允许」)

## 项目结构

```
├─ 工作备忘录.md          # 需求唯一事实来源(功能清单 + 变更记录)
├─ 开发方案.md            # 技术方案与实施记录
├─ settings.gradle.kts / build.gradle.kts
├─ gradle/libs.versions.toml
├─ keystore.properties    # 本地签名配置(不入库)
└─ app/src/main/java/com/lh/shiftassistant/
   ├─ MainActivity.kt     # 主题状态、小组件跳转入口
   ├─ data/               # Room 数据库(实体/DAO/仓库)
   ├─ settings/           # DataStore 设置(主题/小组件天数/日历同步)
   ├─ backup/             # JSON 备份导出/导入
   ├─ calendar/           # 系统日历同步
   ├─ ui/                 # Compose 页面(theme/components/calendar/shift/location/settings)
   └─ widget/             # Jetpack Glance 桌面小组件
```

## 文档

- 《工作备忘录.md》:功能清单与需求变更记录(每次会话必更新)
- 《开发方案.md》:技术选型、数据模型、页面设计、验收标准与实施记录

## 版本历史

| 版本 | 日期 | 内容 |
|---|---|---|
| 1.0.0 | 2026-08-16 | 一期全部功能(月历排班/班次/地点/换班痕迹/小组件/日历同步/备份)+ 卡片化 UI 与日/夜主题 |
| 1.0.0+ | 2026-08-16 | 日历同步修复:日期偏移一天、划掉 App 后日历消失;事件标题改为「地点 + 时间」(未选地点回退班次名)。详见《开发方案.md》§11.6、§11.7(未改 versionCode,仍为 1.0.0) |

## 隐私

所有数据仅保存在本机(数据库 + DataStore),备份文件由用户自行导出,不联网、不上传任何信息。
