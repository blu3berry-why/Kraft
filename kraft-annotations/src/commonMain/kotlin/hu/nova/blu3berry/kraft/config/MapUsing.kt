package hu.nova.blu3berry.kraft.config

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class MapUsing(
    val source: String,  // source property name
    val target: String   // target property name
)
