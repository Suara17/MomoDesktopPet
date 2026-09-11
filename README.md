# <img src="docs/images/ic_launcher.png" width="42" height="42" valign="middle" alt="App Icon"> 墨墨 & 团子 Android 悬浮桌宠

一个生动、轻量且可爱的 Android 顶层悬浮桌宠客户端。支持丰富的序列帧微动作、手势交互拖拽、学习与专注模式、音乐陪伴、迷你模式，以及智能贴边停靠探头。

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

前往 [Releases 发布页面](https://github.com/Suara17/MomoDesktopPet/releases/tag/v1.5.0) 获取最新产物：

- **开箱即用安装包**：[MomoDesktopPet.apk](https://github.com/Suara17/MomoDesktopPet/releases/download/v1.5.0/MomoDesktopPet.apk)（Android 客户端安装包）
- **全套动作素材包**：[Momo_DesktopPet_Assets.zip](https://github.com/Suara17/MomoDesktopPet/releases/download/v1.5.0/Momo_DesktopPet_Assets.zip)（包含 2400+ 帧高清序列帧与 GIF 动图素材）

---

## ✨ 核心特性

1. **系统级顶层悬浮**：
   - 基于 Android `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY` 实现，支持全界面无缝悬浮陪伴。
2. **灵动手势与交互响应**：
   - 支持单击互动反馈、双击体型缩放切换、长按拖拽平滑移动与物理跟随。
3. **学习与休闲计时模式**：
   - 长按唤起快捷菜单，快速激活「学习模式」或「休闲模式」，配备美观的悬浮胶囊计时器，实时显示倒计时/正计时。
4. **音乐播放器**：
   - 提供悬浮音乐播放控制，让桌宠陪伴学习、工作与休闲时光。
   - **当前暂时只支持 TuneFreeNext**，其他音乐播放器的兼容支持将在后续版本中逐步完善。
5. **迷你模式**：
   - 支持一键切换为更小、更轻量的桌宠形态，减少屏幕占用；需要互动或查看状态时可快速恢复，方便快捷。
6. **逐帧状态机与随机生活事件**：
   - 包含眨眼、发呆、喝水、抱猫、专注番茄钟以及闲置入睡等丰富的生活状态机制。
7. **智能贴边探头收纳**：
   - 拖至屏幕左右边缘自动触发收纳或探头模式，保持桌面清爽，不干扰日常操作。

---

## 📁 项目目录组织

```text
├── AndroidManifest.xml          # 悬浮窗权限与前台服务声明
├── src/com/momo/pet/
│   ├── MainActivity.java        # 控制面板：权限申请、启停开关
│   ├── PetFloatingService.java  # 核心服务：动画、手势、计时与播放器控制
│   ├── StatsManager.java        # 学习/休闲时长数据管理
│   ├── StatsActivity.java        # 时长统计展示与日期筛选
│   └── BootReceiver.java        # 开机自启动广播接收
├── res/                         # Android 资源文件
└── docs/images/                 # 演示动图与项目预览素材
```

---

## 🛠️ 构建与运行

1. 本工程可直接导入 Android Studio 进行调试与二次开发。
2. 首次运行请在系统设置中授予 **「显示在其他应用的上层」（悬浮窗权限）**。
3. 如需使用音乐播放器功能，请先安装并启用 **TuneFreeNext**；当前版本暂不保证其他播放器的兼容性。

---

## 🤝 鸣谢与社区认可

本项目遵循开源精神，感谢以下开源社区与交流平台的支持：

- [**LINUX DO**](https://linux.do/) - 新的承载，新的起航。真诚、友善、团结、专业，共建自由多元的开源与技术交流社区。
