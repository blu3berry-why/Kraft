package kraft.itest.producer2

import com.blu3berry.kraft.config.KraftConverter

// Deliberately registers the SAME (Int -> String) pair as :integration-tests:kmp-producer.
// Consumed only by :integration-tests:kmp-consumer-ambiguous to prove the classpath
// ambiguity error fires on the klib by-name discovery path.
@KraftConverter
fun Int.toTagFromInt(): String = "tag=$this"
