pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        exclusiveContent {
            forRepository { maven { name = "ReviewedNexvaryNpse"; url = uri("third_party/npse/maven") } }
            filter { includeGroup("com.nexvary.npse") }
        }
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
rootProject.name = "NEXVARYRecorderStudio"
include(":app")
