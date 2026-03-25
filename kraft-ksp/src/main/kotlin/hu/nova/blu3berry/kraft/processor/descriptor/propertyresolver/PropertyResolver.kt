package hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver

import hu.nova.blu3berry.kraft.model.descriptor.MappingContext
import hu.nova.blu3berry.kraft.model.PropertyInfo
import hu.nova.blu3berry.kraft.model.descriptor.PropertyMappingStrategy
import hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver.MappingRule
import hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver.rules.ClassOverrideRule
import hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver.rules.ConfigOverrideRule
import hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver.rules.ConverterRule
import hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver.rules.DirectMatchRule
import hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver.rules.IgnoreRule
import hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver.rules.NestedRule
import hu.nova.blu3berry.kraft.processor.descriptor.propertyresolver.rules.RequiredFieldErrorRule

class PropertyResolver(private val rules: List<MappingRule> = default()) {

    fun resolve(
        target: PropertyInfo,
        ctx: MappingContext
    ): PropertyMappingStrategy? {

        for (rule in rules) {
            val result = rule.tryResolve(target, ctx)
            if (result != null) return result
        }

        return null
    }

    companion object {
        /**
         * Rule evaluation order — the first rule that returns non-null wins.
         * DO NOT reorder without understanding the constraints below.
         *
         * 1. [ConverterRule]         — explicit @MapUsing overrides must be checked before any
         *                              name-based rule; otherwise DirectMatchRule could silently
         *                              win on a same-named property and skip the converter.
         *
         * 2. [IgnoreRule]            — checks ignoredProperties (populated from @MapIgnore
         *                              and @MapIgnoreField); must run before ConfigOverrideRule so
         *                              an ignored property is never mistakenly treated as a
         *                              source-name override.
         *
         * 3. [NestedRule]            — must claim a property whose type matches a nested mapping
         *                              before DirectMatchRule tries to copy the object directly
         *                              by name, which would produce a type-mismatch error.
         *
         * 4. [ClassOverrideRule]     — annotation-level renames (@MapField); evaluated before
         *                              ConfigOverrideRule so annotation-level intent wins when
         *                              both sources declare an override for the same property.
         *
         * 5. [ConfigOverrideRule]    — config-object-level renames; must precede DirectMatchRule
         *                              so explicitly remapped fields are not also matched by name.
         *
         * 6. [DirectMatchRule]       — automatic name + type match; runs after all explicit rules
         *                              have had the opportunity to claim the property.
         *
         * 7. [RequiredFieldErrorRule] — catch-all sentinel; MUST be last. Emits a KSP error for
         *                              any required (non-null, no default) property that no earlier
         *                              rule could resolve. Moving it earlier would silence valid
         *                              matches that appear after it in the list.
         */
        fun default() = listOf(
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
