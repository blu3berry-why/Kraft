package com.blu3berry.kraft.nested

import com.google.common.truth.Truth.assertThat
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.sourcesGeneratedBySymbolProcessor
import com.blu3berry.kraft.TestKspRunner
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCompilerApi::class)
class CrossPackageNestedMappingTest {

    @Test
    fun `parent mapper imports nested mapper that lives in a different package`() {
        val outer = SourceFile.kotlin(
            "Outer.kt",
            """
            package com.example.outer

            import com.example.address.AddressSource

            data class PersonSource(val name: String, val address: AddressSource)

            data class AddressDto(val street: String)

            @com.blu3berry.kraft.mapping.MapFrom(PersonSource::class)
            data class PersonDto(val name: String, val address: AddressDto)
            """
        )

        val inner = SourceFile.kotlin(
            "Inner.kt",
            """
            package com.example.address

            data class AddressSource(val street: String)
            """
        )

        val generated = TestKspRunner.compileAndReturnGenerated(outer, inner)
        val parentFile = generated.single { it.name.startsWith("PersonSourceToPersonDtoMapper") }
        val parentContent = parentFile.readText()

        assertThat(parentContent).contains("import com.example.address.generated.toAddressDto")
        assertThat(parentContent).contains("address = this.address.toAddressDto()")
    }

    @Test
    fun `parent mapper compiles when two nested mappers from different packages share a name`() {
        // Re-Claw field report A2: `User.toUser()` and `AuthResponse.User.toUser()` live in
        // different generated packages under the same simple name. A parent that calls both
        // must import both; one import alone leaves the other call unresolved.
        val domain = SourceFile.kotlin(
            "Domain.kt",
            """
            package com.example.domain

            data class User(val name: String)

            data class Session(val owner: User, val actor: User)
            """
        )
        val api = SourceFile.kotlin(
            "Api.kt",
            """
            package com.example.api

            import com.example.auth.AuthResponse

            data class User(val name: String)

            @com.blu3berry.kraft.mapping.MapTo(com.example.domain.Session::class)
            data class SessionDto(val owner: User, val actor: AuthResponse.User)
            """
        )
        val auth = SourceFile.kotlin(
            "Auth.kt",
            """
            package com.example.auth

            class AuthResponse {
                data class User(val name: String)
            }
            """
        )

        val result = TestKspRunner.compile(domain, api, auth)
        val parentContent = result.sourcesGeneratedBySymbolProcessor
            .single { it.name.startsWith("SessionDtoToSessionMapper") }
            .readText()

        // The same-package mapper keeps its simple name; the foreign one is imported under an
        // alias. The alias spelling is KotlinPoet's, so only its presence is asserted.
        assertThat(parentContent).contains("import com.example.auth.generated.toUser as ")
        assertThat(parentContent).doesNotContain("import com.example.api.generated.toUser")
        assertThat(parentContent).contains("owner = this.owner.toUser()")
        assertThat(parentContent).doesNotContain("this.actor.com.")
        assertThat(result.messages).doesNotContain("Unresolved reference")
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }

    @Test
    fun `both same-name nested mappers are aliased when neither is in the parent's package`() {
        val models = SourceFile.kotlin(
            "Models.kt",
            """
            package com.example.domain

            data class User(val name: String)

            data class Session(val owner: User, val actor: User)
            """
        )
        val api = SourceFile.kotlin(
            "Api.kt",
            """
            package com.example.api

            data class User(val name: String)
            """
        )
        val auth = SourceFile.kotlin(
            "Auth.kt",
            """
            package com.example.auth

            class AuthResponse {
                data class User(val name: String)
            }
            """
        )
        val session = SourceFile.kotlin(
            "Session.kt",
            """
            package com.example.session

            @com.blu3berry.kraft.mapping.MapTo(com.example.domain.Session::class)
            data class SessionDto(
                val owner: com.example.api.User,
                val actor: com.example.auth.AuthResponse.User,
            )
            """
        )

        val result = TestKspRunner.compile(models, api, auth, session)
        val parentContent = result.sourcesGeneratedBySymbolProcessor
            .single { it.name.startsWith("SessionDtoToSessionMapper") }
            .readText()

        assertThat(parentContent).contains("import com.example.api.generated.toUser as ")
        assertThat(parentContent).contains("import com.example.auth.generated.toUser as ")
        assertThat(parentContent).doesNotContain("this.owner.com.")
        assertThat(parentContent).doesNotContain("this.actor.com.")
        assertThat(result.messages).doesNotContain("Unresolved reference")
        assertThat(result.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    }
}
