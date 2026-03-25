package hu.nova.blu3berry.kraft.model.scan

import hu.nova.blu3berry.kraft.config.IgnoreSide

/** A single ignore declaration extracted from [@MapIgnoreField][hu.nova.blu3berry.kraft.config.MapIgnoreField]. */
data class IgnoredMappingConfig(
    val name: String,
    val direction: IgnoreSide
)
