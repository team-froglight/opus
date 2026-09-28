import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.api.tasks.SourceSetContainer
import org.slf4j.event.Level

plugins {
    java
    id("net.neoforged.moddev")
}

// Library modules must be evaluated before we read their source sets below.
evaluationDependsOn(":opus-core")
evaluationDependsOn(":opus-yoga")
evaluationDependsOn(":opus-minecraft")
evaluationDependsOn(":opus-neoforge")

group = providers.gradleProperty("mod_group_id").get()
version = providers.gradleProperty("showcase_mod_version").get()
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
            // Library modules ride inside the mod's own module layer (dev runs)
            // so FML's layer can see both them and Minecraft classes together.
            // (Plain project jars on the run classpath land on a parent layer
            // that cannot see MC -> NoClassDefFoundError Screen.)
            sourceSet(ss(":opus-core"))
            sourceSet(ss(":opus-yoga"))
            sourceSet(ss(":opus-minecraft"))
            sourceSet(ss(":opus-neoforge"))
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

    // MDG 2.0 on MC <= 1.21.8: plain library jars are NOT on run/test classpaths
    // and are NOT embedded in the mod jar. jarJar embeds them for players;
    // additionalRuntimeClasspath puts them on dev-run classpaths (else
    // ClassNotFoundException, see MDG docs "External Dependencies: Runs").
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
    add("additionalRuntimeClasspath", "org.lwjgl:lwjgl-yoga:${providers.gradleProperty("lwjgl_version").get()}:$natives")
}

// Library assets must also be visible as resources of the consuming mod.
tasks.processResources { from(ss(":opus-minecraft").resources) }
