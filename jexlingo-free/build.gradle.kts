import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("jexsuite.shadow-conventions")
    id("jexsuite.dependencies-yml")
}

group = "de.jexcellence.lingo"
version = "0.3.0"

dependenciesYml {
    usePaperDependencies()
    generatePaperVariant.set(true)
    generateSpigotVariant.set(true)
}

dependencies {
    // ── Implementation ──
    implementation(project(":JExLingo:jexlingo-common"))
    implementation(project(":JExLingo:jexlingo-api"))
    implementation(libs.jehibernate) { isTransitive = false }
    implementation(libs.bundles.jexcellence) {
        isTransitive = false
        exclude(group = "de.jexcellence.hibernate")
    }
    implementation(libs.bundles.jeconfig) { isTransitive = false }

    // ── Compile-only: Server API ──
    compileOnly(libs.paper.api)
    compileOnly(libs.slf4j.api)
    compileOnly(libs.jboss.logging)
    compileOnly(platform(libs.hibernate.platform))
    compileOnly(libs.bundles.hibernate)
    compileOnly(libs.adventure.platform.bukkit)
    compileOnly(libs.bundles.inventory)
}

tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName.set("JExLingo")
    archiveClassifier.set("Free")
    archiveVersion.set(project.version.toString())

    relocate("tools.jackson", "de.jexcellence.remapped.tools.jackson")
    relocate("me.devnatan.inventoryframework", "de.jexcellence.remapped.me.devnatan.inventoryframework")
    relocate("com.tcoded", "de.jexcellence.remapped.com.tcoded")
    relocate("com.cryptomorin.xseries", "de.jexcellence.remapped.com.cryptomorin.xseries")

    configurations = listOf(project.configurations.getByName("runtimeClasspath"))
    mergeServiceFiles()
}

tasks.named("build") {
    dependsOn(tasks.named("shadowJar"))
}

tasks.named<Jar>("jar") {
    enabled = false
}

afterEvaluate {
    publishing {
        publications {
            remove(findByName("maven"))
            create<MavenPublication>("mavenShadow") {
                from(components["shadow"])
                groupId = "de.jexcellence.lingo"
                artifactId = "jexlingo-free"
                version = project.version.toString()
                pom {
                    name.set("JExLingo Free")
                    description.set("JExLingo Free Edition")
                }
            }
        }
    }
}
