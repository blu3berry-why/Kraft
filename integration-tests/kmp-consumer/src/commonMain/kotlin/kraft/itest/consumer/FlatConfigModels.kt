package kraft.itest.consumer

import com.blu3berry.kraft.config.FieldMapping
import com.blu3berry.kraft.config.MapConfig
import com.blu3berry.kraft.config.MapConstant
import com.blu3berry.kraft.config.MapIgnoreField

data class FlatSrc(val fullName: String, val secret: String)

data class FlatDst(val name: String, val secret: String = "none", val currency: String)

@MapConfig(source = FlatSrc::class, target = FlatDst::class)
@FieldMapping(source = "fullName", target = "name")
@MapIgnoreField("secret")
object FlatSrcMapper {
    @MapConstant
    val currency = "HUF"
}
