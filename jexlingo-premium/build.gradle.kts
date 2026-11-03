import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("jexsuite.shadow-conventions")
    id("jexsuite.dependencies-yml")
}

group = "de.jexcellence.lingo"
version = libs.versions.jexlingo.get()

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
    archiveClassifier.set("Premium")
    archiveVersion.set(project.version.toString())

    relocate("tools.jackson", "de.jexcellence.remapped.tools.jackson")
    relocate("me.devnatan.inventoryframework", "de.jexcellence.remapped.me.devnatan.inventoryframework")
    relocate("com.tcoded", "de.jexcellence.remapped.com.tcoded")
    relocate("com.cryptomorin.xseries", "de.jexcellence.remapped.com.cryptomorin.xseries")

    configurations = listOf(project.configurations.getByName("runtimeClasspath"))
    mergeServiceFiles()

    // Resources of bundled libraries that JExLingo never reads: their demo translations, an unused command file,
    // a grammar file and a log4j config. Only JExLingo's own en_US and de_DE translations ship.
    exclude(
        "translations/da_DK.yml", "translations/es_ES.yml", "translations/ja_JP.yml", "translations/ko_KR.yml",
        "translations/nl_NL.yml", "translations/no_NO.yml", "translations/pl_PL.yml", "translations/pt_BR.yml",
        "translations/ru_RU.yml", "translations/sv_SE.yml", "translations/tr_TR.yml", "translations/zh_CN.yml",
        "translations/zh_TW.yml", "commands/pr18n.yml", "grammar.ebnf", "log4j.properties"
    )
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
                artifactId = "jexlingo-premium"
                version = project.version.toString()
                pom {
                    name.set("JExLingo Premium")
                    description.set("JExLingo Premium Edition")
                }
            }
        }
    }
}
