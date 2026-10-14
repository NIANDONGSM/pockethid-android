package com.pockethid.remote.hid

import org.junit.Assert.assertTrue
import org.junit.Test

class HidDescriptorTest {
    @Test
    fun descriptorContainsKeyboardAndMouseReportIds() {
        val unsigned = HidDescriptor.bytes.map { it.toInt() and 0xFF }
        assertTrue(unsigned.windowed(2).contains(listOf(0x85, HidDescriptor.KEYBOARD_REPORT_ID)))
        assertTrue(unsigned.windowed(2).contains(listOf(0x85, HidDescriptor.MOUSE_REPORT_ID)))
        assertTrue(unsigned.windowed(2).contains(listOf(0x09, 0x06))) // Keyboard usage
        assertTrue(unsigned.windowed(2).contains(listOf(0x09, 0x02))) // Mouse usage
    }
}
