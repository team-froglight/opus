import org.gradle.api.publish.maven.MavenPublication
import java.util.jar.JarFile

abstract class VerifyLibraryManifest : DefaultTask() {
    @get:InputFile abstract val archive: RegularFileProperty
    @get:Input abstract val expectedModType: Property<String>
    @get:Input abstract val expectedModuleName: Property<String>

    @TaskAction fun verify() {
        JarFile(archive.get().asFile).use { jar ->
            val attributes = jar.manifest.mainAttributes
            check(attributes.getValue("FMLModType") == expectedModType.get()) {
                "${jar.name}: incorrect NeoForge module layer classification"
            }
            check(attributes.getValue("Automatic-Module-Name") == expectedModuleName.get()) {
                "${jar.name}: incorrect automatic module name"
            }
        }
    }
}

plugins {
    `maven-publish`
    `version-catalog`
    // Versioned once here from the catalog; modules apply it by id.
    alias(libs.plugins.kotlin.jvm) apply false
}

/** The local verification repository and GitHub Packages, shared by the catalog and every library. */
fun RepositoryHandler.publicationRepositories() {
    maven { name = "Verification"; url = uri(rootProject.layout.buildDirectory.dir("verification-maven")) }
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/team-froglight/opus")
        credentials {
            username = providers.gradleProperty("gpr.user").orElse(providers.environmentVariable("GITHUB_ACTOR")).orNull
            password = providers.gradleProperty("gpr.key").orElse(providers.environmentVariable("GITHUB_TOKEN")).orNull
        }
    }
}

group = providers.gradleProperty("mod_group_id").get()
val ciBuildNumber = providers.gradleProperty("ciBuildNumber").orNull
val ciBuildAttempt = providers.gradleProperty("ciBuildAttempt").orNull
require(ciBuildNumber != null || ciBuildAttempt == null) { "ciBuildAttempt requires ciBuildNumber" }
val ciVersionSuffix = ciBuildNumber?.let { number ->
    val attempt = ciBuildAttempt ?: "1"
    require(number.matches(Regex("[1-9][0-9]*")) && attempt.matches(Regex("[1-9][0-9]*"))) {
        "ciBuildNumber and ciBuildAttempt must be positive integers"
    }
    "-build.$number.$attempt"
} ?: ""
version = providers.gradleProperty("opus_version").get() + ciVersionSuffix

catalog { versionCatalog { from(files("gradle/libs.versions.toml")) } }
publishing {
    publications {
        create<MavenPublication>("catalog") {
            artifactId = "opus-catalog"
            from(components["versionCatalog"])
        }
    }
    repositories { publicationRepositories() }
}

// One host classifier for development and tests. Published Yoga jars carry all supported platforms.
val hostOs = System.getProperty("os.name").lowercase(java.util.Locale.ROOT)
val hostArch = System.getProperty("os.arch").lowercase(java.util.Locale.ROOT)
val nativeOs = when {
    hostOs.startsWith("windows") -> "windows"
    hostOs.contains("mac") || hostOs.contains("darwin") -> "macos"
    hostOs.contains("linux") -> "linux"
    else -> error("Unsupported native platform: $hostOs")
}
val nativeArch = when (hostArch) {
    "amd64", "x86_64" -> ""
    "aarch64", "arm64" -> "-arm64"
    else -> error("Supported architectures are x86_64 and arm64; got $hostArch")
}
extra["lwjglNatives"] = "natives-$nativeOs$nativeArch"

subprojects {
    version = if (name == "opus-showcase") providers.gradleProperty("showcase_mod_version").get() + ciVersionSuffix else rootProject.version
    repositories {
        mavenCentral()
    }
    if (name != "opus-showcase") {
        apply(plugin = "maven-publish")
        plugins.withId("java-library") {
            extensions.configure<JavaPluginExtension> { withSourcesJar() }
            val libraryModType = if (project.name in setOf("opus-minecraft", "opus-neoforge")) "GAMELIBRARY" else "LIBRARY"
            val libraryModuleName = project.name.replace('-', '.')
            tasks.withType<Jar>().configureEach {
                isPreserveFileTimestamps = false
                isReproducibleFileOrder = true
                // Game adapters must share the TRANSFORMER layer with Minecraft/NeoForge.
                // LIBRARY is the parent PLUGIN layer and cannot read game modules.
                manifest.attributes("FMLModType" to libraryModType, "Automatic-Module-Name" to libraryModuleName)
            }
            val verifyManifest = tasks.register<VerifyLibraryManifest>("verifyLibraryManifest") {
                archive.set(tasks.named<Jar>("jar").flatMap { it.archiveFile })
                expectedModType.set(libraryModType)
                expectedModuleName.set(libraryModuleName)
            }
            tasks.named("check") { dependsOn(verifyManifest) }
            extensions.configure<PublishingExtension> {
                publications {
                    create<MavenPublication>("library") {
                        from(components["java"])
                        pom {
                            name.set(project.name)
                            val minecraft = providers.gradleProperty("minecraft_version").get()
                            val neoForge = providers.gradleProperty("neo_version").get().substringBeforeLast('.')
                            description.set("Opus declarative UI library for Minecraft $minecraft / NeoForge $neoForge")
                            url.set("https://github.com/team-froglight/opus")
                            val licenseName = providers.gradleProperty("mod_license").get()
                            licenses { license { name.set(licenseName); url.set("https://opensource.org/licenses/$licenseName") } }
                            scm { url.set("https://github.com/team-froglight/opus") }
                        }
                    }
                }
                repositories { publicationRepositories() }
            }
        }
    }
}
