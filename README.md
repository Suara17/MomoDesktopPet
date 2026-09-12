# <img src="docs/images/ic_launcher.png" width="42" height="42" valign="middle" alt="App Icon"> 墨墨 & 团子 Android 悬浮桌宠

一个生动、轻量且可爱的 Android 顶层悬浮桌宠客户端。支持丰富的序列帧微动作、手势交互拖拽、学习与专注模式、沉浸式音效与触感震动反馈、应用防沉迷监督提醒（支持单次与今日累计限额）、音乐陪伴联动与专属听歌动作、迷你极简形态，以及智能贴边停靠探头。

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
- **全套动作素材包**：[Momo_DesktopPet_Assets.zip](https://github.com/Suara17/MomoDesktopPet/releases/download/v1.5.0/Momo_DesktopPet_Assets.zip)（包含 2600+ 帧高清切片，含 SmilingHug、EdgeModes 边缘模式及 Music_Blink 听音乐素材）

---

## ✨ 核心特性

1. **系统级顶层悬浮**：
   - 基于 Android  实现，支持全界面无缝悬浮陪伴。
2. **灵动手势与交互响应**：
   - 支持单击互动反馈、双击体型缩放切换、长按拖拽平滑移动与物理吸附。
3. **沉浸式音效系统（SoundPool）**：
   - 内置轻快生动的音效反馈（点击、长按菜单、拖拽、吸附边缘、体型切换、猫咪互动、开始计时、完成与监督提醒音），支持独立开关与音量调节。
4. **细腻多级触感与震动提醒（Vibrator / VibrationEffect）**：
   - 区分日常计时结束、手动提前结束、防沉迷初次提醒以及严重超时不同场景，定制不同节奏与力度的多段脉冲震动，即使静音状态下也不会错过重要提醒。
5. **应用沉迷监督与多维提醒**：
   - 支持使用情况访问权限（UsageStats），针对短视频、社交等易沉迷应用进行时长检测：
     - 支持全局及单个应用的**单次连续时长限制**与**今日累计时长限制**。
     - 超时触发阶梯式监督机制（独立悬浮气泡提醒 + 专属警示音效 + 多段强劲震动脉冲）。
6. **学习与休闲计时模式**：
   - 长按唤起快捷菜单激活「学习模式」或「休闲模式」，配备悬浮胶囊计时器实时显示倒计时/正计时，并支持历史统计查看。
7. **音乐播放器联动与听歌动作**：
   - 提供悬浮音乐播放控制，陪伴学习与放松。
   - 内置专属**听音乐动作切片（）**，听歌时更具灵性与陪伴感。
   - **当前暂时只支持 TuneFreeNext**，其他播放器的兼容性后续逐步完善。
8. **迷你模式与边缘贴边探头**：
   - 支持一键切换迷你极简形态，拖至屏幕左右边缘自动触发收纳或探头模式，保持桌面清爽不遮挡工作区。
9. **逐帧状态机与生活化事件**：
   - 包含眨眼、发呆、喝水、抱猫、听音乐、专注计时以及超时入睡等丰富的生活状态机制。

---

## 📁 项目目录组织



---

## 🛠️ 构建与运行

1. 本工程可直接导入 Android Studio 进行编译调试与二次开发。
2. 首次运行请在系统设置中授予 **「显示在其他应用的上层」（悬浮窗权限）**。
3. 若需使用监督提醒功能，请按界面引导开启 **「有权查看使用情况」（使用记录访问权限）**。
4. 如需使用音乐播放器功能，请先安装并启用 **TuneFreeNext**。

---

## 🤝 鸣谢与社区认可

本项目遵循开源精神，感谢以下开源社区与交流平台的支持：

- [**LINUX DO**](https://linux.do/) - 新的承载，新的起航。真诚、友善、团结、专业，共建自由多元的开源与技术交流社区。
