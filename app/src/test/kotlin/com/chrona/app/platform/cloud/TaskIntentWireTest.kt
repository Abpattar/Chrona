package com.chrona.app.platform.cloud

import com.chrona.app.domain.model.Flexibility
import com.chrona.app.domain.model.Priority
import com.chrona.app.domain.parse.DraftField
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The wire mapper is the trust boundary for untrusted model output, so these tests pin both the
 * happy mapping and every rejection that must send the caller back to the deterministic parser.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TaskIntentWireTest {

    private fun envelope(draft: JSONObject): String = JSONObject().put("provider", "groq").put("draft", draft).toString()

    @Test
    fun `maps a fully populated draft`() {
        val draft = JSONObject()
            .put("title", "submit the report")
            .put("detail", "for the finance class")
            .put("requestedDate", "2026-10-16")
            .put("deadline", "2026-10-16T17:00:00+00:00")
            .put("estimatedDurationMinutes", 120)
            .put("priority", "CRITICAL")
            .put("flexibility", "FIXED")
            .put("splittable", true)
            .put("minimumSessionMinutes", 30)
            .put("fixedStart", "2026-10-16T15:00:00+00:00")
            .put("fixedEnd", "2026-10-16T16:00:00+00:00")
            .put("assumptions", JSONArray().put(JSONObject().put("field", "DURATION").put("text", "assumed two hours")))

        val drafts = TaskIntentWire.decodeDrafts(envelope(draft))
        assertNotNull(drafts)
        val result = drafts!!.single()
        assertEquals("submit the report", result.title)
        assertEquals("for the finance class", result.detail)
        assertEquals(LocalDate.of(2026, 10, 16), result.requestedDate)
        assertEquals(Duration.ofMinutes(120), result.estimatedDuration)
        assertEquals(Priority.CRITICAL, result.priority)
        assertEquals(Flexibility.FIXED, result.flexibility)
        assertEquals(true, result.splittable)
        assertEquals(Duration.ofMinutes(30), result.minimumSession)
        assertEquals(15, result.fixedRange!!.start.hour)
        assertEquals(listOf("assumed two hours"), result.assumptions.map { it.text })
        assertEquals(DraftField.DURATION, result.assumptions.single().field)
    }

    @Test
    fun `a minimal draft maps with nulls`() {
        val drafts = TaskIntentWire.decodeDrafts(envelope(JSONObject().put("title", "buy milk")))
        assertNotNull(drafts)
        val result = drafts!!.single()
        assertNull(result.requestedDate)
        assertNull(result.priority)
        assertNull(result.estimatedDuration)
        assertNull(result.fixedRange)
        assertEquals(emptyList<String>(), result.assumptions.map { it.text })
    }

    @Test
    fun `rejects a missing title`() {
        assertNull(TaskIntentWire.decodeDrafts(envelope(JSONObject().put("detail", "no title"))))
        assertNull(TaskIntentWire.decodeDrafts(envelope(JSONObject().put("title", "   "))))
    }

    @Test
    fun `rejects a bad requested date`() {
        assertNull(TaskIntentWire.decodeDrafts(envelope(JSONObject().put("title", "x").put("requestedDate", "16/10/2026"))))
    }

    @Test
    fun `rejects a fixed range whose end is not after its start`() {
        val draft = JSONObject()
            .put("title", "x")
            .put("fixedStart", "2026-10-16T10:00:00+00:00")
            .put("fixedEnd", "2026-10-16T09:00:00+00:00")
        assertNull(TaskIntentWire.decodeDrafts(envelope(draft)))
    }

    @Test
    fun `rejects a fixed range with only one end`() {
        assertNull(TaskIntentWire.decodeDrafts(envelope(JSONObject().put("title", "x").put("fixedStart", "2026-10-16T10:00:00+00:00"))))
    }

    @Test
    fun `rejects an unusable duration`() {
        assertNull(TaskIntentWire.decodeDrafts(envelope(JSONObject().put("title", "x").put("estimatedDurationMinutes", 0))))
        assertNull(TaskIntentWire.decodeDrafts(envelope(JSONObject().put("title", "x").put("estimatedDurationMinutes", 2000))))
    }

    @Test
    fun `rejects an unknown priority`() {
        assertNull(TaskIntentWire.decodeDrafts(envelope(JSONObject().put("title", "x").put("priority", "URGENT"))))
    }

    @Test
    fun `reads every task in a multi-task utterance`() {
        val drafts = TaskIntentWire.decodeDrafts(
            JSONObject()
                .put("provider", "groq")
                .put(
                    "drafts",
                    JSONArray()
                        .put(JSONObject().put("title", "function on the 25th").put("requestedDate", "2026-10-25"))
                        .put(JSONObject().put("title", "start studying").put("requestedDate", "2026-10-15")),
                )
                .toString(),
        )

        assertEquals(
            listOf("function on the 25th", "start studying"),
            drafts!!.map { it.title },
        )
        assertEquals(LocalDate.of(2026, 10, 25), drafts.first().requestedDate)
    }

    @Test
    fun `rejects the whole response when one task in it is unusable`() {
        val body = JSONObject()
            .put("provider", "groq")
            .put(
                "drafts",
                JSONArray()
                    .put(JSONObject().put("title", "function on the 25th"))
                    .put(JSONObject().put("detail", "no title here")),
            )
            .toString()

        assertNull("a half-understood utterance must not be stored", TaskIntentWire.decodeDrafts(body))
    }

    @Test
    fun `rejects an empty task list`() {
        assertNull(TaskIntentWire.decodeDrafts(JSONObject().put("drafts", JSONArray()).toString()))
    }

    @Test
    fun `rejects a payload without a draft envelope`() {
        assertNull(TaskIntentWire.decodeDrafts("{\"provider\":\"groq\"}"))
        assertNull(TaskIntentWire.decodeDrafts("not json"))
    }

    @Test
    fun `encodes the request the edge function expects`() {
        val reference = ZonedDateTime.of(2026, 10, 15, 8, 0, 0, 0, ZoneId.of("Europe/London"))
        val encoded = JSONObject(TaskIntentWire.encodeRequest("buy milk tomorrow", reference, "Europe/London"))
        assertEquals("buy milk tomorrow", encoded.getString("text"))
        assertEquals("2026-10-15T08:00+01:00", encoded.getString("referenceTime"))
        assertEquals("Europe/London", encoded.getString("zoneId"))
    }

    /**
     * 100 fixtures over a deterministic spread of field combinations. Every one must map to a
     * draft whose fields match what was sent — this is the bulk contract test the task asks for.
     */
    @Test
    fun `maps one hundred generated fixtures consistently`() {
        val priorities = listOf<String?>(null, "CRITICAL", "IMPORTANT", "LOW")
        val flexibilities = listOf<String?>(null, "FLEXIBLE", "FIXED")
        val baseDate = LocalDate.of(2026, 1, 1)

        for (i in 0 until 100) {
            var json = JSONObject().put("title", "task $i")
            val expectedDate = if (i % 2 == 0) null else baseDate.plusDays((i % 28).toLong())
            expectedDate?.let { json = json.put("requestedDate", it.toString()) }
            if (i % 3 == 0) json = json.put("deadline", "2026-02-01T09:00:00+00:00")
            val expectedMinutes = if (i % 5 == 0) null else 15 * (1 + (i % 8))
            expectedMinutes?.let { json = json.put("estimatedDurationMinutes", it) }
            val expectedPriority = priorities[i % priorities.size]
            expectedPriority?.let { json = json.put("priority", it) }
            val expectedFlexibility = flexibilities[i % flexibilities.size]
            expectedFlexibility?.let { json = json.put("flexibility", it) }
            if (i % 2 == 1) json = json.put("splittable", true)
            if (i % 4 == 0) json = json.put("assumptions", JSONArray().put("assumed $i"))

            val drafts = TaskIntentWire.decodeDrafts(envelope(json))
            assertNotNull("fixture $i must decode", drafts)
            val result = drafts!!.single()
            assertEquals("fixture $i title", "task $i", result.title)
            assertEquals("fixture $i date", expectedDate, result.requestedDate)
            assertEquals("fixture $i duration", expectedMinutes?.let { Duration.ofMinutes(it.toLong()) }, result.estimatedDuration)
            assertEquals("fixture $i priority", expectedPriority?.let { Priority.valueOf(it) }, result.priority)
            assertEquals("fixture $i flexibility", expectedFlexibility?.let { Flexibility.valueOf(it) }, result.flexibility)
        }
    }
}
