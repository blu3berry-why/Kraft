package com.blu3berry.kraft.model.descriptor

/**
 * Indicates which class carries the mapping annotation, which determines
 * which end of the mapper pair is source and which is target.
 */
enum class MappingDirection {
    MAP_FROM, // @MapFrom – annotated class is TARGET
    MAP_TO    // @MapTo   – annotated class is SOURCE
}
