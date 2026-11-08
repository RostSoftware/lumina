pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "LUMINA"

include(":app")
include(
    ":core:designsystem",
    ":core:nostr",
    ":core:signer",
    ":core:data",
    ":core:media",
    ":core:wallet",
    ":core:ui",
)
include(
    ":feature:auth",
    ":feature:feed",
    ":feature:discover",
    ":feature:post",
    ":feature:create",
    ":feature:profile",
    ":feature:messages",
    ":feature:notifications",
    ":feature:settings",
)
