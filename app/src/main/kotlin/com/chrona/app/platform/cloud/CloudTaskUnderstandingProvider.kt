package com.chrona.app.platform.cloud

import com.chrona.app.domain.parse.TaskIntentDraftValidator
import com.chrona.app.domain.parse.TaskUnderstandingProvider
import com.chrona.app.domain.parse.TaskUnderstandingRequest
import com.chrona.app.domain.parse.TaskUnderstandingResult
import kotlin.coroutines.cancellation.CancellationException
import java.time.Instant

/**
 * Cloud task understanding, with the deterministic parser as the safety net.
 *
 * The app never calls an AI provider directly; it calls Chrona's own Edge Function, which owns
 * the provider keys and may itself fail over between providers. This class only decides what the
 * user sees:
 *
 * - the remote draft is used only when it is schema-valid and passes the canonical task rules;
 * - any transport error, timeout, non-2xx status, malformed payload or invalid draft silently
 *   falls back to [fallback] (the deterministic parser), so the capture flow never stops. The
 *   remote either yields a usable draft or nothing: it never returns a clarification, so the
 *   deterministic parser remains the authority on whether something is a task and on when to ask
 *   a focused question.
 *
 * Cancellation is rethrown so a cancelled capture is not swallowed into a fallback.
 */
class CloudTaskUnderstandingProvider(
    private val transport: HttpTransport,
    private val config: CloudConfig,
    private val fallback: TaskUnderstandingProvider,
    private val now: () -> Instant = { Instant.now() },
) : TaskUnderstandingProvider {

    override suspend fun understand(request: TaskUnderstandingRequest): TaskUnderstandingResult {
        if (!config.isUsable) return fallback.understand(request)
        val remote = try {
            callRemote(request)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            null
        }
        return remote ?: fallback.understand(request)
    }

    private suspend fun callRemote(request: TaskUnderstandingRequest): TaskUnderstandingResult? {
        val response = transport.post(
            HttpRequest(
                url = config.functionUrl,
                headers = mapOf(
                    "content-type" to "application/json",
                    "authorization" to "Bearer ${config.anonKey}",
                    "apikey" to config.anonKey,
                ),
                body = TaskIntentWire.encodeRequest(request.text, request.referenceTime, request.zoneId.id),
                timeoutMillis = config.timeoutMillis,
            ),
        )
        if (response.status !in 200..299) return null
        val draft = TaskIntentWire.decodeDraft(response.body) ?: return null
        if (TaskIntentDraftValidator.validate(draft, now()).isNotEmpty()) return null
        return TaskUnderstandingResult.Parsed(draft)
    }
}
