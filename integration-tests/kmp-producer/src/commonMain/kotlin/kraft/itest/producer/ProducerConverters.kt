package kraft.itest.producer

import com.blu3berry.kraft.config.KraftConverter

@KraftConverter
fun Int.toLabelFromInt(): String = "n=$this"
