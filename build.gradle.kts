plugins {
    `maven-publish`
    id("jexsuite.shadow-conventions")
    id("jexsuite.dependencies-yml")
}

group = "de.jexcellence.lingo"
version = "0.3.0"
description = "JExLingo - live chat translation on a self-hosted LibreTranslate instance"

ext["vendor"] = "JExcellence"

subprojects {
    group = rootProject.group
    version = rootProject.version
}

tasks.register("buildAll") {
    group = "build"
    description = "Builds both Free and Premium editions"
    dependsOn(
        ":JExLingo:jexlingo-api:build",
        ":JExLingo:jexlingo-common:build",
        ":JExLingo:jexlingo-free:shadowJar",
        ":JExLingo:jexlingo-premium:shadowJar"
    )
}

tasks.register("publishLocal") {
    group = "publishing"
    description = "Publishes all modules to local Maven repository"
    dependsOn(
        ":JExLingo:jexlingo-api:publishMavenPublicationToMavenLocal",
        ":JExLingo:jexlingo-common:publishMavenPublicationToMavenLocal",
        ":JExLingo:jexlingo-free:publishMavenShadowPublicationToMavenLocal",
        ":JExLingo:jexlingo-premium:publishMavenShadowPublicationToMavenLocal",
    )
}
