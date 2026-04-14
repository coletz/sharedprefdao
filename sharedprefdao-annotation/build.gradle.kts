plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.maven.publish)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.coletz.sharedprefdao"
            artifactId = "annotation"
            version = libs.versions.lib.version.get()

            afterEvaluate {
                from(components["java"])
            }
        }
    }
}