package io.jitrapon.astro

import platform.Foundation.NSUUID

actual fun randomUUID(): String = NSUUID().UUIDString()