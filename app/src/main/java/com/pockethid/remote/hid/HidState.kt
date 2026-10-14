package com.pockethid.remote.hid

data class HostDevice(
    val name: String,
    val address: String,
)

enum class HidPhase {
    IDLE,
    STARTING,
    REGISTERING,
    READY,
    CONNECTING,
    CONNECTED,
    ERROR,
}

data class HidState(
    val phase: HidPhase = HidPhase.IDLE,
    val bluetoothAvailable: Boolean = true,
    val bluetoothEnabled: Boolean = false,
    val profileSupported: Boolean = true,
    val registered: Boolean = false,
    val connectedHost: HostDevice? = null,
    val pairedHosts: List<HostDevice> = emptyList(),
    val message: String = "尚未启动",
) {
    val canSend: Boolean get() = registered && connectedHost != null
}
