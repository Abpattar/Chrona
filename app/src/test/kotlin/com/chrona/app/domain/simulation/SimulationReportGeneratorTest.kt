package com.chrona.app.domain.simulation

import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

/**
 * Generates the simulation report that ships with the repository.
 *
 * This is a real execution, not a recording: the harness runs every shipped scenario through the
 * production planner and the production engine, and every number below is counted from that run.
 * The output is written to `docs/` so it can be read and diffed alongside the code.
 *
 * It lives in the test source set on purpose — the simulation is domain logic, and a JVM run is
 * the honest way to produce evidence about it without a device attached.
 */
class SimulationReportGeneratorTest {

    @Test
    fun `writes the markdown and json report`() = runTest {
        val started = Instant.now()
        val result = SimulationHarness(
            environment = "JVM (Gradle testDebugUnitTest)",
            now = { started },
        ).run()
        val report = result.report

        val markdown = report.toMarkdown()
        val json = report.toJson()

        // Gradle runs unit tests with the module directory as the working directory, so a bare
        // "docs" would land in app/docs and be silently ignored. Walk up to the repository root.
        val directory = repositoryRoot().resolve("docs").apply { mkdirs() }
        directory.resolve("SIMULATION_REPORT.md").writeText(markdown)
        directory.resolve("SIMULATION_REPORT.json").writeText(json)

        // The report must not be able to claim success it did not achieve.
        check(report.allPassed) {
            "the generated report has failing checks:\n" +
                report.assertions.filter { it.status != AssertionStatus.PASSED }
                    .joinToString("\n") { "${it.scenario}/${it.name}: ${it.detail}" }
        }
        println(markdown)
    }

    /** The directory holding `settings.gradle.kts`, which is the repository root by definition. */
    private fun repositoryRoot(): java.io.File {
        var candidate = java.io.File(".").absoluteFile
        while (!candidate.resolve("settings.gradle.kts").isFile) {
            candidate = candidate.parentFile
                ?: error("no settings.gradle.kts above ${candidate.absolutePath}")
        }
        return candidate
    }
}
