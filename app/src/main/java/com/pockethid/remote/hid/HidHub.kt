package com.pockethid.remote.hid

import kotlinx.coroutines.flow.StateFlow

object HidHub {
    val state: StateFlow<HidState> get() = HidService.state
    val logs: StateFlow<List<String>> get() = HidService.logs

    fun refreshPairedHosts() = HidService.instance?.refreshPairedHosts()
    fun register() = HidService.instance?.registerHid()
    fun connect(address: String) = HidService.instance?.connect(address)
    fun disconnect() = HidService.instance?.disconnect()
    fun sendKey(keyCode: Int, modifiers: Int = 0) =
        HidService.instance?.sendKey(keyCode, modifiers) ?: false

    fun sendText(text: String): Int = HidService.instance?.sendText(text) ?: text.length
    fun mouseMove(dx: Int, dy: Int) = HidService.instance?.mouseMove(dx, dy)
    fun mouseClick(button: Int, double: Boolean = false) = HidService.instance?.mouseClick(button, double)
    fun mouseButton(button: Int, pressed: Boolean) = HidService.instance?.mouseButton(button, pressed)
    fun mouseWheel(amount: Int) = HidService.instance?.mouseWheel(amount)
}
