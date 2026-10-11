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
    val lwjglVersion = libs.versions.lwjgl.get()
    val natives = rootProject.extra["lwjglNatives"] as String
    // Native resources are bundled below; never publish a dependency on the publisher's host OS.
    testRuntimeOnly("org.lwjgl:lwjgl-yoga:$lwjglVersion:$natives")
    testRuntimeOnly("org.lwjgl:lwjgl:$lwjglVersion:$natives")
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
    testRuntimeOnly(libs.junit.launcher)
}

// Include Yoga's native resources in the library so the same mod artifact works on each desktop OS.
val bundledNatives = configurations.create("bundledNatives") { isTransitive = false }
dependencies {
    for (platform in listOf("windows", "windows-arm64", "linux", "linux-arm64", "macos", "macos-arm64")) {
        add(bundledNatives.name, "org.lwjgl:lwjgl-yoga:${libs.versions.lwjgl.get()}:natives-$platform")
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
