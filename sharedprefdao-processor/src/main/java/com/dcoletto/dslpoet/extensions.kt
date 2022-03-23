package com.dcoletto.dslpoet

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import java.util.*
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.PackageElement
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
            lowercase(Locale.ROOT) == "true" -> true
            lowercase(Locale.ROOT) == "false" -> false
            else -> null
        }

val Element.parentPackage: PackageElement
    get() {
        var element: Element = this
        while (element.kind != ElementKind.PACKAGE) {
            element = element.enclosingElement
        }
        return element as PackageElement
    }