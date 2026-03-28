package com.blu3berry.kraft.model.scan

import com.blu3berry.kraft.config.IgnoreSide

/** A single ignore declaration extracted from [@MapIgnoreField][com.blu3berry.kraft.config.MapIgnoreField]. */
data class IgnoredMappingConfig(
    val name: String,
    val direction: IgnoreSide
)
