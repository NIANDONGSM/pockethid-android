# PocketHID · Android蓝牙键盘与触控板

**把手机变成电脑的蓝牙输入设备，探索横屏键盘与触控交互。**

| 项目 | 信息 |
| --- | --- |
| 类型 / 时间 | 个人Android项目，2026年 |
| 我的职责 | 使用场景、交互布局、功能需求与迭代反馈 |
| 技术 | Kotlin / Jetpack Compose / Android Bluetooth HID Device API |
| 当前版本 | 0.4.0；已有APK构建记录，实机配对验证待完成 |

开发过程有 AI 辅助参与代码实现与界面迭代。

## 功能与交互

- 蓝牙HID注册、配对引导、连接状态及前台服务。
- 横屏全键盘与手机键盘两种布局，支持数字页、符号页与功能键。
- Ctrl / Alt / Shift / Win组合键、常用快捷键及触控板操作。
- 紧凑/标准/宽三档布局、沉浸全屏、按键反馈与本地设置保存。
- ASCII文本到HID报告转换；中文可通过电脑端拼音输入法组合，不能直接传输任意Unicode文本。

## 我的实践

围绕手机横屏操控电脑的需求梳理键盘与触控场景，迭代按键比例、错位布局、全屏与反馈设置，根据界面体验推进调整，形成源码、构建产物与使用说明。

## 构建与阅读

使用Android Studio打开仓库，准备JDK17和Android SDK35，然后运行：

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

- `app/src/main/.../hid/`：设备连接、报告描述与按键映射。
- `app/src/main/.../ui/`：Compose键盘与触控界面。
- `app/src/test/`：ASCII映射及HID描述测试。
- [构建与使用指南](docs/BUILD_AND_USE.md)：安装、配对、输入限制与诊断。
- [验证说明](docs/VALIDATION.md)：此次检查范围。

不将APK构建成功表述为已通过所有手机与电脑的配对测试。
