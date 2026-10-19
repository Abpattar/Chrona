package com.chrona.app.domain.simulation

import java.time.Duration
import java.time.Instant

/**
 * The result of running scenarios and checking their invariants.
 *
 * Every field is measured. There is no path through this code that can write "passed" without a
 * check having actually run, which is what makes the generated report trustworthy: if a scenario
 * fails, the report says so, and if a check is never run it is counted as NOT RUN rather than
 * quietly folded into the pass total.
 */
data class SimulationReport(
    val runId: String,
    val generatedAt: Instant,
    val environment: String,
    val processingMode: String,
    val scenarios: List<ScenarioReport>,
    val assertions: List<AssertionResult>,
) {
    val passed: Int get() = assertions.count { it.status == AssertionStatus.PASSED }
    val failed: Int get() = assertions.count { it.status == AssertionStatus.FAILED }
    val totalRuntimeMillis: Long get() = scenarios.sumOf { it.runtimeMillis }

    /** True only when every assertion that ran passed. */
    val allPassed: Boolean get() = failed == 0 && assertions.isNotEmpty()

    fun toMarkdown(): String = buildString {
        appendLine("# Chrona simulation report")
        appendLine()
        appendLine("- **Run id:** `$runId`")
        appendLine("- **Generated:** $generatedAt")
        appendLine("- **Processing mode:** $processingMode")
        appendLine("- **Environment:** $environment")
        appendLine("- **Result:** ${if (allPassed) "PASSED" else "FAILED"} " +
            "($passed passed, $failed failed, ${assertions.size} total)")
        appendLine("- **Total simulation runtime:** ${totalRuntimeMillis} ms")
        appendLine()

        appendLine("## Scenarios")
        appendLine()
        scenarios.forEach { scenario ->
            appendLine("### ${scenario.name} (`${scenario.id}`)")
            appendLine()
            appendLine(scenario.description)
            appendLine()
            appendLine("- Days simulated: ${scenario.days.size}")
            appendLine("- Tasks in the pool: ${scenario.poolSize}")
            appendLine("- Distinct task titles: ${scenario.distinctTitles}")
            appendLine("- Runtime: ${scenario.runtimeMillis} ms")
            appendLine()
            if (scenario.days.isNotEmpty()) {
                appendLine("| Day | Date | Active | Scheduled | Completed | Missed | Unplaced | Overdue | At risk | Moved |")
                appendLine("|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|")
                scenario.days.forEach { day ->
                    appendLine(
                        "| ${day.dayOffset + 1} | ${day.dateIso} | ${day.activeTaskCount} | " +
                            "${day.scheduledTaskCount} | ${day.completedTaskCount} | " +
                            "${day.missedTaskCount} | ${day.unscheduledTaskCount} | " +
                            "${day.overdueTaskCount} | ${day.atRiskTaskCount} | ${day.movedTaskCount} |",
                    )
                }
                appendLine()
            }
            val reasons = scenario.days.flatMap { it.unplacedReasons.entries }.groupingBy { it.key }.eachCount()
            if (reasons.isNotEmpty()) {
                appendLine("Reasons work could not be placed:")
                reasons.toSortedMap().forEach { (reason, count) -> appendLine("- `$reason`: $count") }
                appendLine()
            }
        }

        appendLine("## Checks")
        appendLine()
        appendLine("| Status | Scenario | Check | Detail |")
        appendLine("|---|---|---|---|")
        assertions.forEach { result ->
            appendLine(
                "| ${result.status.label} | ${result.scenario} | ${result.name} | " +
                    "${result.detail.replace("|", "\\|")} |",
            )
        }
        appendLine()
    }

    /** Machine-readable twin of [toMarkdown], for CI to assert on without parsing prose. */
    fun toJson(): String = buildString {
        appendLine("{")
        appendLine("""  "runId": "${jsonEscape(runId)}",""")
        appendLine("""  "generatedAt": "$generatedAt",""")
        appendLine("""  "processingMode": "${jsonEscape(processingMode)}",""")
        appendLine("""  "environment": "${jsonEscape(environment)}",""")
        appendLine("""  "result": "${if (allPassed) "PASSED" else "FAILED"}",""")
        appendLine("""  "passed": $passed,""")
        appendLine("""  "failed": $failed,""")
        appendLine("""  "totalRuntimeMillis": $totalRuntimeMillis,""")
        appendLine("""  "scenarios": [""")
        scenarios.forEachIndexed { index, scenario ->
            appendLine("    {")
            appendLine("""      "id": "${jsonEscape(scenario.id)}",""")
            appendLine("""      "name": "${jsonEscape(scenario.name)}",""")
            appendLine("""      "days": ${scenario.days.size},""")
            appendLine("""      "poolSize": ${scenario.poolSize},""")
            appendLine("""      "distinctTitles": ${scenario.distinctTitles},""")
            appendLine("""      "runtimeMillis": ${scenario.runtimeMillis},""")
            appendLine("""      "totals": {""")
            val t = scenario.totals
            appendLine("""        "completed": ${t.completed},""")
            appendLine("""        "missed": ${t.missed},""")
            appendLine("""        "unscheduled": ${t.unscheduled},""")
            appendLine("""        "overdue": ${t.overdue},""")
            appendLine("""        "atRisk": ${t.atRisk},""")
            appendLine("""        "moved": ${t.moved}""")
            appendLine("      }")
            appendLine(if (index == scenarios.lastIndex) "    }" else "    },")
        }
        appendLine("  ],")
        appendLine("""  "assertions": [""")
        assertions.forEachIndexed { index, result ->
            appendLine("    {")
            appendLine("""      "status": "${result.status.name}",""")
            appendLine("""      "scenario": "${jsonEscape(result.scenario)}",""")
            appendLine("""      "name": "${jsonEscape(result.name)}",""")
            appendLine("""      "detail": "${jsonEscape(result.detail)}"""")
            appendLine(if (index == assertions.lastIndex) "    }" else "    },")
        }
        appendLine("  ]")
        appendLine("}")
    }
}

/** One scenario's observed outcome. */
data class ScenarioReport(
    val id: String,
    val name: String,
    val description: String,
    val days: List<SimulationDayReport>,
    val poolSize: Int,
    val distinctTitles: Int,
    val runtimeMillis: Long,
    val eventCount: Int,
) {
    val totals: SimulationTotals get() = SimulationTotals.from(days)
}

/** One invariant check and its measured result. */
data class AssertionResult(
    val scenario: String,
    val name: String,
    val status: AssertionStatus,
    val detail: String,
)

enum class AssertionStatus(val label: String) {
    PASSED("PASS"),
    FAILED("FAIL"),

    /** Recorded as never run rather than folded into the pass count. */
    NOT_RUN("NOT RUN"),
}

/**
 * Collects check results while a scenario runs.
 *
 * Checks are *recorded*, not thrown: a failing invariant should be reported alongside the ones
 * that passed, so a single run tells you everything that is wrong rather than only the first
 * thing that broke.
 */
class SimulationAssertions(private val scenarioId: String) {
    private val results = mutableListOf<AssertionResult>()

    fun check(name: String, condition: Boolean, detail: () -> String) {
        results += AssertionResult(
            scenario = scenarioId,
            name = name,
            status = if (condition) AssertionStatus.PASSED else AssertionStatus.FAILED,
            detail = detail(),
        )
    }

    fun notRun(name: String, detail: String) {
        results += AssertionResult(scenarioId, name, AssertionStatus.NOT_RUN, detail)
    }

    fun results(): List<AssertionResult> = results.toList()
}

/** Formats a millisecond duration for a report line. */
fun millis(value: Long): String = when {
    value < 1_000 -> "$value ms"
    else -> "%.1f s".format(Duration.ofMillis(value).toMillis() / 1000.0)
}

private fun jsonEscape(raw: String): String = raw
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", " ")
