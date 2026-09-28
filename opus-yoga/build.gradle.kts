plugins {
    `java-library`
    id("org.jetbrains.kotlin.jvm")
}

group = providers.gradleProperty("mod_group_id").get()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    api(project(":opus-core"))
    api(libs.lwjgl.yoga)
    // Yoga is not part of Minecraft's LWJGL distribution.
    val lwjglVersion = providers.gradleProperty("lwjgl_version").get()
    val natives = rootProject.extra["lwjglNatives"] as String
    runtimeOnly("org.lwjgl:lwjgl-yoga:$lwjglVersion:$natives")
    testRuntimeOnly("org.lwjgl:lwjgl:$lwjglVersion:$natives")
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
    testRuntimeOnly(libs.junit.launcher)
}

// Include Yoga's native resources in the library so the same mod artifact works on each desktop OS.
val bundledNatives by configurations.creating { isTransitive = false }
dependencies {
    for (platform in listOf("windows", "windows-arm64", "linux", "linux-arm64", "macos", "macos-arm64")) {
        bundledNatives("org.lwjgl:lwjgl-yoga:${providers.gradleProperty("lwjgl_version").get()}:natives-$platform")
    }
}
tasks.jar {
    from({ bundledNatives.map { zipTree(it) } }) {
        exclude("META-INF/MANIFEST.MF", "META-INF/INDEX.LIST", "META-INF/versions/**", "module-info.class")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
