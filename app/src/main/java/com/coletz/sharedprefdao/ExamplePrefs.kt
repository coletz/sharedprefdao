package com.coletz.sharedprefdao

import com.coletz.sharedprefdao.annotation.DefaultValue
import com.coletz.sharedprefdao.annotation.SharedPrefDao

enum class SampleEnum {
    FIRST,
    SECOND,
    THIRD
}

@SharedPrefDao
interface ExamplePrefs {
    // Int
    @get:DefaultValue("5")
    val sampleInt: Int?
    var sampleIntNonNull: Int

    // Long
    @get:DefaultValue("100")
    val sampleLong: Long?
    var sampleLongNonNull: Long

    // Float
    @get:DefaultValue("3.14")
    val sampleFloat: Float?
    var sampleFloatNonNull: Float

    // Boolean
    @get:DefaultValue("true")
    val sampleBoolean: Boolean?
    var sampleBooleanNonNull: Boolean

    // String
    @get:DefaultValue("hello")
    val sampleString: String?
    var sampleStringNonNull: String

    // Set<String>
    val sampleStringSet: Set<String>?
    var sampleStringSetNonNull: Set<String>

    // Enum
    @get:DefaultValue("1")
    val sampleEnum: SampleEnum?
    var sampleEnumNonNull: SampleEnum
}
