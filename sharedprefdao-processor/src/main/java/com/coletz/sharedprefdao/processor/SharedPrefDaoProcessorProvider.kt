package com.coletz.sharedprefdao.processor

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class SharedPrefDaoProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return SharedPrefDaoProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger
        )
    }
}
