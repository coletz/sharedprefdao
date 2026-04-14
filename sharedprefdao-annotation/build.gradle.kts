plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.maven.publish)
}

dependencies {
    implementation(libs.kotlin.stdlib)
}

publishing {
    publications {
        create<MavenPublication>("release") {
            from(components["java"])

            groupId = "com.coletz.sharedprefdao"
            artifactId = "annotation"
            version = libs.versions.lib.version.get()
        }
    }
}