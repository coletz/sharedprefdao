package com.dcoletto.sharedprefdao.annotation

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.PROPERTY_GETTER)
annotation class DefaultValue(val value: String) {
    companion object {
        const val EMPTY_STRING = ""
    }
}