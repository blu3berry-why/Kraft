package hu.nova.blu3berry.kraft.processor.codegen

import com.squareup.kotlinpoet.ClassName
import hu.nova.blu3berry.kraft.model.TypeInfo

/** KotlinPoet [ClassName] bridge for code generation. */
val TypeInfo.className: ClassName get() = ClassName(packageName, simpleName)
