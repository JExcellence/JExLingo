plugins {
    id("jexsuite.library-conventions")
    id("jexsuite.dependencies-yml")
}

group = "de.jexcellence.lingo"
version = "0.4.1"
description = "JExLingo Common - translation pipeline, chat, learning, persistence, commands and views"

dependenciesYml {
    usePaperDependencies()
    generatePaperVariant.set(true)
    generateSpigotVariant.set(true)
}

tasks.processResources {
    exclude("plugin.yml", "paper-plugin.yml")
}

dependencies {
    // ── Implementation ──
    implementation(project(":JExLingo:jexlingo-api"))

    // ── Compile-only: Server API and soft dependencies ──
    compileOnly(libs.paper.api)
    compileOnly(libs.bundles.adventure)
    compileOnly(libs.placeholderapi)
    compileOnly(libs.floodgate.api)

    // ── Compile-only: Logging ──
    compileOnly(libs.slf4j.api)
    compileOnly(libs.jboss.logging)

    // ── Compile-only: Database ──
    compileOnly(platform(libs.hibernate.platform))
    compileOnly(libs.bundles.hibernate)
    compileOnly(libs.jehibernate)

    // ── Compile-only: JExcellence Platform ──
    compileOnly(libs.bundles.jexcellence) {
        exclude(group = "de.jexcellence.hibernate")
        isTransitive = false
    }

    // ── Compile-only: Utilities ──
    compileOnly(libs.caffeine)

    // ── Test ──
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.paper.api)
    testImplementation(libs.caffeine)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}
