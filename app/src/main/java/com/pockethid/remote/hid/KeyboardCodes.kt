package com.pockethid.remote.hid

object Modifiers {
    const val LEFT_CTRL = 0x01
    const val LEFT_SHIFT = 0x02
    const val LEFT_ALT = 0x04
    const val LEFT_GUI = 0x08
    const val RIGHT_CTRL = 0x10
    const val RIGHT_SHIFT = 0x20
    const val RIGHT_ALT = 0x40
    const val RIGHT_GUI = 0x80
}

object KeyCodes {
    const val A = 0x04
    const val B = 0x05
    const val C = 0x06
    const val D = 0x07
    const val E = 0x08
    const val F = 0x09
    const val G = 0x0A
    const val H = 0x0B
    const val I = 0x0C
    const val J = 0x0D
    const val K = 0x0E
    const val L = 0x0F
    const val M = 0x10
    const val N = 0x11
    const val O = 0x12
    const val P = 0x13
    const val Q = 0x14
    const val R = 0x15
    const val S = 0x16
    const val T = 0x17
    const val U = 0x18
    const val V = 0x19
    const val W = 0x1A
    const val X = 0x1B
    const val Y = 0x1C
    const val Z = 0x1D
    const val DIGIT_1 = 0x1E
    const val ENTER = 0x28
    const val ESC = 0x29
    const val BACKSPACE = 0x2A
    const val TAB = 0x2B
    const val SPACE = 0x2C
    const val MINUS = 0x2D
    const val EQUALS = 0x2E
    const val LEFT_BRACKET = 0x2F
    const val RIGHT_BRACKET = 0x30
    const val BACKSLASH = 0x31
    const val SEMICOLON = 0x33
    const val APOSTROPHE = 0x34
    const val GRAVE = 0x35
    const val COMMA = 0x36
    const val PERIOD = 0x37
    const val SLASH = 0x38
    const val CAPS_LOCK = 0x39
    const val F1 = 0x3A
    const val PRINT_SCREEN = 0x46
    const val SCROLL_LOCK = 0x47
    const val PAUSE = 0x48
    const val INSERT = 0x49
    const val HOME = 0x4A
    const val PAGE_UP = 0x4B
    const val DELETE = 0x4C
    const val END = 0x4D
    const val PAGE_DOWN = 0x4E
    const val ARROW_RIGHT = 0x4F
    const val ARROW_LEFT = 0x50
    const val ARROW_DOWN = 0x51
    const val ARROW_UP = 0x52
    const val NUM_LOCK = 0x53
    const val KEYPAD_SLASH = 0x54
    const val KEYPAD_ASTERISK = 0x55
    const val KEYPAD_MINUS = 0x56
    const val KEYPAD_PLUS = 0x57
    const val KEYPAD_ENTER = 0x58
    const val KEYPAD_1 = 0x59
    const val KEYPAD_2 = 0x5A
    const val KEYPAD_3 = 0x5B
    const val KEYPAD_4 = 0x5C
    const val KEYPAD_5 = 0x5D
    const val KEYPAD_6 = 0x5E
    const val KEYPAD_7 = 0x5F
    const val KEYPAD_8 = 0x60
    const val KEYPAD_9 = 0x61
    const val KEYPAD_0 = 0x62
    const val KEYPAD_PERIOD = 0x63
    const val APPLICATION = 0x65
}

data class KeyStroke(val keyCode: Int, val modifiers: Int = 0)

/** Maps printable ASCII to USB HID usage codes for a US keyboard layout. */
object AsciiHidMapper {
    fun strokeFor(char: Char): KeyStroke? {
        if (char in 'a'..'z') return KeyStroke(KeyCodes.A + (char - 'a'))
        if (char in 'A'..'Z') return KeyStroke(KeyCodes.A + (char - 'A'), Modifiers.LEFT_SHIFT)
        if (char in '1'..'9') return KeyStroke(KeyCodes.DIGIT_1 + (char - '1'))
        if (char == '0') return KeyStroke(KeyCodes.DIGIT_1 + 9)

        return when (char) {
            '\n', '\r' -> KeyStroke(KeyCodes.ENTER)
            '\t' -> KeyStroke(KeyCodes.TAB)
            ' ' -> KeyStroke(KeyCodes.SPACE)
            '-' -> KeyStroke(KeyCodes.MINUS)
            '_' -> KeyStroke(KeyCodes.MINUS, Modifiers.LEFT_SHIFT)
            '=' -> KeyStroke(KeyCodes.EQUALS)
            '+' -> KeyStroke(KeyCodes.EQUALS, Modifiers.LEFT_SHIFT)
            '[' -> KeyStroke(KeyCodes.LEFT_BRACKET)
            '{' -> KeyStroke(KeyCodes.LEFT_BRACKET, Modifiers.LEFT_SHIFT)
            ']' -> KeyStroke(KeyCodes.RIGHT_BRACKET)
            '}' -> KeyStroke(KeyCodes.RIGHT_BRACKET, Modifiers.LEFT_SHIFT)
            '\\' -> KeyStroke(KeyCodes.BACKSLASH)
            '|' -> KeyStroke(KeyCodes.BACKSLASH, Modifiers.LEFT_SHIFT)
            ';' -> KeyStroke(KeyCodes.SEMICOLON)
            ':' -> KeyStroke(KeyCodes.SEMICOLON, Modifiers.LEFT_SHIFT)
            '\'' -> KeyStroke(KeyCodes.APOSTROPHE)
            '"' -> KeyStroke(KeyCodes.APOSTROPHE, Modifiers.LEFT_SHIFT)
            '`' -> KeyStroke(KeyCodes.GRAVE)
            '~' -> KeyStroke(KeyCodes.GRAVE, Modifiers.LEFT_SHIFT)
            ',' -> KeyStroke(KeyCodes.COMMA)
            '<' -> KeyStroke(KeyCodes.COMMA, Modifiers.LEFT_SHIFT)
            '.' -> KeyStroke(KeyCodes.PERIOD)
            '>' -> KeyStroke(KeyCodes.PERIOD, Modifiers.LEFT_SHIFT)
            '/' -> KeyStroke(KeyCodes.SLASH)
            '?' -> KeyStroke(KeyCodes.SLASH, Modifiers.LEFT_SHIFT)
            '!' -> KeyStroke(KeyCodes.DIGIT_1, Modifiers.LEFT_SHIFT)
            '@' -> KeyStroke(KeyCodes.DIGIT_1 + 1, Modifiers.LEFT_SHIFT)
            '#' -> KeyStroke(KeyCodes.DIGIT_1 + 2, Modifiers.LEFT_SHIFT)
            '$' -> KeyStroke(KeyCodes.DIGIT_1 + 3, Modifiers.LEFT_SHIFT)
            '%' -> KeyStroke(KeyCodes.DIGIT_1 + 4, Modifiers.LEFT_SHIFT)
            '^' -> KeyStroke(KeyCodes.DIGIT_1 + 5, Modifiers.LEFT_SHIFT)
            '&' -> KeyStroke(KeyCodes.DIGIT_1 + 6, Modifiers.LEFT_SHIFT)
            '*' -> KeyStroke(KeyCodes.DIGIT_1 + 7, Modifiers.LEFT_SHIFT)
            '(' -> KeyStroke(KeyCodes.DIGIT_1 + 8, Modifiers.LEFT_SHIFT)
            ')' -> KeyStroke(KeyCodes.DIGIT_1 + 9, Modifiers.LEFT_SHIFT)
            else -> null
        }
    }
}
