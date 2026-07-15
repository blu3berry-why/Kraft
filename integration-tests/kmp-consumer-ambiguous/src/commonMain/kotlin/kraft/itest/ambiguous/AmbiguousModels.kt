package kraft.itest.ambiguous

import com.blu3berry.kraft.config.MapConfig

// Needs the (Int -> String) converter that BOTH producers publish. Kraft must fail this
// module's KSP run with the classpath ambiguity error; CI asserts exactly that.
data class Src(val count: Int)

data class Dst(val count: String)

@MapConfig(source = Src::class, target = Dst::class)
object SrcMapper
