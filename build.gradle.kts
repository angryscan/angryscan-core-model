plugins {
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
}

subprojects {
    repositories {
        mavenCentral()
    }
}

nexusPublishing {
    repositories {
        sonatype {
            stagingProfileId.set("io.github.gammmaaaa")
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
        }
    }
}

// One command: publish Kotlin lib to Sonatype staging, then close and release to Maven Central.
tasks.register("publishToMavenCentral") {
    group = "publishing"
    description = "Publish modelaudit-kotlin to Sonatype staging and release to Maven Central"
    dependsOn(":modelaudit-kotlin:publishToSonatype", "closeAndReleaseSonatypeStagingRepository")
}
tasks.named("closeAndReleaseSonatypeStagingRepository").configure {
    mustRunAfter(":modelaudit-kotlin:publishToSonatype")
}
