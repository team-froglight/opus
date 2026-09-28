package opus.core

/** Marks a function as part of the Opus declarative UI tree (Jetpack Compose style). */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.BINARY)
annotation class Composable
