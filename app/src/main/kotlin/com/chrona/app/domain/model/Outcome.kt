package com.chrona.app.domain.model

/**
 * Typed result of a write (SHARED_CONTRACT.md §"Expected user/service failures return typed
 * results or sealed outcomes. Do not use generic exceptions as normal control flow").
 *
 * Failure families follow SHARED_CONTRACT.md §"Error outcome families" exactly, so UI can
 * map them to plain language without inspecting exception types or stack traces.
 */
sealed interface Failure {

    /** Input failed a domain rule. Carries field-level reasons, not a stack trace. */
    data class Validation(val reasons: List<String>) : Failure

    /** The referenced record does not exist. */
    data object NotFound : Failure

    /**
     * A stale version, or a change that would break a fixed constraint.
     * [message] is plain language for the user.
     */
    data class Conflict(val message: String) : Failure

    /** A platform permission was denied or revoked. Expected state, not an error. */
    data class PermissionDenied(val permission: String) : Failure

    /** The capability does not exist on this device/platform. */
    data class Unsupported(val reason: String) : Failure

    /** Local persistence failed. The previous state is intact. */
    data class StorageFailure(val reason: String) : Failure

    /** A platform surface failed. Callers must fall back, not fail the task flow. */
    data class PlatformUnavailable(val reason: String) : Failure

    /** The domain cannot decide; the user must choose. */
    data class RequiresUserChoice(val question: String) : Failure

    data object Cancelled : Failure

    data class UnknownFailure(val reason: String) : Failure
}

/** Result of an operation that produces a value. */
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failed(val failure: Failure) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failed -> this
}

inline fun <T> Outcome<T>.onSuccess(action: (T) -> Unit): Outcome<T> {
    if (this is Outcome.Success) action(value)
    return this
}

inline fun <T> Outcome<T>.onFailure(action: (Failure) -> Unit): Outcome<T> {
    if (this is Outcome.Failed) action(failure)
    return this
}

fun <T> Outcome<T>.getOrNull(): T? = (this as? Outcome.Success)?.value

fun <T> Outcome<T>.failureOrNull(): Failure? = (this as? Outcome.Failed)?.failure