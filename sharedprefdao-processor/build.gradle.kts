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

    implementation(kotlin("reflect"))
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.coletz.sharedprefdao"
            artifactId = "processor"
            version = libs.versions.lib.version.get()

            afterEvaluate {
                from(components["java"])
            }
        }
    }
}