package com.coletz.sharedprefdao.annotation

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.PROPERTY_GETTER)
annotation class NumericId(val value: String)