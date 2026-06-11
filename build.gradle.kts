plugins {
    id("fabric-loom")
    id("java-library")
}

val mcVersion = property("minecraft_version").toString()
val mcRange = property("minecraft_range").toString()

// Base version comes from gradle.properties; CI may append a beta suffix
// (-Pversion_suffix) or replace it entirely (-Pversion_override).
val modVersion = findProperty("version_override")?.toString()
    ?: (property("mod_version").toString() + (findProperty("version_suffix")?.toString() ?: ""))

version = "$modVersion+mc$mcVersion"
group = property("maven_group").toString()

val archivesBaseName = property("archives_base_name").toString()

base {
    archivesName = archivesBaseName
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://api.modrinth.com/maven") {
        content { includeGroup("maven.modrinth") }
    }
    maven("https://maven.terraformersmc.com/releases") {
        content { includeGroup("com.terraformersmc") }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
}

loom {
    accessWidenerPath = rootProject.file("src/main/resources/atomized.accesswidener")
    runConfigs.all {
        ideConfigGenerated(false)
    }
}

tasks.processResources {
    inputs.property("version", version.toString())
    inputs.property("minecraft_range", mcRange)
    filesMatching("fabric.mod.json") {
        expand(mapOf("version" to version.toString(), "minecraft_range" to mcRange))
    }
}

tasks.jar {
    from(rootProject.file("LICENSE")) {
        rename { "${it}_$archivesBaseName" }
    }
}
