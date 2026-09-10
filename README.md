# 墨墨 & 团子 Android 悬浮桌宠项目工程

一个轻量、生动可爱的 Android 顶层悬浮桌宠，支持丰富的动作序列帧、手势交互、番茄钟、状态切换以及开箱即用的体验。

## 📦 Releases 下载

- **最新安装包与资源包**：请前往 [Releases 页面](https://github.com/Suara17/MomoDesktopPet/releases)
  - `墨墨与团子桌宠.apk`：开箱即用 Android 客户端安装包
  - `Momo_DesktopPet_SmilingHug.zip`：动作序列帧素材包（包含 2100+ 帧及动图预览）
  - `Momo_EdgeModes.zip`：边缘停靠模式扩展素材包

---

## 📁 目录结构

```text
├── AndroidManifest.xml          # 悬浮窗 SYSTEM_ALERT_WINDOW 与前台服务声明
├── src/com/momo/pet/
│   ├── MainActivity.java        # 权限检测、申请及启停桌宠的控制面板
│   ├── PetFloatingService.java  # 悬浮窗管理、逐帧动画引擎、手势系统与状态机
│   └── BootReceiver.java        # 开机广播自启接收器
└── res/
    ├── drawable/                # 气泡背景、控制面板卡片背景、应用图标等
    ├── values/                  # 字符串与配色定义
    └── layout/                  # 布局文件定义
```

## ✨ 核心功能与技术实现

1. **顶层悬浮窗引擎**：
   - 基于 `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY` 实现全系统顶层无缝悬浮与渲染。
2. **多维手势系统**：
   - 支持单击互动、双击缩放形态、长按拖拽吸附及边缘交互。
3. **逐帧状态机与随机事件**：
   - 包含日常待机、眨眼、抱猫、喝水、专注计时与超时入睡机制。
4. **边缘停靠模式**：
   - 拖至屏幕边缘自动切换收纳/探头模式，保持桌面清爽不遮挡工作区。

---

## 🛠️ 构建与运行

1. 可直接将本工程导入 Android Studio 进行调试与二次开发。
2. 确保在系统设置中授予应用 **「显示在其他应用的上层」（悬浮窗权限）**。
