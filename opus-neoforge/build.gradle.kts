plugins {
    `java-library`
    id("net.neoforged.moddev")
}

group = providers.gradleProperty("mod_group_id").get()
java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }
neoForge { version = providers.gradleProperty("neo_version").get() }
dependencies { api(project(":opus-minecraft")) }
