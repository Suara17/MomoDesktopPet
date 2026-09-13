# <img src="docs/images/ic_launcher.png" width="42" height="42" valign="middle" alt="App Icon"> 墨墨 & 团子 Android 悬浮桌宠

一个生动、轻量且可爱的 Android 顶层悬浮桌宠客户端。支持丰富的序列帧微动作、手势交互拖拽、宠物羁绊成长与心情喂食系统、微信/支付宝通知监听自动记账、模块化功能管理、学习与专注模式、沉浸式音效与触感震动反馈、应用防沉迷监督提醒、音乐陪伴联动、迷你极简形态，以及智能贴边停靠探头。

---

## 🎬 效果演示

| 待机与呼吸互动 | 拥抱互动 | 逗猫互动 |
| :---: | :---: | :---: |
| <img src="docs/images/preview_idle.gif" width="210" alt="待机状态"> | <img src="docs/images/preview_hug.gif" width="210" alt="拥抱状态"> | <img src="docs/images/preview_cat.gif" width="210" alt="逗猫状态"> |

| 专注/学习状态 | 平滑手势拖拽 | 边缘停靠/探头收纳 |
| :---: | :---: | :---: |
| <img src="docs/images/preview_study.gif" width="210" alt="学习状态"> | <img src="docs/images/preview_drag.gif" width="210" alt="拖拽状态"> | <img src="docs/images/preview_edge.gif" width="210" alt="边缘停靠"> |

---

## 📦 下载与安装

前往 [Releases 发布页面](https://github.com/Suara17/MomoDesktopPet/releases/tag/v1.5.1) 获取最新产物：

- **开箱即用安装包**：[MomoDesktopPet.apk](https://github.com/Suara17/MomoDesktopPet/releases/download/v1.5.1/MomoDesktopPet.apk)（Android 客户端安装包 v1.5.1）
- **全套动作素材包**：[Momo_DesktopPet_Assets.zip](https://github.com/Suara17/MomoDesktopPet/releases/download/v1.5.1/Momo_DesktopPet_Assets.zip)（包含 2600+ 帧高清切片，含 SmilingHug、EdgeModes 边缘模式及 Music_Blink 听音乐素材）

---

## ✨ 核心特性

1. **系统级顶层悬浮**：
   - 基于 Android `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY` 实现，支持全界面无缝悬浮陪伴。
2. **羁绊等级与心情成长体系（新）**：
   - 引入 `PetGrowthManager` 成长机制，覆盖 **Lv.1 初遇** 至 **Lv.8 永恒契约** 深度陪伴进阶。
   - 实时心情值计算（0~100）与陪伴时长转化经验；支持**投喂小鱼干**互动，防沉迷多次无视将影响心情，更具情感温度。
3. **微信 / 支付宝自动记账服务**：
   - 基于 `NotificationListenerService` 智能捕获微信支付、支付宝消费入账通知，自动提取金额、商户信息并归档入库（SQLite `BillDbHelper`），随时在主面板查看收支报表。
4. **模块化功能与中心配置管理**：
   - 引入 `ModuleConfigManager` 统一管理各功能模块开关与偏好，主界面采用全新整合式架构，操作更聚合直观。
5. **细腻多级触感与震动提醒**：
   - 区分日常计时结束、手动提前结束、防沉迷初次提醒以及严重超时不同场景，定制不同节奏与力度的多段脉冲震动。
6. **沉浸式音效系统（SoundPool）**：
   - 内置轻快生动的音效反馈（点击、长按菜单、拖拽、吸附边缘、体型切换、猫咪互动、开始计时、气泡弹出、完成与监督提醒音），支持独立开关与音量调节。
7. **应用防沉迷监督与多维提醒**：
   - 基于 `UsageStats` 针对短视频、社交等易沉迷应用进行时长检测，支持单次连续时长与今日累计限额，超时触发阶梯式声光震提醒。
8. **学习与休闲计时模式**：
   - 长按唤起快捷菜单激活「学习模式」或「休闲模式」，配备悬浮胶囊计时器实时显示倒计时/正计时。
9. **音乐播放器联动与听歌动作**：
   - 提供悬浮音乐播放控制，内置专属**听音乐动作切片（`listen_music`）**；**当前暂时只支持 TuneFreeNext**。
10. **迷你模式与边缘贴边探头**：
    - 支持一键切换迷你极简形态，拖至屏幕左右边缘自动触发收纳或探头模式，保持桌面清爽不遮挡工作区。
11. **逐帧状态机与丰富生活事件**：
    - 包含眨眼、发呆、喝水、抱猫、听音乐、专注计时以及超时入睡等丰富的生活状态机制。

---

## 📁 项目目录组织

```text
├── AndroidManifest.xml          # 权限（悬浮窗、前台服务、震动、使用统计、通知监听）声明
├── src/com/momo/pet/
│   ├── MainActivity.java        # 统一控制面板：权限申请、功能聚合开关、成长状态与账本报表
│   ├── PetFloatingService.java  # 核心服务：动画引擎、手势管理、计时器、触感震动与状态机
│   ├── PetGrowthManager.java    # 成长系统：羁绊等级、心情值、投喂小鱼干与互动反馈
│   ├── PetNotificationListenerService.java # 通知监听服务：微信/支付宝自动记账
│   ├── BillDbHelper.java        # 账单数据库管理：记录本地消费流水与金额汇总
│   ├── ModuleConfigManager.java # 模块配置管理：中心化管理各功能模块状态
│   ├── SoundManager.java        # 音效引擎：SoundPool 池化管理与事件音效
│   ├── AppMonitorManager.java   # 监督系统：前台应用检测、连续时长与今日累计限额
│   ├── StatsManager.java        # 学习/休闲时长持久化与数据统计
│   ├── MenuIconView.java        # 自定义悬浮交互菜单图标组件
│   └── BootReceiver.java        # 开机自启动广播接收
├── res/
│   ├── raw/                     # 互动、开始、完成与提醒音效音频（WAV）
│   ├── drawable/                # 背景气泡、菜单卡片与图标资源
│   ├── drawable-nodpi/          # 默认背景图与高清位图
│   └── values/                  # 字符串与配色定义
└── docs/images/                 # 演示动图与项目预览素材
```

---

## 🛠️ 构建与运行

1. 本工程可直接导入 Android Studio 进行编译调试与二次开发。
2. 首次运行请在系统设置中授予 **「显示在其他应用的上层」（悬浮窗权限）**。
3. 若需使用自动记账功能，请开启 **「通知使用权」（通知监听权限）**。
4. 若需使用监督提醒功能，请开启 **「有权查看使用情况」（使用记录访问权限）**。
5. 如需使用音乐播放器功能，请先安装并启用 **TuneFreeNext**。

---

## 🤝 鸣谢与社区认可

本项目遵循开源精神，感谢以下开源社区与交流平台的支持：

- [**LINUX DO**](https://linux.do/) - 新的承载，新的起航。真诚、友善、团结、专业，共建自由多元的开源与技术交流社区。
