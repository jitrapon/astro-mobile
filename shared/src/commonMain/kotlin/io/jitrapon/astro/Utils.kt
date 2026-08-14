package io.jitrapon.astro

/**
 * A random UUID from the platform's own generator.
 *
 * Kept with no caller for the same reason as [Platform]: it is the function half of this module's
 * documented `expect`/`actual` example, where [Platform] is the class half.
 */
expect fun randomUUID(): String
