package com.dcoletto.sharedprefdao.annotation

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class SharedPrefDao(val prefix: String = "SPD", val name: String = "")