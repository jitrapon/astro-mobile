package io.jitrapon.astro.data

import io.jitrapon.astro.data.model.LoggedInUser
import io.jitrapon.astro.randomUUID

/** Class that handles authentication w/ login credentials and retrieves user information. */
class LoginDataSource {

    fun login(username: String, password: String): Result<LoggedInUser> {
        if (username.isBlank() || password.isBlank()) {
            return Result.Error(IllegalArgumentException("Username and password must not be blank"))
        }
        // Placeholder authentication until a real auth backend is wired in: any non-blank
        // credentials succeed and yield a user derived from the supplied username. Both inputs
        // are consumed so the signature stays honest about what a real implementation will need.
        val displayName = username.substringBefore('@').ifBlank { username }
        return Result.Success(LoggedInUser(randomUUID(), displayName))
    }

    fun logout() {
        // No-op until a real auth backend exists to revoke the session/token.
    }
}
