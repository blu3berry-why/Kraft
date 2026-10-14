package kraft.itest.consumer

import com.blu3berry.kraft.config.MapConfig
import com.blu3berry.kraft.config.MapUsing

/**
 * Regression guard for B1: a whole-source `@MapUsing` that omits `source`.
 * In KMP metadata compilations KSP may leave defaulted arguments out of the
 * argument list; Kraft must apply the declared default instead of failing with
 * `Missing required argument 'source'`.
 */
data class WholeSourceDraft(val name: String, val tags: List<String>)

data class WholeSourceInput(val value: String)

data class WholeSourceRequest(val name: String, val inputs: List<WholeSourceInput>)

@MapConfig(source = WholeSourceDraft::class, target = WholeSourceRequest::class)
object WholeSourceMapper {
    @MapUsing(target = "inputs")
    fun WholeSourceDraft.toInputs(): List<WholeSourceInput> = tags.map { WholeSourceInput(it) }
}
