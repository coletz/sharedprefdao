package com.coletz.dslpoet

import java.util.*

fun String.toBooleanOrNull(): Boolean? =
    when {
        lowercase(Locale.ROOT) == "true" -> true
        lowercase(Locale.ROOT) == "false" -> false
        else -> null
    }
