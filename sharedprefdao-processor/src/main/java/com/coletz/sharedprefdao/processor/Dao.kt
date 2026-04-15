package com.coletz.sharedprefdao.processor

import com.coletz.dslpoet.*
import com.coletz.sharedprefdao.annotation.DefaultValue
import com.coletz.sharedprefdao.annotation.DefaultValue.Companion.EMPTY_STRING
import com.coletz.sharedprefdao.annotation.Flow
import com.coletz.sharedprefdao.annotation.Name
import com.coletz.sharedprefdao.annotation.NumericId
import com.coletz.sharedprefdao.annotation.SharedPrefDao
import com.coletz.sharedprefdao.annotation.StringId
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName

private val flowType = ClassName("kotlinx.coroutines.flow", "Flow")

class Dao(private val daoInterface: KSClassDeclaration) {
    private val annotation: SharedPrefDao = daoInterface.getAnnotationsByType(SharedPrefDao::class).first()
    private val packageName = daoInterface.packageName.asString()

    private val className = daoInterface
        .qualifiedName!!
        .asString()
        .substring(packageName.length + 1)
        .plus("Impl")

    fun generateCode(): FileSpec {

        val spType = ClassName("android.content", "SharedPreferences")
        val ctxType = ClassName("android.content", "Context")

        // Pre-scan to check if any @Flow annotations are present
        val hasFlowAnnotations = daoInterface.getAllProperties().any { property ->
            property.getter?.getAnnotationsByType(Flow::class)?.firstOrNull() != null
        }

        return file(packageName, className) {
            // Add imports for callbackFlow when @Flow is used
            if (hasFlowAnnotations) {
                addImport("kotlinx.coroutines.channels", "awaitClose")
                addImport("kotlinx.coroutines.flow", "callbackFlow")
                addImport("kotlinx.coroutines.flow", "distinctUntilChanged")
            }

            val generatorFuncName = daoInterface.simpleName.asString().replaceFirstChar { it.lowercase() }
            val generatorFuncType = ClassName(packageName, className)
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
                addSuperinterface(daoInterface.toClassName())
                addModifiers(KModifier.FINAL)
                constructor {
                    addModifiers(KModifier.INTERNAL)
                    addParameter("sp", spType)
                    addProperty(PropertySpec.builder("sp", spType)
                        .addModifiers(KModifier.PRIVATE)
                        .initializer("sp")
                        .build())
                }

                val keys = arrayListOf<SharedPreferenceKey>()

                daoInterface.getAllProperties().forEach { property ->
                    val propertyName = property.simpleName.asString()
                    val resolvedType = property.type.resolve()
                    val isNullable = resolvedType.isMarkedNullable
                    val propertyType = resolvedType.toTypeName()

                    // Get annotations from the getter
                    val getter = property.getter
                    val defaultValueAnnotation = getter?.getAnnotationsByType(DefaultValue::class)?.firstOrNull()
                    val nameAnnotation = getter?.getAnnotationsByType(Name::class)?.firstOrNull()
                    val flowAnnotation = getter?.getAnnotationsByType(Flow::class)?.firstOrNull()
                    val stringIdAnnotation = getter?.getAnnotationsByType(StringId::class)?.firstOrNull()
                    val numericIdAnnotation = getter?.getAnnotationsByType(NumericId::class)?.firstOrNull()

                    val className = when (val typeName = propertyType.copy(nullable = false)) {
                        is ClassName -> typeName
                        is ParameterizedTypeName -> typeName.rawType
                        else -> ClassName.bestGuess(typeName.toString())
                    }
                    val classSimpleName = className.simpleName.replaceFirstChar { it.titlecase() }
                    val keyName = "${annotation.prefix}_${propertyName.uppercase()}_KEY"
                    val keyValue = nameAnnotation?.value ?: keyName
                    keys.add(SharedPreferenceKey(name = keyName.uppercase(), value = keyValue))

                    val typeDeclaration = resolvedType.declaration
                    if (typeDeclaration is KSClassDeclaration && typeDeclaration.classKind == ClassKind.ENUM_CLASS) {
                        // PROCESS ENUM
                        val enumEntries = typeDeclaration.declarations
                            .filterIsInstance<KSClassDeclaration>()
                            .filter { it.classKind == ClassKind.ENUM_ENTRY }
                            .toList()

                        val defVal = defaultValueAnnotation?.value.let { defValAnnotation ->
                            when {
                                defValAnnotation != null -> {
                                    if (defValAnnotation.toIntOrNull() != null) {
                                        enumEntries[defValAnnotation.toInt()]
                                    } else {
                                        // valueOf defValueAnnotation
                                        enumEntries
                                            .firstOrNull { it.simpleName.asString().equals(defValAnnotation, ignoreCase = true) }
                                            ?: throw Exception("String `$defValAnnotation` used as DefaultValue is not present in enum class ${typeDeclaration.simpleName.asString()}")
                                    }
                                }
                                isNullable -> null
                                else -> enumEntries.first()
                            }
                        }

                        property(propertyName, propertyType.copy(nullable = isNullable)) {
                            addModifiers(KModifier.OVERRIDE)
                            mutable()

                            if (stringIdAnnotation != null) {
                                val idField = stringIdAnnotation.value
                                val defGetterCode = if (defVal != null) {
                                    """|return sp.getString($keyName, $classSimpleName.${defVal.simpleName.asString()}.$idField)
                                        |    ?.let { id -> $classSimpleName.entries.firstOrNull { it.$idField == id } }
                                        |    ?: $classSimpleName.${defVal.simpleName.asString()}""".trimMargin()
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
                                    |return sp.getInt($keyName, $classSimpleName.${defVal.simpleName.asString()}.$idField)
                                    |    .let { id -> $classSimpleName.entries.firstOrNull { it.$idField == id } }
                                    |    ?: $classSimpleName.${defVal.simpleName.asString()}
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
                                        code("""return $classSimpleName.entries[sp.getInt($keyName, $classSimpleName.${defVal.simpleName.asString()}.ordinal)] """)
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

                        // Generate flow property if @Flow annotation is present
                        if (flowAnnotation != null) {
                            addProperty(PropertySpec.builder(
                                "${propertyName}Flow",
                                flowType.parameterizedBy(propertyType.copy(nullable = isNullable))
                            )
                                .initializer(CodeBlock.builder()
                                    .add("callbackFlow {\n")
                                    .indent()
                                    .addStatement("trySend($propertyName)")
                                    .add("val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->\n")
                                    .indent()
                                    .addStatement("if (key == $keyName) trySend($propertyName)")
                                    .unindent()
                                    .add("}\n")
                                    .addStatement("sp.registerOnSharedPreferenceChangeListener(listener)")
                                    .addStatement("awaitClose { sp.unregisterOnSharedPreferenceChangeListener(listener) }")
                                    .unindent()
                                    .add("}.distinctUntilChanged()")
                                    .build())
                                .build())
                        }
                    } else if (propertyType.copy(nullable = false).toString().let { it == "kotlin.collections.Set<kotlin.String>" || it == "Set<String>" }) {
                        // PROCESS SET OF STRING
                        if (defaultValueAnnotation != null) {
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
                        val defValAnnotation = defaultValueAnnotation?.value
                        val typeString = propertyType.copy(nullable = false).toString()
                        val flowGetterCode: String? = when (typeString) {
                            "kotlin.String", "String" -> {
                                val defVal = when {
                                    defValAnnotation != null -> "\"$defValAnnotation\""
                                    isNullable -> null
                                    else -> "\"$EMPTY_STRING\""
                                }
                                """sp.getString($keyName, $defVal) ?: $defVal"""
                            }
                            "kotlin.Boolean", "Boolean" -> {
                                val defVal = when {
                                    defValAnnotation != null -> defValAnnotation.toBooleanOrNull() ?: throw Exception("$defValAnnotation` can't be used as DefaultValue for an Boolean property")
                                    isNullable -> null
                                    else -> false
                                }
                                """sp.getBoolean($keyName, $defVal)"""
                            }
                            "kotlin.Float", "Float" -> {
                                val defVal = when {
                                    defValAnnotation != null -> defValAnnotation.toFloatOrNull() ?: throw Exception("`$defValAnnotation` can't be used as DefaultValue for an Float property")
                                    isNullable -> null
                                    else -> 0F
                                }
                                """sp.getFloat($keyName, ${defVal}f)"""
                            }
                            "kotlin.Int", "Int" -> {
                                val defVal = when {
                                    defValAnnotation != null -> defValAnnotation.toIntOrNull() ?: throw Exception("`$defValAnnotation` can't be used as DefaultValue for an Int property")
                                    isNullable -> null
                                    else -> 0
                                }
                                """sp.getInt($keyName, $defVal)"""
                            }
                            "kotlin.Long", "Long" -> {
                                val defVal = when {
                                    defValAnnotation != null -> defValAnnotation.toLongOrNull() ?: throw Exception("`$defValAnnotation` can't be used as DefaultValue for an Long property")
                                    isNullable -> null
                                    else -> 0L
                                }
                                """sp.getLong($keyName, $defVal)"""
                            }
                            else -> null
                        }

                        if (flowGetterCode == null) {
                            throw Exception("This type [$propertyType] is not supported")
                        }

                        property(propertyName, propertyType.copy(nullable = isNullable)) {
                            addModifiers(KModifier.OVERRIDE)
                            mutable()

                            getter { code("return $flowGetterCode") }

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

                        // Generate flow property if @Flow annotation is present
                        if (flowAnnotation != null) {
                            addProperty(PropertySpec.builder(
                                "${propertyName}Flow",
                                flowType.parameterizedBy(propertyType.copy(nullable = isNullable))
                            )
                                .initializer(CodeBlock.builder()
                                    .add("callbackFlow {\n")
                                    .indent()
                                    .addStatement("trySend($propertyName)")
                                    .add("val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->\n")
                                    .indent()
                                    .addStatement("if (key == $keyName) trySend($propertyName)")
                                    .unindent()
                                    .add("}\n")
                                    .addStatement("sp.registerOnSharedPreferenceChangeListener(listener)")
                                    .addStatement("awaitClose { sp.unregisterOnSharedPreferenceChangeListener(listener) }")
                                    .unindent()
                                    .add("}.distinctUntilChanged()")
                                    .build())
                                .build())
                        }
                    }
                }

                companion {
                    keys.forEach { (name, value) ->
                        property<String>(name) {
                            addModifiers(KModifier.CONST)
                            initializer(""""$value"""")
                        }
                    }
                }
            }
        }
    }
}
