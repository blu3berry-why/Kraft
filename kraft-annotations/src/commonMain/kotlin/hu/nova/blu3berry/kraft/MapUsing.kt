package hu.nova.blu3berry.kraft

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class MapUsing(
    val from: String,  // source property name
    val to: String     // target property name
)