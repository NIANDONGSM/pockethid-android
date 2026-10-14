package com.pockethid.remote.hid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AsciiHidMapperTest {
    @Test
    fun mapsLettersNumbersAndShiftedSymbols() {
        assertEquals(KeyStroke(KeyCodes.A), AsciiHidMapper.strokeFor('a'))
        assertEquals(KeyStroke(KeyCodes.A, Modifiers.LEFT_SHIFT), AsciiHidMapper.strokeFor('A'))
        assertEquals(KeyStroke(KeyCodes.DIGIT_1), AsciiHidMapper.strokeFor('1'))
        assertEquals(
            KeyStroke(KeyCodes.DIGIT_1, Modifiers.LEFT_SHIFT),
            AsciiHidMapper.strokeFor('!'),
        )
        assertEquals(
            KeyStroke(KeyCodes.SLASH, Modifiers.LEFT_SHIFT),
            AsciiHidMapper.strokeFor('?'),
        )
    }

    @Test
    fun rejectsCharactersThatHidCannotRepresentDirectly() {
        assertNull(AsciiHidMapper.strokeFor('中'))
        assertNull(AsciiHidMapper.strokeFor('€'))
    }
}
