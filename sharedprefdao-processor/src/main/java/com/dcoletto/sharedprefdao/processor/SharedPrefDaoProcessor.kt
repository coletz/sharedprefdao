package com.dcoletto.sharedprefdao.processor

import com.dcoletto.dslpoet.writeToFiler
import com.dcoletto.sharedprefdao.annotation.SharedPrefDao
import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.FileSpec
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.ElementKind

import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.tools.Diagnostic.Kind.*

@AutoService(Processor::class)
class SharedPrefDaoProcessor: AbstractProcessor() {

    private lateinit var messager: Messager
    private lateinit var filer: Filer

    private var annotatedClasses: ArrayList<Dao> = arrayListOf()

    @Synchronized
    override fun init(env: ProcessingEnvironment?) {
        env?.let {
            messager = it.messager
            filer = it.filer
        }
    }

    override fun getSupportedAnnotationTypes(): MutableSet<String> =
            mutableSetOf(SharedPrefDao::class.java.canonicalName)

    override fun getSupportedSourceVersion(): SourceVersion =
            SourceVersion.latestSupported()

    override fun process(set: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment?): Boolean {
        if(roundEnv == null || set == null) return false

        roundEnv.getElementsAnnotatedWith(SharedPrefDao::class.java)?.forEach { annotatedElement ->
            if(annotatedElement.kind !== ElementKind.INTERFACE){
                return true
            }

            val annotation = annotatedElement.getAnnotation(SharedPrefDao::class.java)

            annotatedClasses.add(Dao(annotatedElement as TypeElement, annotation))
        }

        try {
            annotatedClasses
                    .map(Dao::generateCode)
                    .forEach(::writeToFiler)


            annotatedClasses.clear()
        } catch (e: Exception) {
            messager.printMessage(ERROR, e.message)
        }

        return true
    }

    private fun writeToFiler(fileSpec: FileSpec) {
        fileSpec.writeToFiler(filer)
                .let { messager.printMessage(NOTE, "Wrote to $it") }
    }
}
