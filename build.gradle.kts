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
    version = providers.gradleProperty("showcase_mod_version").get()
    repositories {
        mavenCentral()
    }
}
