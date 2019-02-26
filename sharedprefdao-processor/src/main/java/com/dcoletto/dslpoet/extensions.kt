package com.dcoletto.dslpoet

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import kotlin.reflect.jvm.internal.impl.builtins.jvm.JavaToKotlinClassMap
import kotlin.reflect.jvm.internal.impl.name.FqName

fun TypeName.javaToKotlinType(): TypeName =
        if (this is ParameterizedTypeName) {
            (rawType.javaToKotlinType() as ClassName).parameterizedBy(
                    *typeArguments.map { it.javaToKotlinType() }.toTypedArray()
            )
        } else {
            val className = JavaToKotlinClassMap
                    .INSTANCE
                    .mapJavaToKotlin(FqName(toString()))
                    ?.asSingleFqName()
                    ?.asString()
            if (className == null) {
                this
            } else {
                ClassName.bestGuess(className)
            }
        }

fun String.toBooleanOrNull(): Boolean? =
        when {
            toLowerCase() == "true" -> true
            toLowerCase() == "false" -> false
            else -> null
        }