import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    `java-library`
    id("net.neoforged.moddev")
}

group = providers.gradleProperty("mod_group_id").get()

fun gradleProp(name: String): String = providers.gradleProperty(name).get()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

neoForge {
    version = gradleProp("neo_version")
    parchment {
        minecraftVersion.set(gradleProp("parchment_minecraft_version"))
        mappingsVersion.set(gradleProp("parchment_version"))
    }
}

dependencies {
    api(project(":opus-core"))
    api(project(":opus-yoga"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation("net.java.dev.jna:jna:5.14.0")
    testImplementation("net.java.dev.jna:jna-platform:5.14.0")
    val lwjglVersion = providers.gradleProperty("lwjgl_version").get()
    val natives = rootProject.extra["lwjglNatives"] as String
    // ModDev's game libraries are not automatically on the test compile classpath.
    testImplementation("org.lwjgl:lwjgl:$lwjglVersion")
    testImplementation("org.lwjgl:lwjgl-stb:$lwjglVersion")
    testRuntimeOnly("org.lwjgl:lwjgl:$lwjglVersion:$natives")
    testRuntimeOnly("org.lwjgl:lwjgl-stb:$lwjglVersion:$natives")
}

tasks.withType<Test> { useJUnitPlatform() }
