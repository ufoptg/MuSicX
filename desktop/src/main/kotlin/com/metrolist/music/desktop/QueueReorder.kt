package com.metrolist.music.desktop

/**
 * Reorders [queue] by moving the item at [from] to [to], returning the new list and the index
 * that keeps the currently playing track current after the move.
 */
internal fun reorderQueue(
    queue: List<SearchHit>,
    currentIndex: Int,
    from: Int,
    to: Int,
): Pair<List<SearchHit>, Int> {
    if (from == to || from !in queue.indices || to !in queue.indices) return queue to currentIndex
    val list = queue.toMutableList()
    val item = list.removeAt(from)
    list.add(to, item)
    val newIndex =
        when {
            currentIndex == from -> to
            from < currentIndex && to >= currentIndex -> currentIndex - 1
            from > currentIndex && to <= currentIndex -> currentIndex + 1
            else -> currentIndex
        }
    return list to newIndex
}

/**
 * Maps absolute lazy-list indices (which include [leadingItems] non-track rows such as a header
 * and a sort row) to track indices, or null when either index is not on a track.
 */
internal fun detailReorderIndices(
    fromIndex: Int,
    toIndex: Int,
    trackCount: Int,
    leadingItems: Int,
): Pair<Int, Int>? {
    val from = fromIndex - leadingItems
    val to = toIndex - leadingItems
    return if (from in 0 until trackCount && to in 0 until trackCount) from to to else null
}
