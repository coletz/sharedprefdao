package com.coletz.sharedprefdao

import com.coletz.sharedprefdao.annotation.DefaultValue
import com.coletz.sharedprefdao.annotation.Name
import com.coletz.sharedprefdao.annotation.NumericId
import com.coletz.sharedprefdao.annotation.SharedPrefDao
import com.coletz.sharedprefdao.annotation.StringId

enum class SampleEnum(val id: Int, val label: String) {
    FIRST(10, "first_label"),
    SECOND(20, "second_label"),
    THIRD(30, "third_label")
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

    // Enum (ordinal - default)
    @get:DefaultValue("1")
    val sampleEnum: SampleEnum?
    var sampleEnumNonNull: SampleEnum

    // Enum with @NumericId
    @get:NumericId("id")
    @get:DefaultValue("SECOND")
    val sampleEnumNumericId: SampleEnum?
    @get:NumericId("id")
    var sampleEnumNumericIdNonNull: SampleEnum

    // Enum with @StringId
    @get:StringId("label")
    @get:DefaultValue("THIRD")
    val sampleEnumStringId: SampleEnum?
    @get:StringId("label")
    var sampleEnumStringIdNonNull: SampleEnum

    // Enum with @NumericId without default
    @get:NumericId("id")
    val sampleEnumNumericIdWithoutDefault: SampleEnum?

    // Enum with @StringId
    @get:StringId("label")
    val sampleEnumStringIdWithoutDefault: SampleEnum?

    // Custom key name
    @get:Name("user_display_name")
    var customNamedPref: String
}
