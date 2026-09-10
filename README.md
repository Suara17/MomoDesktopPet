# 墨墨 & 团子 Android 悬浮桌宠项目工程

这是一个可以直接导入 Android Studio，或者直接在本地/服务器通过命令行构建的 Android 源码工程。

## 目录结构
- `AndroidManifest.xml`：包含悬浮窗权限 SYSTEM_ALERT_WINDOW 与前台服务声明
- `src/com/momo/pet/`：
  - `MainActivity.java`：权限检测、申请及启停桌宠的控制面板 Activity
  - `PetFloatingService.java`：悬浮窗窗口管理、逐帧动画引擎、手势拖拽缩放与状态机
- `assets/`：
  - `manifest.json`：动作序列帧配置文件
  - `frames/`：包含全套 44 帧 PNG 动作素材
- `res/`：
  - 图标与字符串资源

## 核心技术点
1. **纯代码动态构建悬浮窗 UI**：使用 `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY` 实现跨界面顶层悬浮。
2. **手势识别系统**：区分单击互动、双击缩放体型以及长按拖拽位置。
3. **轻量逐帧状态机**：支持待机循环、呼吸、随机事件（眨眼/抱猫/喝水）、拖拽态与超时入睡机制。
