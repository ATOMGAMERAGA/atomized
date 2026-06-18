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

// Mechanical "no release without enough tests" gate (master plan §13). Sums executed
// (non-skipped) tests across every target's JUnit XML and fails below the threshold.
// Run after chiseledTest; override the floor with -Pmin_tests=N.
tasks.register("verifyTestCount") {
    group = "atomized"
    description = "Fails the build if fewer than -Pmin_tests (default 300) tests were executed."
    doLast {
        val minTests = (findProperty("min_tests") as String?)?.toIntOrNull() ?: 300
        val resultFiles = rootDir.resolve("versions").walkTopDown()
            .filter { it.isFile && it.name.startsWith("TEST-") && it.extension == "xml" }
            .filter { it.path.contains("test-results") }
            .toList()
        if (resultFiles.isEmpty()) {
            throw GradleException(
                "verifyTestCount found no JUnit results. Run 'chiseledTest' (or a target ':<v>:test') first."
            )
        }
        var executed = 0
        var failures = 0
        val testsRegex = Regex("""\btests="(\d+)"""")
        val skippedRegex = Regex("""\bskipped="(\d+)"""")
        val failuresRegex = Regex("""\bfailures="(\d+)"""")
        val errorsRegex = Regex("""\berrors="(\d+)"""")
        resultFiles.forEach { file ->
            // Read only the <testsuite ...> header attributes.
            val header = file.bufferedReader().useLines { lines ->
                lines.firstOrNull { it.contains("<testsuite ") } ?: ""
            }
            val total = testsRegex.find(header)?.groupValues?.get(1)?.toInt() ?: 0
            val skipped = skippedRegex.find(header)?.groupValues?.get(1)?.toInt() ?: 0
            executed += (total - skipped)
            failures += (failuresRegex.find(header)?.groupValues?.get(1)?.toInt() ?: 0)
            failures += (errorsRegex.find(header)?.groupValues?.get(1)?.toInt() ?: 0)
        }
        logger.lifecycle("verifyTestCount: $executed tests executed across ${resultFiles.size} result files ($failures failed).")
        if (failures > 0) {
            throw GradleException("verifyTestCount: $failures test(s) failed; refusing to pass the gate.")
        }
        if (executed < minTests) {
            throw GradleException("verifyTestCount: only $executed tests executed, need >= $minTests.")
        }
    }
}
