package com.coletz.sharedprefdao.processor

import com.coletz.sharedprefdao.annotation.SharedPrefDao
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.FileSpec

class SharedPrefDaoProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(SharedPrefDao::class.qualifiedName!!)

        val invalid = mutableListOf<KSAnnotated>()

        symbols.filterIsInstance<KSClassDeclaration>()
            .filter { it.classKind == ClassKind.INTERFACE }
            .forEach { classDeclaration ->
                try {
                    val dao = Dao(classDeclaration)
                    val fileSpec = dao.generateCode()
                    writeToCodeGenerator(fileSpec, classDeclaration)
                    logger.info("Generated ${fileSpec.name}.kt")
                } catch (e: Exception) {
                    logger.error("Error processing ${classDeclaration.simpleName.asString()}: ${e.message}", classDeclaration)
                    invalid.add(classDeclaration)
                }
            }

        return invalid
    }

    private fun writeToCodeGenerator(fileSpec: FileSpec, originatingDeclaration: KSClassDeclaration) {
        val containingFile = originatingDeclaration.containingFile
        val dependencies = if (containingFile != null) {
            Dependencies(aggregating = false, sources = arrayOf(containingFile))
        } else {
            Dependencies(aggregating = false)
        }

        codeGenerator.createNewFile(
            dependencies = dependencies,
            packageName = fileSpec.packageName,
            fileName = fileSpec.name
        ).bufferedWriter().use { writer ->
            fileSpec.writeTo(writer)
        }
    }
}
