package io.jitrapon.astro.data.calendar

/**
 * A response arrived and decoded, but its envelope declares a contract version these models were
 * not written against — so the screen it describes cannot be trusted, however well-formed it looks.
 *
 * Carries both versions as data rather than only in the message so a caller can distinguish the two
 * states without parsing prose: a backend that has moved ahead of this build (prompt an update) is
 * a different product state from a build that has moved ahead of its backend (a rollout ordering
 * bug).
 */
class UnsupportedSchemaVersionException(
    val receivedSchemaVersion: String,
    val supportedSchemaVersion: String,
) :
    Exception(
        "The backend returned schema version $receivedSchemaVersion; " +
            "this client supports only $supportedSchemaVersion."
    )
