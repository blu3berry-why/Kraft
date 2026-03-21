package hu.nova.blu3berry.kraft.mapping

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class MapField(
    val otherName: String
)
