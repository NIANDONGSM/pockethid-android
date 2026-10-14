package com.pockethid.remote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.app.Activity
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pockethid.remote.hid.AsciiHidMapper
import com.pockethid.remote.hid.HidHub
import com.pockethid.remote.hid.HidPhase
import com.pockethid.remote.hid.HidState
import com.pockethid.remote.hid.HostDevice
import com.pockethid.remote.hid.KeyCodes
import com.pockethid.remote.hid.Modifiers
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private enum class MainTab(val title: String) {
    FULL_KEYBOARD("全键盘"),
    MOBILE_KEYBOARD("手机键盘"),
    KEYBOARD("输入 / 功能键"),
    TOUCHPAD("触控板"),
    SHORTCUTS("快捷键"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PocketHidScreen(
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onMakeDiscoverable: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onRestartService: () -> Unit,
) {
    val state by HidHub.state.collectAsStateWithLifecycle()
    val logs by HidHub.logs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val rootView = LocalView.current
    val preferencesStore = remember { UiPreferencesStore(context) }
    val feedbackPlayer = remember { KeyFeedbackPlayer(context) }
    var uiPreferences by remember { mutableStateOf(preferencesStore.load()) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var fullscreenKeyboard by rememberSaveable { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    fun updateUiPreferences(value: UiPreferences) {
        uiPreferences = value
        preferencesStore.save(value)
    }

    BackHandler(enabled = fullscreenKeyboard) { fullscreenKeyboard = false }
    FullscreenSystemBarsEffect(enabled = fullscreenKeyboard)

    if (fullscreenKeyboard) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(4.dp),
        ) {
            if (MainTab.entries.getOrNull(selectedTab) == MainTab.MOBILE_KEYBOARD) {
                MobileKeyboardPanel(
                    enabled = state.canSend,
                    preferences = uiPreferences,
                    onKeyFeedback = { feedbackPlayer.perform(uiPreferences, rootView) },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                FullKeyboardPanel(
                    enabled = state.canSend,
                    preferences = uiPreferences,
                    onKeyFeedback = { feedbackPlayer.perform(uiPreferences, rootView) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        return
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("⌨", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Pocket HID", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                StatusPill(state)
                Spacer(Modifier.weight(1f))
                TextButton(
                    enabled = state.canSend,
                    onClick = {
                        if (MainTab.entries.getOrNull(selectedTab) != MainTab.MOBILE_KEYBOARD) {
                            selectedTab = MainTab.FULL_KEYBOARD.ordinal
                        }
                        fullscreenKeyboard = true
                    },
                ) { Text("全屏") }
                TextButton(onClick = { showSettings = true }) { Text("设置") }
                TextButton(onClick = { showLogs = true }) { Text("日志") }
                TextButton(onClick = { showHelp = true }) { Text("帮助") }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!state.canSend) {
                    ConnectionPane(
                        state = state,
                        permissionsGranted = permissionsGranted,
                        onRequestPermissions = onRequestPermissions,
                        onEnableBluetooth = onEnableBluetooth,
                        onMakeDiscoverable = onMakeDiscoverable,
                        onOpenBluetoothSettings = onOpenBluetoothSettings,
                        onRestartService = onRestartService,
                        modifier = Modifier.width(300.dp).fillMaxHeight(),
                    )
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    TabRow(selectedTabIndex = selectedTab) {
                        MainTab.entries.forEachIndexed { index, tab ->
                            Tab(
                                selected = index == selectedTab,
                                onClick = { selectedTab = index },
                                text = { Text(tab.title) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    when (MainTab.entries[selectedTab]) {
                        MainTab.FULL_KEYBOARD -> FullKeyboardPanel(
                            enabled = state.canSend,
                            preferences = uiPreferences,
                            onKeyFeedback = { feedbackPlayer.perform(uiPreferences, rootView) },
                            modifier = Modifier.fillMaxSize(),
                        )
                        MainTab.MOBILE_KEYBOARD -> MobileKeyboardPanel(
                            enabled = state.canSend,
                            preferences = uiPreferences,
                            onKeyFeedback = { feedbackPlayer.perform(uiPreferences, rootView) },
                            modifier = Modifier.fillMaxSize(),
                        )
                        MainTab.KEYBOARD -> KeyboardPanel(
                            enabled = state.canSend,
                            snackbar = snackbar,
                            onKeyFeedback = { feedbackPlayer.perform(uiPreferences, rootView) },
                            modifier = Modifier.fillMaxSize(),
                        )
                        MainTab.TOUCHPAD -> TouchpadPanel(
                            enabled = state.canSend,
                            modifier = Modifier.fillMaxSize(),
                        )
                        MainTab.SHORTCUTS -> ShortcutsPanel(
                            enabled = state.canSend,
                            onKeyFeedback = { feedbackPlayer.perform(uiPreferences, rootView) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            state = state,
            permissionsGranted = permissionsGranted,
            onRequestPermissions = onRequestPermissions,
            onEnableBluetooth = onEnableBluetooth,
            onMakeDiscoverable = onMakeDiscoverable,
            onOpenBluetoothSettings = onOpenBluetoothSettings,
            onRestartService = onRestartService,
            uiPreferences = uiPreferences,
            onUiPreferencesChange = ::updateUiPreferences,
            onTestHaptic = { feedbackPlayer.perform(uiPreferences, rootView) },
            systemTouchHapticsEnabled = feedbackPlayer.isSystemTouchFeedbackEnabled(),
            onDismiss = { showSettings = false },
        )
    }
    if (showLogs) {
        LogDialog(logs = logs, onDismiss = { showLogs = false })
    }
    if (showHelp) {
        HelpDialog(onDismiss = { showHelp = false })
    }
}

@Composable
private fun FullscreenSystemBarsEffect(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, view) }
        if (enabled) {
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (enabled) controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun StatusPill(state: HidState) {
    val color = when (state.phase) {
        HidPhase.CONNECTED -> Color(0xFF2E7D32)
        HidPhase.ERROR -> MaterialTheme.colorScheme.error
        HidPhase.CONNECTING, HidPhase.REGISTERING, HidPhase.STARTING -> Color(0xFFEF6C00)
        else -> MaterialTheme.colorScheme.secondary
    }
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(50))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(50)))
        Spacer(Modifier.width(6.dp))
        Text(
            when (state.phase) {
                HidPhase.CONNECTED -> "已连接"
                HidPhase.READY -> "已就绪"
                HidPhase.ERROR -> "需要处理"
                HidPhase.CONNECTING -> "连接中"
                HidPhase.REGISTERING, HidPhase.STARTING -> "启动中"
                HidPhase.IDLE -> "未启动"
            },
            color = color,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun ConnectionPane(
    state: HidState,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onMakeDiscoverable: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onRestartService: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("连接", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(state.message, style = MaterialTheme.typography.bodySmall)
            state.connectedHost?.let {
                Text("${it.name}\n${it.address}", color = MaterialTheme.colorScheme.primary)
                Button(onClick = { HidHub.disconnect() }, modifier = Modifier.fillMaxWidth()) { Text("断开") }
            }

            if (!permissionsGranted) {
                Button(onClick = onRequestPermissions, modifier = Modifier.fillMaxWidth()) { Text("授予附近设备权限") }
            }
            if (!state.bluetoothEnabled) {
                Button(onClick = onEnableBluetooth, modifier = Modifier.fillMaxWidth()) { Text("开启蓝牙") }
            }
            if (state.phase == HidPhase.ERROR) {
                OutlinedButton(onClick = onRestartService, modifier = Modifier.fillMaxWidth()) { Text("重新注册 HID") }
            }
            OutlinedButton(onClick = onMakeDiscoverable, modifier = Modifier.fillMaxWidth()) {
                Text("让 Windows 发现本机")
            }
            TextButton(onClick = onOpenBluetoothSettings, modifier = Modifier.fillMaxWidth()) {
                Text("打开系统蓝牙设置")
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("已配对设备", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { HidHub.refreshPairedHosts() }) { Text("刷新") }
            }
            if (state.pairedHosts.isEmpty()) {
                Text("暂无设备。先让手机可被发现，再从 Windows“添加设备”完成配对。", style = MaterialTheme.typography.bodySmall)
            }
            state.pairedHosts.forEach { host ->
                DeviceRow(host = host, enabled = state.registered && state.connectedHost == null)
            }
        }
    }
}

@Composable
private fun DeviceRow(host: HostDevice, enabled: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(host.name, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
            Text(host.address, style = MaterialTheme.typography.labelSmall)
        }
        TextButton(onClick = { HidHub.connect(host.address) }, enabled = enabled) { Text("连接") }
    }
}

@Composable
private fun SettingsDialog(
    state: HidState,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onMakeDiscoverable: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onRestartService: () -> Unit,
    uiPreferences: UiPreferences,
    onUiPreferencesChange: (UiPreferences) -> Unit,
    onTestHaptic: () -> Boolean,
    systemTouchHapticsEnabled: Boolean,
    onDismiss: () -> Unit,
) {
    var hapticTestStatus by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.92f)
                .widthIn(max = 1180.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("设置与连接", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("完成") }
            }
            Text(
                "键盘布局与反馈设置会自动保存；右侧仍可管理蓝牙连接。",
                modifier = Modifier.padding(horizontal = 18.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxSize().padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Card(
                    modifier = Modifier.weight(0.46f).fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("键盘布局", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        SettingSwitchRow(
                            title = "显示数字小键盘",
                            subtitle = "隐藏后主键区会自动扩展",
                            checked = uiPreferences.showNumpad,
                            onCheckedChange = { onUiPreferencesChange(uiPreferences.copy(showNumpad = it)) },
                        )
                        SettingSwitchRow(
                            title = "显示方向 / 编辑键区",
                            subtitle = "包括方向键、Insert、Home、Page 等",
                            checked = uiPreferences.showNavigation,
                            onCheckedChange = { onUiPreferencesChange(uiPreferences.copy(showNavigation = it)) },
                        )
                        Text("手机键盘宽度", style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(80 to "紧凑", 88 to "标准", 96 to "宽").forEach { (width, label) ->
                                FilterChip(
                                    selected = uiPreferences.mobileKeyboardWidthPercent == width,
                                    onClick = {
                                        onUiPreferencesChange(
                                            uiPreferences.copy(mobileKeyboardWidthPercent = width),
                                        )
                                    },
                                    label = { Text(label) },
                                )
                            }
                        }
                        Text(
                            "横屏够不到边缘时选择“紧凑”；普通与全屏模式同时生效。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        HorizontalDivider()
                        Text("按键视觉反馈", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = uiPreferences.keyFeedbackStyle == KeyFeedbackStyle.SIMPLE,
                                onClick = {
                                    onUiPreferencesChange(uiPreferences.copy(keyFeedbackStyle = KeyFeedbackStyle.SIMPLE))
                                },
                                label = { Text("简洁") },
                            )
                            FilterChip(
                                selected = uiPreferences.keyFeedbackStyle == KeyFeedbackStyle.OBVIOUS,
                                onClick = {
                                    onUiPreferencesChange(uiPreferences.copy(keyFeedbackStyle = KeyFeedbackStyle.OBVIOUS))
                                },
                                label = { Text("明显") },
                            )
                        }
                        Text(
                            if (uiPreferences.keyFeedbackStyle == KeyFeedbackStyle.OBVIOUS) {
                                "按下时按键会缩小并变为主题色。"
                            } else {
                                "保留标准轻量涟漪反馈。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        HorizontalDivider()
                        SettingSwitchRow(
                            title = "按键震动",
                            subtitle = "键盘及快捷键发送时振动",
                            checked = uiPreferences.hapticsEnabled,
                            onCheckedChange = { onUiPreferencesChange(uiPreferences.copy(hapticsEnabled = it)) },
                        )
                        if (!systemTouchHapticsEnabled) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            ) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("OriginOS 系统触感反馈当前为关闭", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "请在“声音与振动 → 触感反馈”中开启；这是 vivo 官方给出的检查路径。",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    TextButton(onClick = {
                                        context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
                                    }) { Text("打开声音与振动设置") }
                                }
                            }
                        }
                        if (uiPreferences.hapticsEnabled) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("弱", style = MaterialTheme.typography.labelMedium)
                                Slider(
                                    value = uiPreferences.hapticStrength.toFloat(),
                                    onValueChange = {
                                        onUiPreferencesChange(uiPreferences.copy(hapticStrength = it.roundToInt()))
                                    },
                                    valueRange = 1f..100f,
                                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                )
                                Text("强", style = MaterialTheme.typography.labelMedium)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("强度 ${uiPreferences.hapticStrength}%", style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.weight(1f))
                                OutlinedButton(onClick = {
                                    hapticTestStatus = if (onTestHaptic()) {
                                        "已向系统发送震动"
                                    } else {
                                        "系统未检测到可用振动器"
                                    }
                                }) { Text("试震") }
                            }
                            hapticTestStatus?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Text(
                                "若仍无体感，请把强度调高，并检查 OriginOS 的触感反馈、静音与省电设置。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }
                ConnectionPane(
                    state = state,
                    permissionsGranted = permissionsGranted,
                    onRequestPermissions = onRequestPermissions,
                    onEnableBluetooth = onEnableBluetooth,
                    onMakeDiscoverable = onMakeDiscoverable,
                    onOpenBluetoothSettings = onOpenBluetoothSettings,
                    onRestartService = onRestartService,
                    modifier = Modifier.weight(0.54f).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private data class FullKey(
    val label: String,
    val keyCode: Int? = null,
    val modifierMask: Int = 0,
    val units: Float = 1f,
)

private enum class MobileKeyboardPage {
    LETTERS,
    SYMBOLS,
    NUMPAD,
}

private data class MobileKey(
    val label: String,
    val hint: String? = null,
    val keyCode: Int? = null,
    val sendModifiers: Int = 0,
    val modifierMask: Int = 0,
    val pageTarget: MobileKeyboardPage? = null,
    val units: Float = 1f,
)

private fun mobileCharacterKey(character: Char, units: Float = 1f): MobileKey {
    val stroke = AsciiHidMapper.strokeFor(character)
    return MobileKey(
        label = character.toString(),
        keyCode = stroke?.keyCode,
        sendModifiers = stroke?.modifiers ?: 0,
        units = units,
    )
}

@Composable
private fun MobileKeyboardPanel(
    enabled: Boolean,
    preferences: UiPreferences,
    onKeyFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var page by rememberSaveable { mutableStateOf(MobileKeyboardPage.LETTERS) }
    var activeModifiers by rememberSaveable { mutableIntStateOf(0) }

    fun press(key: MobileKey) {
        key.pageTarget?.let {
            page = it
            return
        }
        if (key.modifierMask != 0) {
            activeModifiers = if (activeModifiers and key.modifierMask != 0) {
                activeModifiers and key.modifierMask.inv()
            } else {
                activeModifiers or key.modifierMask
            }
            return
        }
        key.keyCode?.let {
            HidHub.sendKey(it, activeModifiers or key.sendModifiers)
            activeModifiers = 0
        }
    }

    val utilityRow = listOf(
        MobileKey("Esc", keyCode = KeyCodes.ESC),
        MobileKey("Tab", keyCode = KeyCodes.TAB),
        MobileKey("Ctrl", modifierMask = Modifiers.LEFT_CTRL),
        MobileKey("Alt", modifierMask = Modifiers.LEFT_ALT),
        MobileKey("Win", modifierMask = Modifiers.LEFT_GUI),
        MobileKey("←", keyCode = KeyCodes.ARROW_LEFT),
        MobileKey("↑", keyCode = KeyCodes.ARROW_UP),
        MobileKey("↓", keyCode = KeyCodes.ARROW_DOWN),
        MobileKey("→", keyCode = KeyCodes.ARROW_RIGHT),
    )

    val letterHints = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    val homeHints = listOf("~", "!", "@", "#", "%", "“", "”", "*", "?")
    val lowerHints = listOf("(", ")", "-", "_", ":", ";", "/")
    val commonBottomRow = listOf(
        MobileKey("ABC", pageTarget = MobileKeyboardPage.LETTERS, units = 1.15f),
        MobileKey("123", pageTarget = MobileKeyboardPage.NUMPAD, units = 1.15f),
        mobileCharacterKey(',', units = 0.85f),
        MobileKey("空格", keyCode = KeyCodes.SPACE, units = 4.3f),
        mobileCharacterKey('.', units = 0.85f),
        MobileKey("回车", keyCode = KeyCodes.ENTER, units = 1.55f),
    )

    val rows = when (page) {
        MobileKeyboardPage.LETTERS -> listOf(
            "qwertyuiop".mapIndexed { index, letter ->
                MobileKey(
                    label = letter.uppercase(),
                    hint = letterHints[index],
                    keyCode = KeyCodes.A + (letter - 'a'),
                )
            },
            listOf(MobileKey("", units = 0.48f)) +
                "asdfghjkl".mapIndexed { index, letter ->
                    MobileKey(
                        label = letter.uppercase(),
                        hint = homeHints[index],
                        keyCode = KeyCodes.A + (letter - 'a'),
                    )
                } + MobileKey("", units = 0.48f),
            listOf(MobileKey("⇧", modifierMask = Modifiers.LEFT_SHIFT, units = 1.4f)) +
                "zxcvbnm".mapIndexed { index, letter ->
                    MobileKey(
                        label = letter.uppercase(),
                        hint = lowerHints[index],
                        keyCode = KeyCodes.A + (letter - 'a'),
                    )
                } + MobileKey("⌫", keyCode = KeyCodes.BACKSPACE, units = 1.4f),
            listOf(
                MobileKey("符", pageTarget = MobileKeyboardPage.SYMBOLS, units = 1.15f),
                MobileKey("123", pageTarget = MobileKeyboardPage.NUMPAD, units = 1.15f),
                mobileCharacterKey(',', units = 0.85f),
                MobileKey("空格", keyCode = KeyCodes.SPACE, units = 4.3f),
                mobileCharacterKey('.', units = 0.85f),
                MobileKey("回车", keyCode = KeyCodes.ENTER, units = 1.55f),
            ),
        )
        MobileKeyboardPage.SYMBOLS -> listOf(
            "1234567890".map { mobileCharacterKey(it) },
            listOf('~', '/', ':', ';', '(', ')', '@', '"', '\'', '#').map { mobileCharacterKey(it) },
            listOf(
                mobileCharacterKey('-', units = 1.15f),
                mobileCharacterKey('_'),
                mobileCharacterKey('+'),
                mobileCharacterKey('='),
                mobileCharacterKey('\\'),
                mobileCharacterKey('|'),
                mobileCharacterKey('?'),
                mobileCharacterKey('!'),
                MobileKey("⌫", keyCode = KeyCodes.BACKSPACE, units = 1.45f),
            ),
            commonBottomRow,
        )
        MobileKeyboardPage.NUMPAD -> listOf(
            listOf(
                mobileCharacterKey('1', 1.25f), mobileCharacterKey('2', 1.25f), mobileCharacterKey('3', 1.25f),
                MobileKey("⌫", keyCode = KeyCodes.BACKSPACE, units = 1.25f),
            ),
            listOf(
                mobileCharacterKey('4', 1.25f), mobileCharacterKey('5', 1.25f), mobileCharacterKey('6', 1.25f),
                mobileCharacterKey('.', 1.25f),
            ),
            listOf(
                mobileCharacterKey('7', 1.25f), mobileCharacterKey('8', 1.25f), mobileCharacterKey('9', 1.25f),
                MobileKey("回车", keyCode = KeyCodes.ENTER, units = 1.25f),
            ),
            listOf(
                MobileKey("符", pageTarget = MobileKeyboardPage.SYMBOLS, units = 1.25f),
                MobileKey("ABC", pageTarget = MobileKeyboardPage.LETTERS, units = 1.25f),
                mobileCharacterKey('0', 2.5f),
            ),
        )
    }

    BoxWithConstraints(modifier, contentAlignment = Alignment.BottomCenter) {
        val keyboardWidth = minOf(
            maxWidth * (preferences.mobileKeyboardWidthPercent / 100f),
            920.dp,
        )
        val keyboardHeight = minOf(maxHeight, 300.dp)
        Column(
            modifier = Modifier
                .width(keyboardWidth)
                .height(keyboardHeight)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
                    RoundedCornerShape(18.dp),
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            MobileKeyboardRow(
                keys = utilityRow,
                enabled = enabled,
                activeModifiers = activeModifiers,
                feedbackStyle = preferences.keyFeedbackStyle,
                onKeyDownFeedback = onKeyFeedback,
                onPress = ::press,
                compact = true,
                modifier = Modifier.weight(0.72f),
            )
            rows.forEach { row ->
                MobileKeyboardRow(
                    keys = row,
                    enabled = enabled,
                    activeModifiers = activeModifiers,
                    feedbackStyle = preferences.keyFeedbackStyle,
                    onKeyDownFeedback = onKeyFeedback,
                    onPress = ::press,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MobileKeyboardRow(
    keys: List<MobileKey>,
    enabled: Boolean,
    activeModifiers: Int,
    feedbackStyle: KeyFeedbackStyle,
    onKeyDownFeedback: () -> Unit,
    onPress: (MobileKey) -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val indication = LocalIndication.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        keys.forEach { key ->
            if (key.keyCode == null && key.modifierMask == 0 && key.pageTarget == null) {
                Spacer(Modifier.weight(key.units).fillMaxHeight())
            } else {
                val selected = key.modifierMask != 0 && activeModifiers and key.modifierMask != 0
                val interactionSource = remember(key.label, key.hint, key.keyCode, key.modifierMask, key.pageTarget) {
                    MutableInteractionSource()
                }
                val pressed by interactionSource.collectIsPressedAsState()
                LaunchedEffect(pressed) {
                    if (pressed) onKeyDownFeedback()
                }
                val obviousPressed = pressed && feedbackStyle == KeyFeedbackStyle.OBVIOUS
                val scale by animateFloatAsState(
                    targetValue = if (obviousPressed) 0.91f else 1f,
                    label = "mobileKeyboardKeyScale",
                )
                val keyBackground = when {
                    obviousPressed -> MaterialTheme.colorScheme.primary
                    selected -> MaterialTheme.colorScheme.primaryContainer
                    key.pageTarget != null -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                Box(
                    modifier = Modifier
                        .weight(key.units)
                        .fillMaxHeight()
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .background(keyBackground, RoundedCornerShape(10.dp))
                        .border(
                            if (obviousPressed) 2.dp else 1.dp,
                            if (selected || obviousPressed) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                            RoundedCornerShape(10.dp),
                        )
                        .clickable(
                            enabled = enabled,
                            interactionSource = interactionSource,
                            indication = indication,
                        ) { onPress(key) },
                    contentAlignment = Alignment.Center,
                ) {
                    val contentColor = if (obviousPressed) MaterialTheme.colorScheme.onPrimary
                    else if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        key.hint?.let {
                            Text(
                                it,
                                maxLines = 1,
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor.copy(alpha = 0.58f),
                            )
                        }
                        Text(
                            key.label,
                            maxLines = 1,
                            style = if (compact) {
                                MaterialTheme.typography.labelLarge
                            } else {
                                MaterialTheme.typography.titleMedium
                            },
                            fontWeight = if (key.keyCode != null && key.label.length == 1) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            },
                            color = contentColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FullKeyboardPanel(
    enabled: Boolean,
    preferences: UiPreferences,
    onKeyFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeModifiers by rememberSaveable { mutableIntStateOf(0) }

    fun press(key: FullKey) {
        if (key.modifierMask != 0) {
            activeModifiers = if (activeModifiers and key.modifierMask != 0) {
                activeModifiers and key.modifierMask.inv()
            } else {
                activeModifiers or key.modifierMask
            }
        } else {
            key.keyCode?.let { HidHub.sendKey(it, activeModifiers) }
            activeModifiers = 0
        }
    }

    val functionRow = listOf(
        FullKey("Esc", KeyCodes.ESC, units = 1.15f), FullKey("", units = 0.35f),
        FullKey("F1", KeyCodes.F1), FullKey("F2", KeyCodes.F1 + 1), FullKey("F3", KeyCodes.F1 + 2), FullKey("F4", KeyCodes.F1 + 3),
        FullKey("", units = 0.35f),
        FullKey("F5", KeyCodes.F1 + 4), FullKey("F6", KeyCodes.F1 + 5), FullKey("F7", KeyCodes.F1 + 6), FullKey("F8", KeyCodes.F1 + 7),
        FullKey("", units = 0.35f),
        FullKey("F9", KeyCodes.F1 + 8), FullKey("F10", KeyCodes.F1 + 9), FullKey("F11", KeyCodes.F1 + 10), FullKey("F12", KeyCodes.F1 + 11),
        FullKey("Prt", KeyCodes.PRINT_SCREEN), FullKey("Scr", KeyCodes.SCROLL_LOCK), FullKey("Pause", KeyCodes.PAUSE, units = 1.2f),
    )
    val mainRows = listOf(
        listOf(
            FullKey("` ~", KeyCodes.GRAVE),
            FullKey("1", KeyCodes.DIGIT_1), FullKey("2", KeyCodes.DIGIT_1 + 1), FullKey("3", KeyCodes.DIGIT_1 + 2),
            FullKey("4", KeyCodes.DIGIT_1 + 3), FullKey("5", KeyCodes.DIGIT_1 + 4), FullKey("6", KeyCodes.DIGIT_1 + 5),
            FullKey("7", KeyCodes.DIGIT_1 + 6), FullKey("8", KeyCodes.DIGIT_1 + 7), FullKey("9", KeyCodes.DIGIT_1 + 8),
            FullKey("0", KeyCodes.DIGIT_1 + 9), FullKey("-", KeyCodes.MINUS), FullKey("=", KeyCodes.EQUALS),
            FullKey("Backspace", KeyCodes.BACKSPACE, units = 2.15f),
        ),
        listOf(
            FullKey("Tab", KeyCodes.TAB, units = 1.55f),
            FullKey("Q", KeyCodes.Q), FullKey("W", KeyCodes.W), FullKey("E", KeyCodes.E), FullKey("R", KeyCodes.R), FullKey("T", KeyCodes.T),
            FullKey("Y", KeyCodes.Y), FullKey("U", KeyCodes.U), FullKey("I", KeyCodes.I), FullKey("O", KeyCodes.O), FullKey("P", KeyCodes.P),
            FullKey("[", KeyCodes.LEFT_BRACKET), FullKey("]", KeyCodes.RIGHT_BRACKET), FullKey("\\", KeyCodes.BACKSLASH, units = 1.6f),
        ),
        listOf(
            FullKey("Caps", KeyCodes.CAPS_LOCK, units = 1.85f),
            FullKey("A", KeyCodes.A), FullKey("S", KeyCodes.S), FullKey("D", KeyCodes.D), FullKey("F", KeyCodes.F), FullKey("G", KeyCodes.G),
            FullKey("H", KeyCodes.H), FullKey("J", KeyCodes.J), FullKey("K", KeyCodes.K), FullKey("L", KeyCodes.L),
            FullKey(";", KeyCodes.SEMICOLON), FullKey("'", KeyCodes.APOSTROPHE), FullKey("Enter", KeyCodes.ENTER, units = 2.3f),
        ),
        listOf(
            FullKey("Shift", modifierMask = Modifiers.LEFT_SHIFT, units = 2.35f),
            FullKey("Z", KeyCodes.Z), FullKey("X", KeyCodes.X), FullKey("C", KeyCodes.C), FullKey("V", KeyCodes.V),
            FullKey("B", KeyCodes.B), FullKey("N", KeyCodes.N), FullKey("M", KeyCodes.M),
            FullKey(",", KeyCodes.COMMA), FullKey(".", KeyCodes.PERIOD), FullKey("/", KeyCodes.SLASH),
            FullKey("Shift", modifierMask = Modifiers.RIGHT_SHIFT, units = 2.8f),
        ),
        listOf(
            FullKey("Ctrl", modifierMask = Modifiers.LEFT_CTRL, units = 1.45f),
            FullKey("Win", modifierMask = Modifiers.LEFT_GUI, units = 1.35f),
            FullKey("Alt", modifierMask = Modifiers.LEFT_ALT, units = 1.35f),
            FullKey("Space", KeyCodes.SPACE, units = 6.2f),
            FullKey("Alt", modifierMask = Modifiers.RIGHT_ALT, units = 1.35f),
            FullKey("Win", modifierMask = Modifiers.RIGHT_GUI, units = 1.25f),
            FullKey("Menu", KeyCodes.APPLICATION, units = 1.35f),
            FullKey("Ctrl", modifierMask = Modifiers.RIGHT_CTRL, units = 1.45f),
        ),
    )
    val navigationRows = listOf(
        listOf(FullKey("Ins", KeyCodes.INSERT), FullKey("Home", KeyCodes.HOME), FullKey("Pg↑", KeyCodes.PAGE_UP)),
        listOf(FullKey("Del", KeyCodes.DELETE), FullKey("End", KeyCodes.END), FullKey("Pg↓", KeyCodes.PAGE_DOWN)),
        listOf(FullKey(""), FullKey("↑", KeyCodes.ARROW_UP), FullKey("")),
        listOf(FullKey("←", KeyCodes.ARROW_LEFT), FullKey("↓", KeyCodes.ARROW_DOWN), FullKey("→", KeyCodes.ARROW_RIGHT)),
        listOf(FullKey(""), FullKey(""), FullKey("")),
    )
    val numpadRows = listOf(
        listOf(FullKey("Num", KeyCodes.NUM_LOCK), FullKey("/", KeyCodes.KEYPAD_SLASH), FullKey("*", KeyCodes.KEYPAD_ASTERISK), FullKey("-", KeyCodes.KEYPAD_MINUS)),
        listOf(FullKey("7", KeyCodes.KEYPAD_7), FullKey("8", KeyCodes.KEYPAD_8), FullKey("9", KeyCodes.KEYPAD_9), FullKey("+", KeyCodes.KEYPAD_PLUS)),
        listOf(FullKey("4", KeyCodes.KEYPAD_4), FullKey("5", KeyCodes.KEYPAD_5), FullKey("6", KeyCodes.KEYPAD_6), FullKey("")),
        listOf(FullKey("1", KeyCodes.KEYPAD_1), FullKey("2", KeyCodes.KEYPAD_2), FullKey("3", KeyCodes.KEYPAD_3), FullKey("Enter", KeyCodes.KEYPAD_ENTER)),
        listOf(FullKey("0", KeyCodes.KEYPAD_0, units = 2f), FullKey(".", KeyCodes.KEYPAD_PERIOD), FullKey("")),
    )

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FullKeyboardRow(
            keys = functionRow,
            enabled = enabled,
            activeModifiers = activeModifiers,
            feedbackStyle = preferences.keyFeedbackStyle,
            onKeyDownFeedback = onKeyFeedback,
            onPress = ::press,
            modifier = Modifier.weight(0.85f),
        )
        Row(Modifier.weight(5f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FullKeyboardBlock(
                rows = mainRows,
                enabled = enabled,
                activeModifiers = activeModifiers,
                feedbackStyle = preferences.keyFeedbackStyle,
                onKeyDownFeedback = onKeyFeedback,
                onPress = ::press,
                modifier = Modifier.weight(0.68f),
            )
            if (preferences.showNavigation) {
                FullKeyboardBlock(
                    rows = navigationRows,
                    enabled = enabled,
                    activeModifiers = activeModifiers,
                    feedbackStyle = preferences.keyFeedbackStyle,
                    onKeyDownFeedback = onKeyFeedback,
                    onPress = ::press,
                    modifier = Modifier.weight(0.15f),
                )
            }
            if (preferences.showNumpad) {
                FullKeyboardBlock(
                    rows = numpadRows,
                    enabled = enabled,
                    activeModifiers = activeModifiers,
                    feedbackStyle = preferences.keyFeedbackStyle,
                    onKeyDownFeedback = onKeyFeedback,
                    onPress = ::press,
                    modifier = Modifier.weight(0.17f),
                )
            }
        }
    }
}

@Composable
private fun FullKeyboardBlock(
    rows: List<List<FullKey>>,
    enabled: Boolean,
    activeModifiers: Int,
    feedbackStyle: KeyFeedbackStyle,
    onKeyDownFeedback: () -> Unit,
    onPress: (FullKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { row ->
            FullKeyboardRow(
                keys = row,
                enabled = enabled,
                activeModifiers = activeModifiers,
                feedbackStyle = feedbackStyle,
                onKeyDownFeedback = onKeyDownFeedback,
                onPress = onPress,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FullKeyboardRow(
    keys: List<FullKey>,
    enabled: Boolean,
    activeModifiers: Int,
    feedbackStyle: KeyFeedbackStyle,
    onKeyDownFeedback: () -> Unit,
    onPress: (FullKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val indication = LocalIndication.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        keys.forEach { key ->
            if (key.keyCode == null && key.modifierMask == 0) {
                Spacer(Modifier.weight(key.units).fillMaxHeight())
            } else {
                val selected = key.modifierMask != 0 && activeModifiers and key.modifierMask != 0
                val interactionSource = remember(key.label, key.keyCode, key.modifierMask) {
                    MutableInteractionSource()
                }
                val pressed by interactionSource.collectIsPressedAsState()
                LaunchedEffect(pressed) {
                    if (pressed) onKeyDownFeedback()
                }
                val obviousPressed = pressed && feedbackStyle == KeyFeedbackStyle.OBVIOUS
                val scale by animateFloatAsState(
                    targetValue = if (obviousPressed) 0.91f else 1f,
                    label = "fullKeyboardKeyScale",
                )
                val keyBackground = when {
                    obviousPressed -> MaterialTheme.colorScheme.primary
                    selected -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                Box(
                    modifier = Modifier
                        .weight(key.units)
                        .fillMaxHeight()
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .background(keyBackground, RoundedCornerShape(7.dp))
                        .border(
                            if (obviousPressed) 2.dp else 1.dp,
                            if (selected || obviousPressed) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                            RoundedCornerShape(7.dp),
                        )
                        .clickable(
                            enabled = enabled,
                            interactionSource = interactionSource,
                            indication = indication,
                        ) { onPress(key) }
                        .padding(horizontal = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        key.label,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (obviousPressed) MaterialTheme.colorScheme.onPrimary
                        else if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyboardPanel(
    enabled: Boolean,
    snackbar: SnackbarHostState,
    onKeyFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var stickyModifiers by rememberSaveable { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    fun sendKey(code: Int) {
        onKeyFeedback()
        if (!HidHub.sendKey(code, stickyModifiers)) {
            scope.launch { snackbar.showSnackbar("尚未连接 Windows") }
        }
        stickyModifiers = 0
    }

    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = enabled,
                label = { Text("调用手机输入法输入") },
                placeholder = { Text("英文、数字、常用符号；中文请看右上角帮助") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Text,
                ),
            )
            Button(
                enabled = enabled && text.isNotEmpty(),
                onClick = {
                    onKeyFeedback()
                    val unsupported = HidHub.sendText(text)
                    text = text.filter { AsciiHidMapper.strokeFor(it) == null }
                    if (unsupported > 0) scope.launch {
                        snackbar.showSnackbar("已跳过 $unsupported 个非 ASCII 字符；蓝牙 HID 不能直接发送中文 Unicode")
                    }
                },
            ) { Text("发送") }
        }
        Text(
            "中文建议：Windows 切到中文拼音，手机键盘切英文，输入拼音后发送，再用空格/数字选词。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            (0 until 12).forEach { index ->
                KeyButton("F${index + 1}", enabled) { sendKey(KeyCodes.F1 + index) }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            KeyButton("Esc", enabled) { sendKey(KeyCodes.ESC) }
            KeyButton("Tab", enabled) { sendKey(KeyCodes.TAB) }
            KeyButton("Enter", enabled) { sendKey(KeyCodes.ENTER) }
            KeyButton("⌫", enabled) { sendKey(KeyCodes.BACKSPACE) }
            KeyButton("Del", enabled) { sendKey(KeyCodes.DELETE) }
            KeyButton("Home", enabled) { sendKey(KeyCodes.HOME) }
            KeyButton("End", enabled) { sendKey(KeyCodes.END) }
            KeyButton("PgUp", enabled) { sendKey(KeyCodes.PAGE_UP) }
            KeyButton("PgDn", enabled) { sendKey(KeyCodes.PAGE_DOWN) }
            KeyButton("Caps", enabled) { sendKey(KeyCodes.CAPS_LOCK) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            ModifierChip("Ctrl", Modifiers.LEFT_CTRL, stickyModifiers, enabled) { onKeyFeedback(); stickyModifiers = it }
            ModifierChip("Alt", Modifiers.LEFT_ALT, stickyModifiers, enabled) { onKeyFeedback(); stickyModifiers = it }
            ModifierChip("Shift", Modifiers.LEFT_SHIFT, stickyModifiers, enabled) { onKeyFeedback(); stickyModifiers = it }
            ModifierChip("Win", Modifiers.LEFT_GUI, stickyModifiers, enabled) { onKeyFeedback(); stickyModifiers = it }
            KeyButton("←", enabled) { sendKey(KeyCodes.ARROW_LEFT) }
            KeyButton("↑", enabled) { sendKey(KeyCodes.ARROW_UP) }
            KeyButton("↓", enabled) { sendKey(KeyCodes.ARROW_DOWN) }
            KeyButton("→", enabled) { sendKey(KeyCodes.ARROW_RIGHT) }
            KeyButton("Space", enabled, wide = true) { sendKey(KeyCodes.SPACE) }
        }
        Text("Ctrl / Alt / Shift / Win 为一次性修饰键：点亮后会作用于下一次功能键。", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ModifierChip(
    label: String,
    mask: Int,
    current: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    val selected = current and mask != 0
    FilterChip(
        selected = selected,
        enabled = enabled,
        onClick = { onChange(if (selected) current and mask.inv() else current or mask) },
        label = { Text(label) },
    )
}

@Composable
private fun KeyButton(label: String, enabled: Boolean, wide: Boolean = false, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = if (wide) Modifier.width(130.dp) else Modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
    ) { Text(label, maxLines = 1) }
}

@Composable
private fun TouchpadPanel(enabled: Boolean, modifier: Modifier = Modifier) {
    var sensitivity by rememberSaveable { mutableFloatStateOf(1.25f) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("指针速度", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = sensitivity,
                onValueChange = { sensitivity = it },
                valueRange = 0.5f..2.5f,
                modifier = Modifier.width(220.dp).padding(horizontal = 12.dp),
            )
            Text("${"%.1f".format(sensitivity)}×", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            Text("单指移动 · 轻点单击 · 连点双击 · 双指滚动", style = MaterialTheme.typography.bodySmall)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                .pointerInput(enabled, sensitivity) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val first = awaitFirstDown(requireUnconsumed = false)
                        val downAt = System.currentTimeMillis()
                        var lastCentroid = first.position
                        var previousCount = 1
                        var totalMovement = 0f
                        var wheelAccumulator = 0f

                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            val centroid = pressed.map { it.position }.fold(Offset.Zero) { acc, p -> acc + p } / pressed.size.toFloat()
                            if (pressed.size == previousCount) {
                                val delta = centroid - lastCentroid
                                totalMovement += abs(delta.x) + abs(delta.y)
                                if (pressed.size >= 2) {
                                    wheelAccumulator += delta.y
                                    val steps = (wheelAccumulator / 20f).toInt()
                                    if (steps != 0) {
                                        HidHub.mouseWheel(-steps)
                                        wheelAccumulator -= steps * 20f
                                    }
                                } else {
                                    HidHub.mouseMove(
                                        (delta.x * sensitivity).roundToInt(),
                                        (delta.y * sensitivity).roundToInt(),
                                    )
                                }
                            }
                            previousCount = pressed.size
                            lastCentroid = centroid
                            event.changes.forEach { it.consume() }
                        }

                        if (totalMovement < 12f && System.currentTimeMillis() - downAt < 450) {
                            HidHub.mouseClick(button = 1)
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("◎", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
                Text(if (enabled) "触控板" else "连接 Windows 后可用", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = enabled,
                onClick = { HidHub.mouseClick(1) },
                modifier = Modifier.weight(1f),
            ) { Text("左键") }
            OutlinedButton(
                enabled = enabled,
                onClick = { HidHub.mouseClick(1, double = true) },
                modifier = Modifier.weight(1f),
            ) { Text("双击") }
            Button(
                enabled = enabled,
                onClick = { HidHub.mouseClick(3) },
                modifier = Modifier.weight(1f),
            ) { Text("中键") }
            Button(
                enabled = enabled,
                onClick = { HidHub.mouseClick(2) },
                modifier = Modifier.weight(1f),
            ) { Text("右键") }
        }
    }
}

@Composable
private fun ShortcutsPanel(
    enabled: Boolean,
    onKeyFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shortcuts = listOf(
        "复制  Ctrl+C" to (KeyCodes.C to Modifiers.LEFT_CTRL),
        "粘贴  Ctrl+V" to (KeyCodes.V to Modifiers.LEFT_CTRL),
        "剪切  Ctrl+X" to (KeyCodes.X to Modifiers.LEFT_CTRL),
        "撤销  Ctrl+Z" to (KeyCodes.Z to Modifiers.LEFT_CTRL),
        "全选  Ctrl+A" to (KeyCodes.A to Modifiers.LEFT_CTRL),
        "保存  Ctrl+S" to (KeyCodes.S to Modifiers.LEFT_CTRL),
        "切换窗口  Alt+Tab" to (KeyCodes.TAB to Modifiers.LEFT_ALT),
        "显示桌面  Win+D" to (KeyCodes.D to Modifiers.LEFT_GUI),
        "任务管理器  Ctrl+Shift+Esc" to (KeyCodes.ESC to (Modifiers.LEFT_CTRL or Modifiers.LEFT_SHIFT)),
        "锁屏  Win+L" to (KeyCodes.L to Modifiers.LEFT_GUI),
    )
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("常用快捷键", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            shortcuts.take(5).forEach { (label, stroke) ->
                Button(
                    onClick = { onKeyFeedback(); HidHub.sendKey(stroke.first, stroke.second) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 14.dp),
                ) { Text(label, maxLines = 2) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            shortcuts.drop(5).forEach { (label, stroke) ->
                OutlinedButton(
                    onClick = { onKeyFeedback(); HidHub.sendKey(stroke.first, stroke.second) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 14.dp),
                ) { Text(label, maxLines = 2) }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("关于“粘贴”", fontWeight = FontWeight.SemiBold)
                Text(
                    "Ctrl+V 只会让 Windows 粘贴它自己剪贴板里已有的内容。标准蓝牙 HID 没有传输手机剪贴板或 Unicode 中文的通道；若要跨设备粘贴，需另用系统自带的跨设备剪贴板或安装电脑端程序。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun LogDialog(logs: List<String>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val content = if (logs.isEmpty()) "暂无日志" else logs.joinToString("\n")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("诊断日志") },
        text = {
            SelectionContainer {
                Text(content, modifier = Modifier.fillMaxWidth().height(260.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Pocket HID 日志", content))
            }) { Text("复制") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("配对与中文输入") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. 点“让 Windows 发现本机”，再到 Windows 设置 → 蓝牙和设备 → 添加设备，选择手机并确认配对码。")
                Text("2. 回到本应用刷新列表，点电脑旁的“连接”。若 Windows 出现两个输入设备条目，这是组合键鼠的正常表现。")
                Text("3. 中文：标准 HID 只发送物理按键码，不能发送 Unicode。推荐在 Windows 切到微软拼音，手机输入法切到英文键盘，输入拼音并发送，再用空格或数字选词。")
                Text("4. 手机中文输入法已经选出的汉字无法直接送到 Windows；“Ctrl+V”也不会同步手机剪贴板。")
                Text("5. 若注册失败：关闭其他蓝牙键盘/遥控器应用，允许本应用后台高耗电运行，保持前台后重新注册。部分 OriginOS 固件可能彻底禁用了 HID Device profile。")
                Text("6. 点右上角“全屏”后只显示键盘；使用系统返回手势或返回键退出全屏。键盘布局和反馈可在“设置”中调整。")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
    )
}
