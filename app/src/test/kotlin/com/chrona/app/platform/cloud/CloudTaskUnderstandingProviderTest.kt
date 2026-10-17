package com.chrona.app.platform.cloud

import com.chrona.app.domain.parse.ParseFailureReason
import com.chrona.app.domain.parse.ParsePreferences
import com.chrona.app.domain.parse.TaskUnderstandingProvider
import com.chrona.app.domain.parse.TaskUnderstandingRequest
import com.chrona.app.domain.parse.TaskUnderstandingResult
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.coroutines.cancellation.CancellationException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CloudTaskUnderstandingProviderTest {

    private val request = TaskUnderstandingRequest(
        text = "submit the report tomorrow",
        referenceTime = ZonedDateTime.of(2026, 10, 15, 8, 0, 0, 0, ZoneId.of("UTC")),
        zoneId = ZoneId.of("UTC"),
        preferences = ParsePreferences(),
    )

    private val fallbackResult: TaskUnderstandingResult =
        TaskUnderstandingResult.Unsupported(ParseFailureReason.NOT_A_TASK_REQUEST)

    private val usable = CloudConfig(enabled = true, functionUrl = "https://example.test/function", anonKey = "anon")

    private class RecordingParser(private val result: TaskUnderstandingResult) : TaskUnderstandingProvider {
        var calls = 0
        override suspend fun understand(request: TaskUnderstandingRequest): TaskUnderstandingResult {
            calls++
            return result
        }
    }

    private class FakeTransport(
        private val responder: suspend (HttpRequest) -> HttpResponse,
    ) : HttpTransport {
        val requests = mutableListOf<HttpRequest>()
        override suspend fun post(request: HttpRequest): HttpResponse {
            requests += request
            return responder(request)
        }
    }

    private fun validBody(title: String = "submit the report"): String =
        JSONObject()
            .put("provider", "groq")
            .put(
                "draft",
                JSONObject()
                    .put("title", title)
                    .put("requestedDate", "2026-10-16")
                    .put("estimatedDurationMinutes", 30),
            )
            .toString()

    private fun provider(config: CloudConfig, transport: HttpTransport, fallback: RecordingParser) =
        CloudTaskUnderstandingProvider(transport, config, fallback, now = { Instant.parse("2026-10-15T08:00:00Z") })

    @Test
    fun `uses the fallback and never calls out when cloud is disabled`() = runTest {
        val transport = FakeTransport { HttpResponse(200, validBody()) }
        val fallback = RecordingParser(fallbackResult)

        val result = provider(CloudConfig(enabled = false, functionUrl = "", anonKey = ""), transport, fallback)
            .understand(request)

        assertEquals(fallbackResult, result)
        assertEquals(1, fallback.calls)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `uses the fallback when the url is blank`() = runTest {
        val transport = FakeTransport { HttpResponse(200, validBody()) }
        val fallback = RecordingParser(fallbackResult)

        provider(usable.copy(functionUrl = "  "), transport, fallback).understand(request)

        assertEquals(1, fallback.calls)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `returns the remote draft when the response is valid`() = runTest {
        val transport = FakeTransport { HttpResponse(200, validBody("submit the report")) }
        val fallback = RecordingParser(fallbackResult)

        val result = provider(usable, transport, fallback).understand(request)

        val draft = (result as TaskUnderstandingResult.Parsed).drafts.single()
        assertEquals("submit the report", draft.title)
        assertEquals(0, fallback.calls)
    }

    @Test
    fun `sends the anon key and the encoded request`() = runTest {
        val transport = FakeTransport { HttpResponse(200, validBody()) }
        val fallback = RecordingParser(fallbackResult)

        provider(usable, transport, fallback).understand(request)

        val sent = transport.requests.single()
        assertEquals("Bearer anon", sent.headers["authorization"])
        assertEquals("anon", sent.headers["apikey"])
        assertEquals("submit the report tomorrow", JSONObject(sent.body).getString("text"))
    }

    @Test
    fun `falls back on a non-2xx status`() = runTest {
        val transport = FakeTransport { HttpResponse(502, "upstream down") }
        val fallback = RecordingParser(fallbackResult)

        val result = provider(usable, transport, fallback).understand(request)

        assertEquals(fallbackResult, result)
        assertEquals(1, fallback.calls)
    }

    @Test
    fun `falls back on an unparseable body`() = runTest {
        val transport = FakeTransport { HttpResponse(200, "not json") }
        val fallback = RecordingParser(fallbackResult)

        assertEquals(fallbackResult, provider(usable, transport, fallback).understand(request))
        assertEquals(1, fallback.calls)
    }

    @Test
    fun `falls back when the draft breaks the canonical rules`() = runTest {
        val transport = FakeTransport { HttpResponse(200, validBody(title = "   ")) }
        val fallback = RecordingParser(fallbackResult)

        assertEquals(fallbackResult, provider(usable, transport, fallback).understand(request))
        assertEquals(1, fallback.calls)
    }

    @Test
    fun `falls back when the transport throws`() = runTest {
        val transport = FakeTransport { throw IOException("no route to host") }
        val fallback = RecordingParser(fallbackResult)

        assertEquals(fallbackResult, provider(usable, transport, fallback).understand(request))
        assertEquals(1, fallback.calls)
    }

    @Test
    fun `rethrows cancellation instead of falling back`() = runTest {
        val transport = FakeTransport { throw CancellationException("cancelled") }
        val fallback = RecordingParser(fallbackResult)

        val outcome = runCatching { provider(usable, transport, fallback).understand(request) }

        assertTrue(outcome.exceptionOrNull() is CancellationException)
        assertEquals(0, fallback.calls)
    }
}
