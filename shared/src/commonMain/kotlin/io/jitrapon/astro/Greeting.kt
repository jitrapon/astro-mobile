package io.jitrapon.astro

class Greeting {
    fun greeting(): String {
        return "Hello, ${Platform().platform}!"
    }
}