package com.blu3berry.kraft.sample

import com.blu3berry.kraft.config.MapConfig
import com.blu3berry.kraft.config.MapReverse
import com.blu3berry.kraft.sample.domain.Category
import com.blu3berry.kraft.sample.dto.CategoryDto
import com.blu3berry.kraft.sample.dto.generated.toDomain
import com.blu3berry.kraft.sample.domain.generated.toDto

@MapReverse
@MapConfig(source = CategoryDto::class, target = Category::class)
object CategoryMapper

@Suppress("unused")
internal fun demoCategoryAliases() {
    val dto = CategoryDto(id = 1, label = "Demo")
    val domain = dto.toDomain()       // alias from side `Domain`
    val backDto = domain.toDto()      // alias from side `Dto` via @MapReverse
    println("${dto.label} → ${domain.label} → ${backDto.label}")
}
