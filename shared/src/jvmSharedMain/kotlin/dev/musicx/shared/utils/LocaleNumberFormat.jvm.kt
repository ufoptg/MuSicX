package dev.musicx.shared.utils

import java.text.DecimalFormatSymbols

internal actual fun localeDecimalSeparator(): Char = DecimalFormatSymbols.getInstance().decimalSeparator
