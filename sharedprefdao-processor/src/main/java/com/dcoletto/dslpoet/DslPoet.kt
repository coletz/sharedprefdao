package com.dcoletto.dslpoet

import com.squareup.kotlinpoet.*
import java.lang.reflect.Type
import javax.annotation.processing.Filer
import javax.lang.model.type.TypeMirror
import javax.tools.StandardLocation

// Create a standalone class
fun file(pkg: String, name: String, f: FileSpec.Builder.() -> Unit = {})
        = FileSpec.builder(pkg, name)
        .also(f)
        .build()

// Create a standalone class
fun genKclass(name: String, f: TypeSpec.Builder.() -> Unit = {})
        = TypeSpec.classBuilder(name)
        .also(f)
        .build()

// Create a class inside a file
fun FileSpec.Builder.kclass(name: String, f: TypeSpec.Builder.() -> Unit = {})
        = genKclass(name, f)
        .also { this.addType(it) }

// Create a standalone companion object
fun genCompanion(name: String? = null, f: TypeSpec.Builder.() -> Unit = {})
        = TypeSpec.companionObjectBuilder(name)
        .also(f)
        .build()

// Create a companion object inside a class or other typespec
fun TypeSpec.Builder.companion(name: String? = null, f: TypeSpec.Builder.() -> Unit = {})
        = genCompanion(name, f)
        .also { this.addType(it) }

// Set the primary constructor without any parameter
fun TypeSpec.Builder.constructor(c: FunSpec.Builder.() -> Unit = {}) {
    this.primaryConstructor(FunSpec.constructorBuilder().apply(c).build())
}

/** FUNCTIONS **/
// Create a standalone function that returns a type specified by T
inline fun <reified T> genFunction(name: String, f: FunSpec.Builder.() -> Unit = {}): FunSpec {
    return FunSpec.builder(name)
            .returns(T::class)
            .also(f)
            .build()
}

// Create a standalone function that returns a type specified by T
inline fun genFunction(name: String, type: TypeName, f: FunSpec.Builder.() -> Unit = {}): FunSpec {
    return FunSpec.builder(name)
            .returns(type.javaToKotlinType())
            .also(f)
            .build()
}

// Create a function that returns a type specified by T inside a companion object or other typespec
inline fun <reified T> TypeSpec.Builder.function(name: String, f: FunSpec.Builder.() -> Unit = {})
        = genFunction<T>(name, f)
        .also { this.addFunction(it) }

// Create a function that returns a type specified by T inside a companion object or other typespec
inline fun TypeSpec.Builder.function(name: String, type: TypeName, f: FunSpec.Builder.() -> Unit = {})
        = genFunction(name, type, f)
        .also { this.addFunction(it) }

// Create a standalone void function
fun genVoidFunction(name: String, f: FunSpec.Builder.() -> Unit = {}): FunSpec {
    return FunSpec.builder(name)
            .also(f)
            .build()
}

// Create a void function inside a companion object or other typespec
fun TypeSpec.Builder.voidFunction(name: String, f: FunSpec.Builder.() -> Unit = {})
        = genVoidFunction(name, f)
        .also { this.addFunction(it) }

/** PROPERTY W/ GETTER/SETTER **/

// Create a property that returns a type specified by T inside a companion object or other typespec
inline fun <reified T> TypeSpec.Builder.property(name: String, p: PropertySpec.Builder.() -> Unit = {})
        = genProperty(name, T::class.java.asTypeName().javaToKotlinType(), p)
        .also { this.addProperty(it) }

// Create a property inside a companion object or other typespec
fun TypeSpec.Builder.property(name: String, type: TypeName, p: PropertySpec.Builder.() -> Unit = {})
        = genProperty(name, type, p)
        .also { this.addProperty(it) }

// Create a standalone property
inline fun genProperty(name: String, type: TypeName, p: PropertySpec.Builder.() -> Unit = {}): PropertySpec {
    return PropertySpec.builder(name, type.javaToKotlinType())
            .also(p)
            .build()
}

fun PropertySpec.Builder.getter(g: FunSpec.Builder.() -> Unit = {}) {
    val func = FunSpec.getterBuilder()
            .also(g)
            .build()
    getter(func)
}

inline fun <reified T> PropertySpec.Builder.setter(g: FunSpec.Builder.() -> Unit = {}) {
    val func = FunSpec.setterBuilder()
            .addParameter("value", T::class.java)
            .also(g)
            .build()
    setter(func)
}

fun PropertySpec.Builder.setter(type: TypeName, g: FunSpec.Builder.() -> Unit = {}) {
    val func = FunSpec.setterBuilder()
            .addParameter("value", type.javaToKotlinType())
            .also(g)
            .build()
    setter(func)
}

// Add code to the body of a function
fun FunSpec.Builder.code(format: String, vararg args: Any)
        = genCode(format, *args)
        .also { this.addCode(it) }

// Add code to the body of a function
fun genCode(format: String, vararg args: Any)
        = CodeBlock.builder()
        .addStatement(format, *args)
        .build()



// Write FileSpec to the specified Filer
// Usually filer can be obtained from processingEnv.filer
fun FileSpec.writeToFiler(filer: Filer): String {

    val kotlinFileObject = filer.createResource(StandardLocation.SOURCE_OUTPUT, this.packageName, "${this.name}.kt")

    val filePath = kotlinFileObject.openWriter().use {
        writeTo(it)
        kotlinFileObject.name
    }

    return filePath ?: throw Exception("Error while creating file ${this.name}.kt")
}