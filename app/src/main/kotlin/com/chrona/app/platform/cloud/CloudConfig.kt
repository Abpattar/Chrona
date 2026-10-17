package com.chrona.app.platform.cloud

/**
 * Configuration for the optional cloud task-understanding path.
 *
 * The whole feature is off unless [enabled] is true and [functionUrl] is non-blank, so a normal
 * build keeps the deterministic parser as the only path and never opens a socket. The values come
 * from BuildConfig fields that are empty unless the owner supplies them at build time; no key is
 * ever embedded in source.
 */
data class CloudConfig(
    val enabled: Boolean,
    val functionUrl: String,
    /** The Supabase anon key (a public JWT), not a service-role key. */
    val anonKey: String,
    val timeoutMillis: Long = 8_000,
) {
    val isUsable: Boolean get() = enabled && functionUrl.isNotBlank()
}
