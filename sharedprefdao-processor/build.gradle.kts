plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.maven.publish)
}

dependencies {
    implementation(project(":sharedprefdao-annotation"))

    kapt(libs.autoservice)
    implementation(libs.autoservice)

    implementation(libs.kotlinpoet)

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
}

publishing {
    publications {
        create<MavenPublication>("release") {
            from(components["java"])

            groupId = "com.coletz.sharedprefdao"
            artifactId = "processor"
            version = libs.versions.lib.version.get()
        }
    }
}