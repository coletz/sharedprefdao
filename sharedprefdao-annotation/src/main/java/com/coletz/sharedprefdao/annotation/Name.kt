package com.coletz.sharedprefdao.annotation

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.PROPERTY_GETTER)
annotation class Name(val value: String)
