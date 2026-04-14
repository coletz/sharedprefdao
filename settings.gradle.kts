pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "sharedprefdao"

include(":app")
include(":sharedprefdao-processor")
include(":sharedprefdao-annotation")
include(":sharedprefdao-editor")