package io.jitrapon.astro

/**
 * The host platform's name and version, as the platform itself reports it.
 *
 * Deliberately kept with no caller: together with [randomUUID] it is the worked example of the
 * `expect`/`actual` seam this module resolves every platform difference through, which the project
 * documentation points at by name. Delete it only alongside that documentation — an unreferenced
 * declaration here is not the same thing as a dead one.
 */
expect class Platform() {
    val name: String
}
