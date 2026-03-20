package hu.nova.blu3berry.kraft.onclass
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class MapNested(
    val sourceName: String = ""   // "" means: same property name as the target
)