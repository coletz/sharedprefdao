SharedPrefDao

Add to your project:

```
dependencies {
    ...
    implementation 'com.github.dcoletto.sharedprefdao:sharedprefdao-annotation:0.0.2'
    kapt 'com.github.dcoletto.sharedprefdao:sharedprefdao-processor:0.0.2'
    ...
}
```
Don't forget to `apply plugin: 'kotlin-kapt'` and add `maven { url "https://jitpack.io" }` to your repositories

Usage:

1. Annotate an interface with `@SharedPrefDao`
2. Add `var` for each key you want in your Dao
3. Set default values for your entries with `@get:DefaultValue("myDefaultValue")`
4. In order to set an empty string as default value, use EMPTY_STRING constant;

```
@SharedPrefDao(name = AppConfig.SHARED_PREF_NAME)
interface SettingsDao {

    @get:DefaultValue("5")
    var volume: Int?
    @get:DefaultValue("PlayerOne")
    var username: String?
    @get:DefaultValue("true")
    var shouldShowTutorial: Boolean
}
```
