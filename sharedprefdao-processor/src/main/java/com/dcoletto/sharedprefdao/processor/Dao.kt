package com.dcoletto.sharedprefdao.processor

import com.dcoletto.dslpoet.*
import com.dcoletto.sharedprefdao.annotation.DefaultValue
import com.dcoletto.sharedprefdao.annotation.DefaultValue.Companion.EMPTY_STRING
import com.dcoletto.sharedprefdao.annotation.SharedPrefDao
import com.squareup.kotlinpoet.*
import org.jetbrains.annotations.Nullable
import java.util.*
import javax.lang.model.element.*
import javax.lang.model.type.*

class Dao(private val daoInterface: TypeElement, private val annotation: SharedPrefDao){
    private val packageName = daoInterface.parentPackage.qualifiedName.toString()

    private val className = daoInterface
            .qualifiedName
            .toString()
            .substring(packageName.length + 1)
            .plus("_Impl")

    fun generateCode(): FileSpec {

        val spType = ClassName("android.content", "SharedPreferences")
        val ctxType = ClassName("android.content", "Context")

        return file(packageName, className) {

            val generatorFuncName = daoInterface.simpleName.toString().replaceFirstChar { it.lowercase() }
            val generatorFuncType = daoInterface.asType().asTypeName()
            val spName = annotation.name.takeIf { it.isNotBlank() } ?: generatorFuncName.uppercase().plus("_SP")


            val genFromSp = genFunction(generatorFuncName, generatorFuncType) {
                receiver(spType)
                code("return $className(this)")
            }

            val genFromCtx = genFunction(generatorFuncName, generatorFuncType) {
                receiver(ctxType)
                code("""return getSharedPreferences("$spName", Context.MODE_PRIVATE).$generatorFuncName()""")
            }

            addFunction(genFromSp)
            addFunction(genFromCtx)

            kclass(className) {
                addSuperinterface(daoInterface.asClassName())
                addModifiers(KModifier.FINAL)
                constructor {
                    addModifiers(KModifier.INTERNAL)
                    addParameter("sp", spType, KModifier.PRIVATE)
                    addProperty(PropertySpec.builder("sp", spType)
                            .initializer("sp")
                            .build())
                }

                val keys = arrayListOf<String>()

                daoInterface.enclosedElements.forEach { element ->
                    if(element.kind == ElementKind.METHOD){

                        when {
                            element.simpleName.startsWith("get") -> { "get" }
                            element.simpleName.startsWith("is") -> { "" }
                            else -> { null }
                        }?.let { methodPrefix ->
                            val propertyName = element.simpleName
                                .removePrefix(methodPrefix)
                                .toString()
                                .replaceFirstChar { it.lowercase(Locale.getDefault()) }

                            val isNullable = element.getAnnotation(Nullable::class.java) != null
                            val defaultValueAnnotation = element.getAnnotation(DefaultValue::class.java)
                            val retType = (element as ExecutableElement).returnType
                            val propertyType = retType.asTypeName().javaToKotlinType()
                            val className = ClassName.bestGuess(propertyType.toString())
                            val classSimpleName = className.simpleName.replaceFirstChar { it.titlecase() }
                            val key = "${annotation.prefix}_${propertyName.uppercase()}_KEY"

                            keys.add(key)

                            if((retType is DeclaredType) && retType.asElement().kind == ElementKind.ENUM) {
                                // PROCESS ENUM
                                val enumElement = retType.asElement()

                                val defVal = defaultValueAnnotation?.value.let { defValAnnotation ->
                                    when {
                                        defValAnnotation != null -> {
                                            if(defValAnnotation.toIntOrNull() != null) {
                                                enumElement.enclosedElements.filter { it.kind == ElementKind.ENUM_CONSTANT }[defValAnnotation.toInt()]
                                            } else {
                                                // valueOf defValueAnnotation
                                                enumElement.enclosedElements
                                                        .filter { it.kind == ElementKind.ENUM_CONSTANT }
                                                        .firstOrNull { it.simpleName.toString().lowercase() == defValAnnotation.lowercase() }
                                                        ?: throw Exception("String `$defValAnnotation` used as DefaultValue is not present in enum class ${enumElement.simpleName}")
                                            }
                                        }
                                        isNullable -> null
                                        else -> enumElement.enclosedElements.first { it.kind == ElementKind.ENUM_CONSTANT }
                                    }
                                }

                                property(propertyName, propertyType.copy(nullable = isNullable)) {
                                    addModifiers(KModifier.OVERRIDE)
                                    mutable()

                                    getter {
                                        code("""return $classSimpleName.values()[sp.getInt($key, $classSimpleName.$defVal.ordinal)] """)
                                    }

                                    setter(propertyType) {
                                        code("""sp.edit().putInt($key, value.ordinal).apply()""")
                                    }
                                }
                            //} else if(is set of string){
                            } else if(propertyType.toString() == "kotlin.collections.Set<kotlin.String>"){
                                // PROCESS SET OF STRING
                                if(defaultValueAnnotation != null){
                                    throw Exception("DefaultValue is not supported for Set")
                                }

                                property(propertyName, propertyType.copy(nullable = isNullable)) {
                                    addModifiers(KModifier.OVERRIDE)
                                    mutable()

                                    val defVal = when {
                                        isNullable -> null
                                        else ->  "setOf()"
                                    }

                                    getter {
                                        code("""return sp.getStringSet($key, $defVal) """)
                                    }

                                    setter(propertyType) {
                                        code("""sp.edit().putStringSet($key, value).apply()""")
                                    }
                                }
                            } else {
                                // PROCESS NORMAL TYPES
                                val defVal: Any? = defaultValueAnnotation?.value.let { defValAnnotation ->
                                    when(propertyType.toString()){
                                        "kotlin.String" -> {
                                            when {
                                                defValAnnotation != null -> "\"$defValAnnotation\""
                                                isNullable -> null
                                                else ->  "\"$EMPTY_STRING\""
                                            }
                                        }
                                        "kotlin.Boolean" -> {
                                            when {
                                                defValAnnotation != null -> defValAnnotation.toBooleanOrNull() ?: throw Exception("$defValAnnotation` can't be used as DefaultValue for an Boolean property")
                                                isNullable -> null
                                                else ->  false
                                            }
                                        }
                                        "kotlin.Float" -> {
                                            when {
                                                defValAnnotation != null -> defValAnnotation.toFloatOrNull() ?: throw Exception("`$defValAnnotation` can't be used as DefaultValue for an Float property")
                                                isNullable -> null
                                                else ->  0F
                                            }
                                        }
                                        "kotlin.Int" -> {
                                            when {
                                                defValAnnotation != null -> defValAnnotation.toIntOrNull() ?: throw Exception("`$defValAnnotation` can't be used as DefaultValue for an Int property")
                                                isNullable -> null
                                                else ->  0
                                            }
                                        }
                                        "kotlin.Long" -> {
                                            when {
                                                defValAnnotation != null -> defValAnnotation.toLongOrNull() ?: throw Exception("`$defValAnnotation` can't be used as DefaultValue for an Long property")
                                                isNullable -> null
                                                else ->  0L
                                            }
                                        }
                                        else -> {
                                            throw Exception("This type [$propertyType] is not supported")
                                        }
                                    }
                                }

                                property(propertyName, propertyType.copy(nullable = isNullable)) {
                                    addModifiers(KModifier.OVERRIDE)
                                    mutable()

                                    getter {
                                        code("""return sp.get$classSimpleName($key, $defVal) """)
                                    }

                                    setter(propertyType) {
                                        code("""sp.edit().put$classSimpleName($key, value).apply()""")
                                    }
                                }
                            }
                        }
                    }
                }

                companion {
                    keys.forEach {
                        property<String>(it){
                            addModifiers(KModifier.CONST)
                            initializer(""""$it"""")
                        }
                    }
                }
            }
        }
    }
}