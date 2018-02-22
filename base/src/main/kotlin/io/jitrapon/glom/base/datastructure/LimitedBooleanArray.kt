package io.jitrapon.glom.base.datastructure

import android.util.SparseBooleanArray
import androidx.util.forEach
import androidx.util.set
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingDeque

/**
 * A data structure that will keep track of, at most, a specified number of 'true' values.
 * Supports concurrency. Access and modification can be done via an Integer keys similar
 * to a HashMap.
 *
 * @author Jitrapon Tiachunpun
 */
class LimitedBooleanArray(initialCapacity: Int = 10, private var maxPositive: Int = 1) : SparseBooleanArray(initialCapacity) {

    /*
     * Stores indices of the keys that represent a positive value (true)
     */
    private val positiveQueue: BlockingQueue<Int> = LinkedBlockingDeque()

    override fun put(key: Int, value: Boolean) {
        super.put(key, value)

        if (value) {
            if (!positiveQueue.contains(key)) {
                positiveQueue.put(key)
                val numItemsToPop = Math.max(0, positiveQueue.size - maxPositive)
                for (i in 0 until numItemsToPop) {
                    this[positiveQueue.remove()] = false
                }
            }
        }
        else {
            positiveQueue.remove(key)
        }
    }

    override fun toString(): String {
        return StringBuilder().apply {
            this@LimitedBooleanArray.forEach { key, value ->
                append("key=$key, value=$value\n")
            }
        }.toString()
    }
}