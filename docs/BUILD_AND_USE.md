# Pocket HID

Pocket HID 把 Android 12+ 手机变成 Windows 的蓝牙键盘和触控板。它使用 Android 系统的 **Bluetooth HID Device API**，不依赖 Wi-Fi、热点或 Windows 端软件。

首版针对 vivo X100 Pro / OriginOS 的横屏桌面使用场景设计，包含：

- HID 键盘 + 鼠标组合设备注册、配对引导、连接、断开和状态显示；
- 手机系统输入法文本框，发送英文、数字和常用 ASCII 符号；
- 横屏全键盘模式：主键区、F1–F12、编辑/方向区和数字小键盘完整呈现，不需要横向滚动；
- 手机键盘模式：按成熟 Android 键盘比例重新设计，采用 10 键主行、半键错位、加宽 Shift/退格和大空格键；支持独立符号页与大按键数字页；
- 手机键盘宽度提供“紧凑 / 标准 / 宽”三档，普通模式和全屏模式同步生效；
- 全键盘可在设置中分别显示/隐藏数字小键盘和方向/编辑键区；
- 右上角可进入沉浸式全屏键盘，隐藏标题栏、标签栏及系统状态/导航栏，返回键退出；
- 按键视觉反馈支持“简洁/明显”两档，按键震动可独立开关并调节强度；
- Ctrl / Alt / Shift / Win、Esc、Tab、Enter、Backspace、方向键、Home、End、PageUp、PageDown 等功能键；
- 触控板移动、轻点、双击按钮、左/中/右键、双指滚动；
- Ctrl+C/V/X/Z/A/S、Alt+Tab、Win+D 等快捷键；
- Android 12+ “附近的设备”权限和 Android 14+ connectedDevice 前台服务；
- 应用内诊断日志与适合 OriginOS 的故障说明。

连接成功后，连接管理卡片会从主界面自动收起，让键盘占用完整横屏空间。需要断开、切换电脑或重新配对时，点右上角 **设置** 即可重新打开连接管理。选择“手机键盘”标签后再点 **全屏**，会进入只显示手机键盘的沉浸布局。0.4.0 起，手机键盘在普通与全屏模式中都固定在底部居中并限制最大宽高，避免横屏时按键被拉得过宽或过高。

键盘布局、视觉反馈和震动设置会保存在手机本地，下次启动继续使用。0.3.2 起按照 Android 官方虚拟键盘方案，在手指按下时调用系统 `VIRTUAL_KEY` 触感，让 OriginOS 选择适配 X 轴线性马达的效果；系统不接受时才使用预定义 `EFFECT_CLICK` 或一次性振动。设置页会检测系统触感总开关，关闭时提示前往 **设置 → 声音与振动 → 触感反馈** 开启。应用强度滑块主要控制备用振动，系统虚拟键触感的最终强度由 OriginOS 控制。

## 环境要求

- Android Studio（带 JDK 17）
- Android SDK Platform 35
- 目标手机：Android 12 或更高版本
- Windows 10/11 电脑，带蓝牙

应用最低版本设为 API 31（Android 12），编译/目标 API 为 35。

## 编译

1. 用 Android Studio 打开本目录 `PocketHID`。
2. 等待 Gradle 同步；首次同步需要联网下载 Gradle 和 AndroidX 依赖。
3. 确认 SDK Manager 已安装 Android SDK Platform 35。
4. 菜单选择 **Build → Build APK(s)**。
5. 产物位于 `app/build/outputs/apk/debug/app-debug.apk`。

也可在项目根目录执行：

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

## 安装

通过 Android Studio 的 Run 按钮安装，或开启手机 USB 调试后执行：

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

首次启动时允许“附近的设备”。通知权限可以拒绝，但系统仍可能在任务管理界面显示前台服务；允许通知更方便确认 HID 会话仍在运行。

## 与 Windows 配对

1. 打开 Pocket HID，允许附近设备权限并开启蓝牙。
2. 点 **让 Windows 发现本机**，在系统弹窗中允许 300 秒可发现。
3. Windows 打开 **设置 → 蓝牙和设备 → 添加设备 → 蓝牙**。
4. 选择手机名称，确认手机和电脑显示的配对码一致。
5. 回到 Pocket HID，点 **刷新**，在已配对设备列表中找到电脑并点 **连接**。
6. 状态变为“已连接”后即可使用键盘、触控板和快捷键。

Windows 有时会为组合 HID 显示多个输入设备条目，这是正常现象。若连接后没有输入，请先在 Windows 删除手机配对记录、在手机删除电脑配对记录，再重新完成一次配对。

## 中文输入：可用方案与限制

标准蓝牙 HID 发送的是“物理键盘按键码”，不是 Unicode 文本。因此：

- **推荐方案**：Windows 切换到微软拼音等中文输入法；手机系统键盘切到英文键盘；在 Pocket HID 文本框输入拼音并发送，然后使用 Space、数字键或 Enter 在 Windows 端选词。
- 文本框会调用手机系统输入法，但首版只会发送可映射到美式键盘布局的 ASCII 字符。手机输入法已经组成的汉字、表情等会被跳过并给出提示。
- **“粘贴 Ctrl+V”只操作 Windows 自己的剪贴板**。标准 HID 没有读取或写入 Windows 剪贴板的能力，也不能把手机剪贴板传到电脑。
- 若需要把手机上已经组成的任意中文直接发送或同步剪贴板，必须增加 Windows 端程序、使用系统厂商提供的跨设备剪贴板，或改用非 HID 的通信通道；这与“不安装 Windows 端软件”的目标冲突。
- 符号映射按 Windows **English (US)** 键盘布局定义。其他物理键盘布局下，部分标点可能不同。

## OriginOS 建议

Bluetooth HID Device 是 Android 公共 API，但具体固件必须提供该 profile。若提示 HID 注册失败：

1. 关闭 Linkpad、蓝牙遥控器等其他会注册 HID 的应用；系统同一时间只允许一个 HID Device 应用注册。
2. 在 OriginOS 的应用权限中允许“附近的设备”，并把电池设置改为允许后台高耗电/后台运行。
3. 保持 Pocket HID 在前台，点“重新注册 HID”。
4. 打开右上角虫子图标查看并复制日志。
5. 如果日志持续显示系统未提供 HID Device profile，说明当前 OriginOS 固件可能禁用了该能力；普通应用无法绕过。

## 隐私

应用不申请网络权限，不上传数据，不读取手机剪贴板。输入内容只在内存中转换为 HID 报告并发给当前连接的蓝牙主机。诊断日志仅保留在应用进程内，最多 250 行。

## 项目结构

- `hid/HidService.kt`：前台服务、HID 注册/连接、报告发送和日志。
- `hid/HidDescriptor.kt`：键盘 + 鼠标组合 HID report descriptor。
- `hid/KeyboardCodes.kt`：按键码与 ASCII 映射。
- `ui/PocketHidScreen.kt`：横屏 Compose 界面、虚拟键盘、触控板和快捷键。
- `src/test`：ASCII 映射和 HID descriptor 基础单元测试。

## 已知限制

- 受 Android/厂商蓝牙栈限制，无法保证所有 OriginOS 版本都开放 HID Device profile。
- 无 Windows 端程序时，无法直接发送 Unicode 中文或同步剪贴板。
- 鼠标移动报告使用 8 位相对坐标；应用会自动拆分较大位移。
- 首版不保存多台电脑的自动重连偏好，重启应用后需从已配对列表再次点连接。
- 首版强制横屏，适合手机平放当键盘/触控板使用。
