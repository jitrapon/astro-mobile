package io.jitrapon.glom.base.util

/**
 * Created by Jitrapon
 */
fun String.getLastWord(delimiter: Char): String {
    return if (last() == delimiter) {
        dropLast(1).let {
            val lastSpaceIndex = it.lastIndexOf(delimiter)
            if (lastSpaceIndex != -1) it.substring(lastSpaceIndex, it.length).trim() else it
        }
    }
    else {
        val lastSpaceIndex = lastIndexOf(delimiter)
        if (lastSpaceIndex != -1) substring(lastSpaceIndex, length).trim() else this
    }
}