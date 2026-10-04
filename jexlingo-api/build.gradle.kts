plugins {
    id("jexsuite.library-conventions")
}

group = "de.jexcellence.lingo"
version = "0.4.1"
description = "JExLingo API - translation service, provider SPI and events for other plugins"

dependencies {
    // ── Compile-only: Server API ──
    compileOnly(libs.paper.api)
}

afterEvaluate {
    publishing {
        publications {
            named<MavenPublication>("maven") {
                groupId = "de.jexcellence.lingo"
                artifactId = "jexlingo-api"
                version = project.version.toString()
                pom {
                    name.set("JExLingo API")
                    description.set("Public API for JExLingo chat translation")
                }
            }
        }
    }
}
