package com.chrona.app.platform.cloud

import com.chrona.app.domain.model.Flexibility
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.model.TimeRange
import com.chrona.app.domain.parse.DraftField
import com.chrona.app.domain.parse.ParseAssumption
import com.chrona.app.domain.parse.TaskIntentDraft
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZonedDateTime

/**
 * The wire contract between the Android app and the Supabase Edge Function, and the trust
 * boundary for model output.
 *
 * The function (and the model behind it) is untrusted: every field is re-checked here, and an
 * unusable payload becomes `null` so the caller falls back to the deterministic parser. Only
 * structured fields are read; no raw model text is ever stored or shown.
 */
object TaskIntentWire {

    fun encodeRequest(text: String, referenceTime: ZonedDateTime, zoneId: String): String =
        JSONObject()
            .put("text", text)
            .put("referenceTime", referenceTime.toOffsetDateTime().toString())
            .put("zoneId", zoneId)
            .toString()

    /** Reads the `{provider, draft}` envelope. Returns null when the payload is not usable. */
    fun decodeDraft(body: String): TaskIntentDraft? {
        val draftJson = runCatching { JSONObject(body).optJSONObject("draft") }.getOrNull() ?: return null
        return runCatching { toDraft(draftJson) }.getOrNull()
    }

    fun toDraft(json: JSONObject): TaskIntentDraft {
        val title = json.stringOrNull("title") ?: throw IllegalArgumentException("title is required")

        val fixedStart = json.stringOrNull("fixedStart")?.let(::parseDateTime)
        val fixedEnd = json.stringOrNull("fixedEnd")?.let(::parseDateTime)
        require((fixedStart == null) == (fixedEnd == null)) { "fixed range needs both ends" }

        return TaskIntentDraft(
            title = title,
            detail = json.stringOrNull("detail"),
            requestedDate = json.stringOrNull("requestedDate")?.let(LocalDate::parse),
            deadline = json.stringOrNull("deadline")?.let(::parseDateTime),
            estimatedDuration = json.durationOrNull("estimatedDurationMinutes"),
            priority = json.stringOrNull("priority")?.let { Priority.valueOf(it.uppercase()) },
            flexibility = json.stringOrNull("flexibility")?.let { Flexibility.valueOf(it.uppercase()) },
            splittable = json.boolOrNull("splittable"),
            minimumSession = json.durationOrNull("minimumSessionMinutes"),
            fixedRange = if (fixedStart != null && fixedEnd != null) TimeRange(fixedStart, fixedEnd) else null,
            assumptions = decodeAssumptions(json),
            // A schema-valid draft is the provider's complete answer to this request. It is a
            // heuristic indicator of "the cloud heard a task", not a calibrated probability.
            confidence = 1f,
        )
    }

    private fun parseDateTime(value: String): ZonedDateTime =
        OffsetDateTime.parse(value).toZonedDateTime()

    private fun decodeAssumptions(json: JSONObject): List<ParseAssumption> {
        if (!json.has("assumptions") || json.isNull("assumptions")) return emptyList()
        val array = json.optJSONArray("assumptions") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            when (val item = array.opt(index)) {
                is String -> item.trim().takeIf { it.isNotEmpty() }
                    ?.let { ParseAssumption(DraftField.TITLE, it) }

                is JSONObject -> item.optString("text").trim().takeIf { it.isNotEmpty() }
                    ?.let { ParseAssumption(fieldOf(item.optString("field")), it) }

                else -> null
            }
        }
    }

    private fun fieldOf(name: String): DraftField =
        DraftField.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DraftField.TITLE

    private fun JSONObject.stringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).trim().ifEmpty { null }

    private fun JSONObject.boolOrNull(key: String): Boolean? =
        if (!has(key) || isNull(key)) null else getBoolean(key)

    /** Whole minutes in 1..1440 only; anything else is a schema violation, so it is refused. */
    private fun JSONObject.durationOrNull(key: String): Duration? {
        if (!has(key) || isNull(key)) return null
        val minutes = getInt(key)
        require(minutes in 1..1440) { "duration outside 1..1440 minutes" }
        return Duration.ofMinutes(minutes.toLong())
    }
}
