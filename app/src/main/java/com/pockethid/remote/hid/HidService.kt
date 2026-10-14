package com.pockethid.remote.hid

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.pockethid.remote.MainActivity
import com.pockethid.remote.R
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class HidService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val reportMutex = Mutex()
    private var adapter: BluetoothAdapter? = null
    private var hidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null
    private var mouseButtons = 0
    private var shuttingDown = false

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            hidDevice = proxy as? BluetoothHidDevice
            if (hidDevice == null) {
                fail("系统返回了无效的 HID Device 服务。")
                return
            }
            addLog("已取得系统 HID Device 服务")
            registerHid()
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            hidDevice = null
            connectedDevice = null
            state.update {
                it.copy(
                    phase = HidPhase.ERROR,
                    registered = false,
                    connectedHost = null,
                    message = "系统 HID 服务已断开，请点“重新注册”重试。",
                )
            }
            addLog("系统 HID Device 服务断开")
            updateNotification()
        }
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            if (shuttingDown) return
            if (registered) {
                state.update {
                    it.copy(
                        phase = HidPhase.READY,
                        registered = true,
                        profileSupported = true,
                        message = "HID 已就绪，请在 Windows 蓝牙设置中配对后连接。",
                    )
                }
                addLog("HID 应用注册成功")
                refreshPairedHosts()
            } else {
                connectedDevice = null
                state.update {
                    it.copy(
                        phase = HidPhase.ERROR,
                        registered = false,
                        connectedHost = null,
                        message = "HID 注册被系统拒绝或撤销。请保持应用在前台并查看日志；部分 OriginOS 版本可能不开放该能力。",
                    )
                }
                addLog("HID 应用注册失败或被系统撤销")
            }
            updateNotification()
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, newState: Int) {
            val host = device.toHostDevice()
            when (newState) {
                BluetoothProfile.STATE_CONNECTING -> {
                    state.update { it.copy(phase = HidPhase.CONNECTING, message = "正在连接 ${host.name}…") }
                    addLog("正在连接 ${host.name} (${host.address})")
                }
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevice = device
                    state.update {
                        it.copy(
                            phase = HidPhase.CONNECTED,
                            connectedHost = host,
                            message = "已连接，可以使用键盘和触控板。",
                        )
                    }
                    addLog("已连接 ${host.name} (${host.address})")
                }
                BluetoothProfile.STATE_DISCONNECTING -> {
                    state.update { it.copy(phase = HidPhase.CONNECTING, message = "正在断开连接…") }
                    addLog("正在断开 ${host.name}")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (connectedDevice?.address == device.address) connectedDevice = null
                    state.update {
                        it.copy(
                            phase = if (it.registered) HidPhase.READY else HidPhase.IDLE,
                            connectedHost = null,
                            message = "已断开，可重新选择电脑连接。",
                        )
                    }
                    addLog("已断开 ${host.name}")
                }
            }
            updateNotification()
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            if (!hasConnectPermission()) return
            val payload = when (id.toInt()) {
                HidDescriptor.KEYBOARD_REPORT_ID -> ByteArray(8)
                HidDescriptor.MOUSE_REPORT_ID -> ByteArray(4)
                else -> null
            }
            try {
                if (payload == null) {
                    hidDevice?.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID)
                } else {
                    hidDevice?.replyReport(device, type, id, payload)
                }
            } catch (_: SecurityException) {
                addLog("回复主机报告时蓝牙权限被撤销")
            }
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            // Windows sends keyboard LED state here. The phone has no LED to update.
            if (!hasConnectPermission()) return
            try {
                hidDevice?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
            } catch (_: SecurityException) {
                addLog("确认主机输出报告时蓝牙权限被撤销")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startAsForeground()

        state.value = HidState(phase = HidPhase.STARTING, message = "正在启动蓝牙 HID…")
        addLog("服务启动，Android ${android.os.Build.VERSION.RELEASE} (${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL})")

        if (!hasConnectPermission()) {
            fail("尚未授予“附近的设备”权限。请返回界面授权后重试。")
            return
        }

        val manager = getSystemService(BluetoothManager::class.java)
        adapter = manager?.adapter
        val bluetoothAdapter = adapter
        if (bluetoothAdapter == null) {
            state.update { it.copy(bluetoothAvailable = false, message = "此设备没有可用的蓝牙适配器。") }
            fail("未检测到蓝牙适配器。")
            return
        }

        state.update { it.copy(bluetoothEnabled = bluetoothAdapter.isEnabled) }
        if (!bluetoothAdapter.isEnabled) {
            fail("蓝牙尚未开启。请开启蓝牙后点“重新注册”。")
            return
        }

        state.update { it.copy(phase = HidPhase.REGISTERING, message = "正在获取系统 HID 服务…") }
        val requested = bluetoothAdapter.getProfileProxy(this, profileListener, BluetoothProfile.HID_DEVICE)
        if (!requested) {
            state.update { it.copy(profileSupported = false) }
            fail("系统未提供 Bluetooth HID Device profile。此 OriginOS 固件可能不支持手机模拟键鼠。")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        shuttingDown = true
        try {
            connectedDevice?.let { hidDevice?.disconnect(it) }
            hidDevice?.unregisterApp()
            hidDevice?.let { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) }
        } catch (_: SecurityException) {
            // Permission may have been revoked while the service was active.
        }
        hidDevice = null
        connectedDevice = null
        instance = null
        serviceScope.cancel()
        state.value = HidState(message = "服务已停止")
        addLog("服务停止")
        super.onDestroy()
    }

    fun registerHid() {
        if (!hasConnectPermission()) {
            fail("缺少“附近的设备”权限，无法注册 HID。")
            return
        }
        val bluetoothAdapter = adapter
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            fail("蓝牙不可用或尚未开启。")
            return
        }
        val profile = hidDevice
        if (profile == null) {
            state.update { it.copy(phase = HidPhase.REGISTERING, message = "正在重新获取 HID 服务…") }
            val requested = bluetoothAdapter.getProfileProxy(this, profileListener, BluetoothProfile.HID_DEVICE)
            if (!requested) fail("无法获取 HID Device profile。")
            return
        }
        state.update { it.copy(phase = HidPhase.REGISTERING, message = "正在注册键盘与鼠标…") }
        val settings = BluetoothHidDeviceAppSdpSettings(
            "Pocket HID",
            "Android 蓝牙键盘与触控板",
            "PocketHID",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            HidDescriptor.bytes,
        )
        val accepted = try {
            profile.registerApp(settings, null, null, mainExecutor, callback)
        } catch (e: SecurityException) {
            fail("系统拒绝 HID 注册：${e.message ?: "权限不足"}")
            false
        } catch (e: RuntimeException) {
            fail("HID 注册异常：${e.message ?: e.javaClass.simpleName}")
            false
        }
        if (!accepted) {
            fail("HID 注册请求未被系统接受。可能已有另一个 HID 应用正在运行。")
        } else {
            addLog("HID 注册请求已提交，等待系统回调")
        }
    }

    fun refreshPairedHosts() {
        if (!hasConnectPermission()) return
        try {
            val devices = adapter?.bondedDevices.orEmpty()
                .map { it.toHostDevice() }
                .sortedBy { it.name.lowercase() }
            state.update {
                it.copy(
                    bluetoothEnabled = adapter?.isEnabled == true,
                    pairedHosts = devices,
                )
            }
            addLog("已刷新配对设备：${devices.size} 个")
        } catch (e: SecurityException) {
            fail("读取已配对设备失败：请重新授予“附近的设备”权限。")
        }
    }

    fun connect(address: String) {
        if (!hasConnectPermission()) {
            fail("没有蓝牙连接权限。")
            return
        }
        val profile = hidDevice
        if (profile == null || !state.value.registered) {
            fail("HID 尚未就绪，请先重新注册。")
            return
        }
        val device = try {
            adapter?.getRemoteDevice(address)
        } catch (_: IllegalArgumentException) {
            null
        }
        if (device == null) {
            fail("找不到所选蓝牙设备。")
            return
        }
        state.update { it.copy(phase = HidPhase.CONNECTING, message = "正在请求连接 ${device.safeName()}…") }
        val accepted = try {
            profile.connect(device)
        } catch (e: SecurityException) {
            fail("连接请求被系统拒绝：${e.message ?: "权限不足"}")
            false
        }
        if (!accepted) fail("连接请求未被系统接受。请确认 Windows 端已完成配对。")
    }

    fun disconnect() {
        val device = connectedDevice ?: return
        try {
            hidDevice?.disconnect(device)
        } catch (e: SecurityException) {
            fail("断开连接失败：${e.message ?: "权限不足"}")
        }
    }

    fun sendKey(keyCode: Int, modifiers: Int = 0): Boolean {
        if (!state.value.canSend) return false
        serviceScope.launch {
            reportMutex.withLock {
                sendKeyboardReport(modifiers, keyCode)
                delay(REPORT_DELAY_MS)
                sendKeyboardReport(0, 0)
            }
        }
        return true
    }

    /** Returns the number of characters that cannot be represented as US-layout HID keys. */
    fun sendText(text: String): Int {
        val strokes = text.mapNotNull { AsciiHidMapper.strokeFor(it) }
        val unsupported = text.length - strokes.size
        if (!state.value.canSend || strokes.isEmpty()) return text.length
        serviceScope.launch {
            reportMutex.withLock {
                for (stroke in strokes) {
                    sendKeyboardReport(stroke.modifiers, stroke.keyCode)
                    delay(REPORT_DELAY_MS)
                    sendKeyboardReport(0, 0)
                    delay(REPORT_DELAY_MS)
                }
            }
        }
        if (unsupported > 0) addLog("文本中有 $unsupported 个非 ASCII 字符未发送")
        return unsupported
    }

    fun mouseMove(dx: Int, dy: Int) {
        if (!state.value.canSend || (dx == 0 && dy == 0)) return
        var leftX = dx
        var leftY = dy
        while (leftX != 0 || leftY != 0) {
            val stepX = leftX.coerceIn(-127, 127)
            val stepY = leftY.coerceIn(-127, 127)
            sendMouseReport(mouseButtons, stepX, stepY, 0)
            leftX -= stepX
            leftY -= stepY
        }
    }

    fun mouseButton(button: Int, pressed: Boolean) {
        if (!state.value.canSend || button !in 1..3) return
        val mask = 1 shl (button - 1)
        mouseButtons = if (pressed) mouseButtons or mask else mouseButtons and mask.inv()
        sendMouseReport(mouseButtons, 0, 0, 0)
    }

    fun mouseClick(button: Int, double: Boolean = false) {
        if (!state.value.canSend || button !in 1..3) return
        serviceScope.launch {
            reportMutex.withLock {
                val repeats = if (double) 2 else 1
                repeat(repeats) {
                    val mask = 1 shl (button - 1)
                    sendMouseReport(mouseButtons or mask, 0, 0, 0)
                    delay(REPORT_DELAY_MS)
                    sendMouseReport(mouseButtons and mask.inv(), 0, 0, 0)
                    if (double) delay(80)
                }
            }
        }
    }

    fun mouseWheel(amount: Int) {
        if (!state.value.canSend || amount == 0) return
        var remaining = amount
        while (remaining != 0) {
            val step = remaining.coerceIn(-127, 127)
            sendMouseReport(mouseButtons, 0, 0, step)
            remaining -= step
        }
    }

    private fun sendKeyboardReport(modifiers: Int, keyCode: Int): Boolean {
        val device = connectedDevice ?: return false
        val report = ByteArray(8)
        report[0] = modifiers.toByte()
        report[2] = keyCode.toByte()
        return try {
            hidDevice?.sendReport(device, HidDescriptor.KEYBOARD_REPORT_ID, report) == true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun sendMouseReport(buttons: Int, dx: Int, dy: Int, wheel: Int): Boolean {
        val device = connectedDevice ?: return false
        val report = byteArrayOf(buttons.toByte(), dx.toByte(), dy.toByte(), wheel.toByte())
        return try {
            hidDevice?.sendReport(device, HidDescriptor.MOUSE_REPORT_ID, report) == true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun hasConnectPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun BluetoothDevice.safeName(): String = try {
        name?.takeIf { it.isNotBlank() } ?: "未命名设备"
    } catch (_: SecurityException) {
        "未命名设备"
    }

    private fun BluetoothDevice.toHostDevice() = HostDevice(safeName(), address)

    private fun fail(message: String) {
        state.update { it.copy(phase = HidPhase.ERROR, message = message) }
        addLog("错误：$message")
        updateNotification()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun startAsForeground() {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val content = state.value.connectedHost?.let {
            getString(R.string.notification_connected, it.name)
        } ?: getString(R.string.notification_waiting)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(content)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "hid_session"
        private const val NOTIFICATION_ID = 1101
        private const val REPORT_DELAY_MS = 12L
        private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

        @Volatile
        internal var instance: HidService? = null

        internal val state = MutableStateFlow(HidState())
        internal val logs = MutableStateFlow<List<String>>(emptyList())

        internal fun addLog(message: String) {
            val line = "${LocalTime.now().format(timeFormatter)}  $message"
            logs.update { (it + line).takeLast(250) }
        }

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, HidService::class.java))
        }

    }
}
