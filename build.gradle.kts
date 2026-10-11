import org.gradle.api.publish.maven.MavenPublication

plugins {
    `maven-publish`
    `version-catalog`
}

group = providers.gradleProperty("mod_group_id").get()
version = providers.gradleProperty("opus_version").get()

catalog { versionCatalog { from(files("gradle/libs.versions.toml")) } }
publishing {
    publications {
        create<MavenPublication>("catalog") {
            artifactId = "opus-catalog"
            from(components["versionCatalog"])
        }
    }
    repositories {
        maven { name = "Verification"; url = uri(layout.buildDirectory.dir("verification-maven")) }
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/team-froglight/opus")
            credentials {
                username = providers.gradleProperty("gpr.user").orElse(providers.environmentVariable("GITHUB_ACTOR")).orNull
                password = providers.gradleProperty("gpr.key").orElse(providers.environmentVariable("GITHUB_TOKEN")).orNull
            }
        }
    }
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
    version = if (name == "opus-showcase") providers.gradleProperty("showcase_mod_version").get() else rootProject.version
    repositories {
        mavenCentral()
    }
    if (name != "opus-showcase") {
        apply(plugin = "maven-publish")
        plugins.withId("java-library") {
            extensions.configure<JavaPluginExtension> { withSourcesJar() }
            tasks.withType<Jar>().configureEach {
                isPreserveFileTimestamps = false
                isReproducibleFileOrder = true
                manifest.attributes("FMLModType" to "LIBRARY", "Automatic-Module-Name" to project.name.replace('-', '.'))
            }
            extensions.configure<PublishingExtension> {
                publications {
                    create<MavenPublication>("library") {
                        from(components["java"])
                        pom {
                            name.set(project.name)
                            description.set("Opus declarative UI library for Minecraft 1.21.1 / NeoForge 21.1")
                            url.set("https://github.com/team-froglight/opus")
                            licenses { license { name.set("MIT"); url.set("https://opensource.org/licenses/MIT") } }
                            scm { url.set("https://github.com/team-froglight/opus") }
                        }
                    }
                }
                repositories {
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
            }
        }
    }
}
