package kotlinx.serialization

@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
annotation class Serializable

inline fun <reified T> kotlinx.serialization.json.Json.encodeToString(value: T): String =
    encodeValueToString(value)
