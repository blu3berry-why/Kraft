package hu.nova.blu3berry.kraft

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform