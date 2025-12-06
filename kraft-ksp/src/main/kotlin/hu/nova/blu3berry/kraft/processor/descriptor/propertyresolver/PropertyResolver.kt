import hu.nova.blu3berry.kraft.model.MappingContext
import hu.nova.blu3berry.kraft.model.PropertyInfo
import hu.nova.blu3berry.kraft.model.PropertyMappingStrategy
import hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver.MappingRule
import hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver.rules.ClassOverrideRule
import hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver.rules.ConfigOverrideRule
import hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver.rules.ConverterRule
import hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver.rules.DirectMatchRule
import hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver.rules.IgnoreRule
import hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver.rules.RequiredFieldErrorRule

class PropertyResolver(private val rules: List<MappingRule> = default()) {

    fun resolve(
        target: PropertyInfo,
        ctx: MappingContext
    ): PropertyMappingStrategy? {

        for (rule in rules) {
            println("Trying rule: ${rule::class.simpleName}")
            val result = rule.tryResolve(target, ctx)
            if (result != null) return result
        }

        return null
    }

    companion object {
        fun default()= listOf(
                ConverterRule(),
                IgnoreRule(),
                NestedRule(),
                ClassOverrideRule(),
                ConfigOverrideRule(),
                DirectMatchRule(),
                RequiredFieldErrorRule()
            )
    }
}
