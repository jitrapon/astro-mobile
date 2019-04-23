package io.jitrapon.glom.base.util

/**
 * Below contains convenient extension functions to convert
 * Moshi's deserialization way of converting Json number to Java's Double
 */
fun Any?.asNullableLong(): Long? = (this as? Double?)?.toLong()

fun Any?.asNullableDouble(): Double? = this as? Double?

fun Any?.asNullableInt(): Int? = (this as? Double?)?.toInt()

fun Any?.asInt(): Int = (this as Double).toInt()

fun Any?.asLong(): Long = (this as Double).toLong()

@Suppress("UNCHECKED_CAST")
fun Any?.asNullableIntList(): List<Int>? {
    return (this as? List<Double>)?.map {
        it.toInt()
    }
}