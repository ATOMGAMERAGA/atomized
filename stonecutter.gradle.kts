plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.21.11" /* [SC] DO NOT EDIT */

// Build the version subprojects sequentially to keep peak memory usage low
// (each target spins up its own Loom pipeline).
stonecutter tasks {
    order("build")
    order("test")
}

tasks.register("chiseledBuild") {
    group = "atomized"
    description = "Builds the mod jar for every supported Minecraft target."
    dependsOn(stonecutter.tasks.named("build").map { it.values })
}

tasks.register("chiseledTest") {
    group = "atomized"
    description = "Runs the unit test suite for every supported Minecraft target."
    dependsOn(stonecutter.tasks.named("test").map { it.values })
}
