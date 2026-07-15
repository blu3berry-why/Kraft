package kraft.itest.consumer

import com.blu3berry.kraft.config.MapConfig

data class Src(val count: Int)

data class Dst(val count: String)

@MapConfig(source = Src::class, target = Dst::class)
object SrcMapper
