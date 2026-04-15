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
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import java.util.*

private val mutableStateFlowType = ClassName("kotlinx.coroutines.flow", "MutableStateFlow")
private val stateFlowType = ClassName("kotlinx.coroutines.flow", "StateFlow")

private data class FlowPropertyInfo(
    val propertyName: String,
    val keyName: String
)

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
            // Add import for asStateFlow extension function only when @Flow is used
            if (hasFlowAnnotations) {
                addImport("kotlinx.coroutines.flow", "asStateFlow")
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
                val flowProperties = arrayListOf<FlowPropertyInfo>()

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

                        // Determine getter code for flow (used in property and flow initialization)
                        // Wrapped in parentheses to prevent KotlinPoet line-breaking in the middle of expressions
                        val enumFlowGetterCode: String = when {
                            stringIdAnnotation != null -> {
                                val idField = stringIdAnnotation.value
                                if (defVal != null) {
                                    """(sp.getString($keyName, $classSimpleName.${defVal.simpleName.asString()}.$idField)?.let { id -> $classSimpleName.entries.firstOrNull { it.$idField == id } } ?: $classSimpleName.${defVal.simpleName.asString()})"""
                                } else {
                                    """(sp.getString($keyName, null)?.let { id -> $classSimpleName.entries.firstOrNull { it.$idField == id } })"""
                                }
                            }
                            numericIdAnnotation != null -> {
                                val idField = numericIdAnnotation.value
                                if (defVal != null) {
                                    """(sp.getInt($keyName, $classSimpleName.${defVal.simpleName.asString()}.$idField).let { id -> $classSimpleName.entries.firstOrNull { it.$idField == id } } ?: $classSimpleName.${defVal.simpleName.asString()})"""
                                } else {
                                    """($keyName.takeIf { sp.contains(it) }?.let { sp.getInt(it, 0) }?.let { id -> $classSimpleName.entries.firstOrNull { it.$idField == id } })"""
                                }
                            }
                            else -> {
                                // Default: use ordinal
                                if (defVal != null) {
                                    """$classSimpleName.entries[sp.getInt($keyName, $classSimpleName.${defVal.simpleName.asString()}.ordinal)]"""
                                } else {
                                    """(if (sp.contains($keyName)) $classSimpleName.entries[sp.getInt($keyName, 0)] else null)"""
                                }
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
                            flowProperties.add(FlowPropertyInfo(
                                propertyName = propertyName,
                                keyName = keyName
                            ))

                            // Private backing MutableStateFlow
                            addProperty(PropertySpec.builder(
                                "_${propertyName}Flow",
                                mutableStateFlowType.parameterizedBy(propertyType.copy(nullable = isNullable))
                            )
                                .addModifiers(KModifier.PRIVATE)
                                .initializer("MutableStateFlow($enumFlowGetterCode)")
                                .build())

                            // Public read-only StateFlow
                            addProperty(PropertySpec.builder(
                                "${propertyName}Flow",
                                stateFlowType.parameterizedBy(propertyType.copy(nullable = isNullable))
                            )
                                .initializer("_${propertyName}Flow.asStateFlow()")
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
                            flowProperties.add(FlowPropertyInfo(
                                propertyName = propertyName,
                                keyName = keyName
                            ))

                            // Private backing MutableStateFlow
                            addProperty(PropertySpec.builder(
                                "_${propertyName}Flow",
                                mutableStateFlowType.parameterizedBy(propertyType.copy(nullable = isNullable))
                            )
                                .addModifiers(KModifier.PRIVATE)
                                .initializer("MutableStateFlow($flowGetterCode)")
                                .build())

                            // Public read-only StateFlow
                            addProperty(PropertySpec.builder(
                                "${propertyName}Flow",
                                stateFlowType.parameterizedBy(propertyType.copy(nullable = isNullable))
                            )
                                .initializer("_${propertyName}Flow.asStateFlow()")
                                .build())
                        }
                    }
                }

                // Generate flow listener infrastructure if any @Flow properties exist
                if (flowProperties.isNotEmpty()) {
                    val listenerType = ClassName("android.content", "SharedPreferences", "OnSharedPreferenceChangeListener")

                    // Private listener field - uses property getter to avoid code duplication and line-wrapping issues
                    addProperty(PropertySpec.builder("_flowPreferenceListener", listenerType)
                        .addModifiers(KModifier.PRIVATE)
                        .initializer(CodeBlock.builder()
                            .add("SharedPreferences.OnSharedPreferenceChangeListener { _, key ->\n")
                            .indent()
                            .add("when (key) {\n")
                            .indent()
                            .apply {
                                flowProperties.forEach { prop ->
                                    add("${prop.keyName} -> _${prop.propertyName}Flow.value = ${prop.propertyName}\n")
                                }
                            }
                            .unindent()
                            .add("}\n")
                            .unindent()
                            .add("}")
                            .build())
                        .build())

                    // Register method
                    addFunction(FunSpec.builder("registerFlowListeners")
                        .addStatement("sp.registerOnSharedPreferenceChangeListener(_flowPreferenceListener)")
                        .build())

                    // Unregister method
                    addFunction(FunSpec.builder("unregisterFlowListeners")
                        .addStatement("sp.unregisterOnSharedPreferenceChangeListener(_flowPreferenceListener)")
                        .build())
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
