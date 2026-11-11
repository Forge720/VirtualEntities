plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.24"
}

group = "net.plexmc"
version = "0.1.0-SNAPSHOT"

repositories {
    // The optional provider APIs are published outside Maven Central.
    maven("https://mvn.lumine.io/repository/maven-public/")
}

dependencies {
    add("paperweightDevelopmentBundle", "io.papermc.paper:dev-bundle:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.ticxo.modelengine:ModelEngine:R4.1.0")
    compileOnly("io.lumine:Mythic-Dist:5.12.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}

java {
    // Use the JDK that launches Gradle. The project requires Java 21; avoiding
    // toolchain auto-download makes offline and managed build environments
    // deterministic. The compile task below enforces Java 21 bytecode.
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
    withJavadocJar()
}

tasks {
    compileJava {
        options.release.set(21)
        options.encoding = "UTF-8"
    }

    javadoc {
        source = fileTree("src/main/java/net/plexmc/virtualentities/api")
        options.encoding = "UTF-8"
        (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:none", true)
        (options as StandardJavadocDocletOptions).addStringOption("sourcepath", "")
    }

    processResources {
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }

    jar {
        archiveBaseName.set("VirtualEntities")
    }

    test {
        useJUnitPlatform()
    }
}

