package com.coletz.sharedprefdao.processor

import com.coletz.dslpoet.*
import com.coletz.sharedprefdao.annotation.DefaultValue
import com.coletz.sharedprefdao.annotation.DefaultValue.Companion.EMPTY_STRING
import com.coletz.sharedprefdao.annotation.Name
import com.coletz.sharedprefdao.annotation.NumericId
import com.coletz.sharedprefdao.annotation.SharedPrefDao
import com.coletz.sharedprefdao.annotation.StringId
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
            .plus("Impl")

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

                val keys = arrayListOf<SharedPreferenceKey>()

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
                            val nameAnnotation = element.getAnnotation(Name::class.java)
                            val keyName = "${annotation.prefix}_${propertyName.uppercase()}_KEY"
                            val keyValue = nameAnnotation?.value ?: keyName
                            keys.add(SharedPreferenceKey(name = keyName.uppercase(), value = keyValue))

                            if((retType is DeclaredType) && retType.asElement().kind == ElementKind.ENUM) {
                                // PROCESS ENUM
                                val enumElement = retType.asElement()

                                val stringIdAnnotation = element.getAnnotation(StringId::class.java)
                                val numericIdAnnotation = element.getAnnotation(NumericId::class.java)

                                val defVal = defaultValueAnnotation?.value.let { defValAnnotation ->
                                    when {
                                        defValAnnotation != null -> {
                                            if(defValAnnotation.toIntOrNull() != null) {
                                                enumElement.enclosedElements.filter { it.kind == ElementKind.ENUM_CONSTANT }[defValAnnotation.toInt()]
                                            } else {
                                                // valueOf defValueAnnotation
                                                enumElement.enclosedElements
                                                        .filter { it.kind == ElementKind.ENUM_CONSTANT }
                                                        .firstOrNull { it.simpleName.toString().equals(defValAnnotation, ignoreCase = true) }
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

                                    if (stringIdAnnotation != null) {
                                        val idField = stringIdAnnotation.value
                                        val defGetterCode = if (defVal != null) {
                                            """|return sp.getString($keyName, $classSimpleName.$defVal.$idField)
                                                |    ?.let { id -> $classSimpleName.entries.firstOrNull { it.$idField == id } } 
                                                |    ?: $classSimpleName.$defVal""".trimMargin()
                                        } else {
                                            """return sp.getString($keyName, null)?.let { id -> $classSimpleName.entries.firstOrNull { it.$idField == id } }"""
                                        }

                                        getter { code(defGetterCode) }

                                        setter(propertyType) {
                                            if (isNullable) {
                                                code("""
                                                    |sp.edit().run {
                                                    |    if (value == null) remove($keyName) else putString($keyName, value.$idField)
                                                    |}.apply()
                                                    |""".trimMargin())
                                            } else {
                                                code("""sp.edit().putString($keyName, value.$idField).apply()""")
                                            }
                                        }
                                    } else if (numericIdAnnotation != null) {
                                        val idField = numericIdAnnotation.value
                                        val defGetterCode = if (defVal != null) {
                                            """
                                            |return sp.getInt($keyName, $classSimpleName.$defVal.$idField)
                                            |    .let { id -> $classSimpleName.entries.firstOrNull { it.$idField == id } }
                                            |    ?: $classSimpleName.$defVal
                                            |""".trimMargin()
                                        } else {
                                            """
                                                | return $keyName
                                                | .takeIf { sp.contains(it) }
                                                | ?.let { sp.getInt(it, 0) }
                                                | ?.let { id -> $classSimpleName.entries.firstOrNull { it.$idField == id } }
                                            |""".trimMargin()
                                        }

                                        getter { code(defGetterCode) }

                                        setter(propertyType) {
                                            if (isNullable) {
                                                code("""
                                                    |sp.edit().run {
                                                    |    if (value == null) remove($keyName) else putInt($keyName, value.$idField)
                                                    |}.apply()
                                                    |""".trimMargin())
                                            } else {
                                                code("""sp.edit().putInt($keyName, value.$idField).apply()""")
                                            }
                                        }
                                    } else {
                                        // Default: use ordinal
                                        getter {
                                            if (defVal != null) {
                                                code("""return $classSimpleName.entries[sp.getInt($keyName, $classSimpleName.$defVal.ordinal)] """)
                                            } else {
                                                code("""
                                                    |val key = $keyName
                                                    |return if (sp.contains(key)) $classSimpleName.entries[sp.getInt(key, 0)] else null
                                                    |""".trimMargin())
                                            }
                                        }

                                        setter(propertyType) {
                                            if (isNullable) {
                                                code("""
                                                    |sp.edit().run {
                                                    |    if (value == null) remove($keyName) else putInt($keyName, value.ordinal)
                                                    |}.apply()
                                                    |""".trimMargin())
                                            } else {
                                                code("""sp.edit().putInt($keyName, value.ordinal).apply()""")
                                            }
                                        }
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

                                    getter {
                                        when {
                                            isNullable -> {
                                                code("""return sp.getStringSet($keyName, null)""")
                                            }
                                            else -> {
                                                val defVal = "setOf()"
                                                code("""return sp.getStringSet($keyName, $defVal) ?: $defVal""")
                                            }
                                        }
                                    }

                                    setter(propertyType) {
                                        code("""sp.edit().putStringSet($keyName, value).apply()""")
                                    }
                                }
                            } else {
                                // PROCESS NORMAL TYPES
                                property(propertyName, propertyType.copy(nullable = isNullable)) {
                                    addModifiers(KModifier.OVERRIDE)
                                    mutable()

                                    getter {
                                        val defValAnnotation = defaultValueAnnotation?.value
                                        when(propertyType.toString()){
                                            "kotlin.String" -> {
                                                val defVal = when {
                                                    defValAnnotation != null -> "\"$defValAnnotation\""
                                                    isNullable -> null
                                                    else ->  "\"$EMPTY_STRING\""
                                                }
                                                code("""return sp.getString($keyName, $defVal) ?: $defVal""")
                                            }
                                            "kotlin.Boolean" -> {
                                                val defVal = when {
                                                    defValAnnotation != null -> defValAnnotation.toBooleanOrNull() ?: throw Exception("$defValAnnotation` can't be used as DefaultValue for an Boolean property")
                                                    isNullable -> null
                                                    else ->  false
                                                }
                                                code("""return sp.getBoolean($keyName, $defVal) """)
                                            }
                                            "kotlin.Float" -> {
                                                val defVal = when {
                                                    defValAnnotation != null -> defValAnnotation.toFloatOrNull() ?: throw Exception("`$defValAnnotation` can't be used as DefaultValue for an Float property")
                                                    isNullable -> null
                                                    else ->  0F
                                                }
                                                code("""return sp.getFloat($keyName, ${defVal}f) """)
                                            }
                                            "kotlin.Int" -> {
                                                val defVal = when {
                                                    defValAnnotation != null -> defValAnnotation.toIntOrNull() ?: throw Exception("`$defValAnnotation` can't be used as DefaultValue for an Int property")
                                                    isNullable -> null
                                                    else ->  0
                                                }
                                                code("""return sp.getInt($keyName, $defVal) """)
                                            }
                                            "kotlin.Long" -> {
                                                val defVal = when {
                                                    defValAnnotation != null -> defValAnnotation.toLongOrNull() ?: throw Exception("`$defValAnnotation` can't be used as DefaultValue for an Long property")
                                                    isNullable -> null
                                                    else ->  0L
                                                }
                                                code("""return sp.getLong($keyName, $defVal) """)
                                            }
                                            else -> {
                                                throw Exception("This type [$propertyType] is not supported")
                                            }
                                        }
                                    }

                                    setter(propertyType) {
                                        if (isNullable) {
                                            code("""
                                                |sp.edit().run {
                                                |    if (value == null) remove($keyName) else put$classSimpleName($keyName, value)
                                                |}.apply()
                                                |""".trimMargin())
                                        } else {
                                            code("""sp.edit().put$classSimpleName($keyName, value).apply()""")

                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                companion {
                    keys.forEach { (name, value) ->
                        property<String>(name){
                            addModifiers(KModifier.CONST)
                            initializer(""""$value"""")
                        }
                    }
                }
            }
        }
    }
}

