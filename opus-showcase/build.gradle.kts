import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.api.tasks.SourceSetContainer
import org.slf4j.event.Level

plugins {
    java
    id("net.neoforged.moddev")
}

// Resource assets are copied from the Minecraft adapter for the mod resource pack.
evaluationDependsOn(":opus-minecraft")

group = providers.gradleProperty("mod_group_id").get()
base {
    archivesName.set(providers.gradleProperty("showcase_mod_id").get())
}

fun gradleProp(name: String): String = providers.gradleProperty(name).get()

fun ss(path: String) = project(path).extensions.getByType<SourceSetContainer>()["main"]

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
    runs {
        configureEach {
            logLevel.set(Level.DEBUG)
        }
        create("client") {
            client()
        }
        create("server") {
            server()
            gameDirectory.set(file("runServer"))
            programArguments.add("--nogui")
        }
    }
    mods {
        create(gradleProp("showcase_mod_id")) {
            sourceSet(sourceSets["main"])
            // Load the built library jars, just like downstream consumers. Merging
            // library source sets here would hide incorrect FMLModType manifests.
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":opus-core"))
    implementation(project(":opus-yoga"))
    implementation(project(":opus-minecraft"))
    implementation(project(":opus-neoforge"))

    // FMLModType manifests make Opus jars discoverable in development runs;
    // jarJar embeds the same jars in the distributable mod.
    jarJar(project(":opus-core"))
    jarJar(project(":opus-yoga"))
    jarJar(project(":opus-minecraft"))
    jarJar(project(":opus-neoforge"))
    // kotlin-stdlib ships inside the mod jar too (no KFF dependency in v1).
    jarJar(libs.kotlin.stdlib)
    jarJar(libs.lwjgl.yoga)
    // External libs for dev runs (MDG "External Dependencies: Runs", MC 1.21.1).

    // Transitives the library modules need at runtime (kotlin-stdlib via
    // opus-core's api dep, lwjgl-yoga via opus-yoga's api dep).
    add("additionalRuntimeClasspath", libs.kotlin.stdlib)
    add("additionalRuntimeClasspath", libs.lwjgl.yoga)
    // Yoga natives for dev runs (harmless duplicate if vanilla ever ships them).
    val natives = rootProject.extra["lwjglNatives"] as String
    add("additionalRuntimeClasspath", "org.lwjgl:lwjgl-yoga:${libs.versions.lwjgl.get()}:$natives")
}

// Library assets must also be visible as resources of the consuming mod.
tasks.processResources {
    from(ss(":opus-minecraft").resources)
    val modVersion = project.version.toString()
    inputs.property("showcaseVersion", modVersion)
    filesMatching("META-INF/neoforge.mods.toml") { expand("mod_version" to modVersion) }
}
