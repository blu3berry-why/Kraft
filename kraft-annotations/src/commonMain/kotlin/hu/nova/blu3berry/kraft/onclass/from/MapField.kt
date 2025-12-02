package hu.nova.blu3berry.kraft.onclass.from

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class MapField(
    val otherName: String
)