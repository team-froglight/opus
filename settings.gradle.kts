pluginManagement {
    val mdgVersion = providers.gradleProperty("mdg_plugin_version").get()
    val kotlinVersion = providers.gradleProperty("kotlin_plugin_version").get()
    val foojayVersion = providers.gradleProperty("foojay_plugin_version").get()

    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven {
            name = "NeoForged"
            setUrl("https://maven.neoforged.net/releases")
        }
    }

    plugins {
        id("net.neoforged.moddev") version mdgVersion
        id("org.jetbrains.kotlin.jvm") version kotlinVersion
        id("org.gradle.toolchains.foojay-resolver-convention") version foojayVersion
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention")
}

rootProject.name = "opus"
include("opus-core", "opus-yoga", "opus-minecraft", "opus-neoforge", "opus-showcase")
