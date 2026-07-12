plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.jetbrainsKotlinJvm) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.kover)
}

// Aggregate coverage from the library modules into the root Kover report;
// kraft-ksp's jvmTest exercises kraft-core, so both count toward coverage.
dependencies {
    kover(project(":kraft-core"))
    kover(project(":kraft-ksp"))
}

sonar {
    properties {
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            layout.buildDirectory.file("reports/kover/report.xml").get().asFile.path,
        )
    }
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(files("${rootProject.projectDir}/detekt.yml"))
        parallel = true
    }

    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        reports {
            xml.required.set(true)
            html.required.set(true)
            sarif.required.set(false)
        }
    }
}