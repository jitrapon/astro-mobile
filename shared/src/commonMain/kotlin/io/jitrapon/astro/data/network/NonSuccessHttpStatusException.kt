package io.jitrapon.astro.data.network

/**
 * A response arrived, but its status was outside 2xx — so its body is an error document rather than
 * the payload the caller asked for and must not be decoded as one.
 *
 * Carries [statusCode] as data rather than only in the message so a caller can branch on it (a 404
 * range is a different product state from a 503) without parsing prose.
 */
class NonSuccessHttpStatusException(val statusCode: Int, statusDescription: String) :
    Exception("The backend returned HTTP $statusCode $statusDescription.")
