plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.maven.publish)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=com.google.devtools.ksp.KspExperimental")
    }
}

dependencies {
    implementation(project(":sharedprefdao-annotation"))

    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
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