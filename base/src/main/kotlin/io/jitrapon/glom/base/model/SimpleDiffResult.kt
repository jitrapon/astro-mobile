package io.jitrapon.glom.base.model

/**
 * Diff result for that stores the items that are added and removed
 */
class SimpleDiffResult<T> {

    private val added: HashSet<T> = HashSet()
    private val removed: HashSet<T> = HashSet()

    fun clear() {
        added.clear()
        removed.clear()
    }

    fun getAdded() = added

    fun getRemoved() = removed

    fun add(obj: T, markAdded: Boolean) {
        removed.remove(obj)
        if (markAdded) added.add(obj)
    }

    fun remove(obj: T, markRemoved: Boolean) {
        added.remove(obj)
        if (markRemoved) removed.add(obj)
    }

    fun hasAdded(obj: T) = added.contains(obj)

    fun hasRemoved(obj: T) = removed.contains(obj)
}
