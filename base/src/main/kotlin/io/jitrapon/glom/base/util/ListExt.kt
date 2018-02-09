package io.jitrapon.glom.base.util

/**
 * Utility extension method for List
 *
 * Created by Jitrapon on 11/26/2017.
 */

/**
 * Safely gets an element from the list, without throwing ArrayOutOfBoundException
 * by checking the size of the list beforehand. Also checks the list nullability and whether or
 * not it is empty.
 */
fun <T> List<T>?.get(position: Int, defaultValue: T?): T? {
    this ?: return defaultValue
    if (isEmpty() || position < 0 || position > size - 1) return defaultValue
    return get(position)
}

/**
 * Returns true iff this List is null or empty
 */
fun <T> List<T>?.isNullOrEmpty(): Boolean {
    this ?: return true
    return isEmpty()
}