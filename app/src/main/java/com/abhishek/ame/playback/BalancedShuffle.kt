package com.abhishek.ame.playback

import kotlin.random.Random

/**
 * Spotify-inspired Balanced Distribution Shuffle Engine.
 *
 * Implements a perceptual uniform placement algorithm that spaces tracks
 * by the same artist/album evenly across the queue sequence so that tracks
 * by the same artist never cluster together.
 *
 * Also provides deterministic seeded shuffling so that shuffle orders survive
 * app restarts and resume snapshots without resetting or re-permuting.
 */
object BalancedShuffle {

    /**
     * Reorders a list of items using a balanced, perceptual distribution algorithm.
     *
     * @param items The items to shuffle.
     * @param seed Deterministic seed for reproducible permutations.
     * @param artistOf Extractor for the artist name or group identifier.
     */
    fun <T> balancedShuffle(
        items: List<T>,
        seed: Long = System.currentTimeMillis(),
        artistOf: (T) -> String,
    ): List<T> {
        if (items.size <= 2) return items.shuffled(Random(seed))

        val random = Random(seed)

        // 1. Group items by normalized artist key (case-insensitive & trimmed)
        val groups = items.groupBy { artistOf(it).trim().lowercase() }
            .mapValues { (_, groupItems) -> groupItems.shuffled(random).toMutableList() }

        // 2. Sort artist buckets by size descending (largest buckets placed first)
        val sortedBuckets = groups.entries
            .sortedWith(compareByDescending<Map.Entry<String, MutableList<T>>> { it.value.size }
                .thenBy { it.key })
            .map { it.value }

        val totalCount = items.size
        val result = MutableList<T?>(totalCount) { null }
        val occupied = BooleanArray(totalCount)
        val placedSet = mutableSetOf<T>()

        // 3. For each artist bucket, distribute its tracks uniformly across the list
        for (bucket in sortedBuckets) {
            val count = bucket.size
            if (count == 0) continue

            // Ideal spacing between tracks of this artist
            val step = totalCount.toDouble() / count
            // Start offset with slight jitter
            var offset = (random.nextDouble() * step).coerceIn(0.0, (step - 1.0).coerceAtLeast(0.0))

            for (track in bucket) {
                var targetIdx = offset.toInt().coerceIn(0, totalCount - 1)

                // If target index is occupied, find nearest unoccupied slot
                if (occupied[targetIdx]) {
                    var found = -1
                    for (radius in 1 until totalCount) {
                        val forward = targetIdx + radius
                        if (forward < totalCount && !occupied[forward]) {
                            found = forward
                            break
                        }
                        val backward = targetIdx - radius
                        if (backward >= 0 && !occupied[backward]) {
                            found = backward
                            break
                        }
                    }
                    targetIdx = if (found != -1) found else occupied.indexOfFirst { !it }
                }

                if (targetIdx in 0 until totalCount && !occupied[targetIdx]) {
                    result[targetIdx] = track
                    occupied[targetIdx] = true
                    placedSet.add(track)
                }
                offset += step
            }
        }

        // 4. Fill any remaining unoccupied slots with any unplaced items
        val remaining = items.filter { it !in placedSet }
        var remIdx = 0
        for (i in 0 until totalCount) {
            if (result[i] == null && remIdx < remaining.size) {
                result[i] = remaining[remIdx++]
            }
        }

        return result.filterNotNull()
    }

    /**
     * Generates a balanced shuffle index permutation for an ExoPlayer Timeline or MediaItem queue.
     * Anchors the currently playing item at index 0, preserves user-queued items immediately
     * after the playing item, and applies uniform artist spacing to all context tracks.
     */
    fun createBalancedOrder(
        totalCount: Int,
        currentIndex: Int,
        seed: Long = System.currentTimeMillis(),
        isQueued: (Int) -> Boolean,
        artistOf: (Int) -> String,
    ): IntArray {
        if (totalCount <= 0) return IntArray(0)
        if (totalCount == 1) return intArrayOf(0)

        val allIndices = (0 until totalCount).toList()
        val current = currentIndex.coerceIn(0, totalCount - 1)

        // Divide into: current, user-queued items, and remaining context items
        val queuedIndices = mutableListOf<Int>()
        val contextIndices = mutableListOf<Int>()

        for (i in allIndices) {
            if (i == current) continue
            if (isQueued(i)) {
                queuedIndices.add(i)
            } else {
                contextIndices.add(i)
            }
        }

        // Apply balanced uniform shuffle to context indices
        val shuffledContext = balancedShuffle(contextIndices, seed) { artistOf(it) }

        // Final sequence: current -> user-queued -> balanced context tracks
        val finalOrder = ArrayList<Int>(totalCount)
        finalOrder.add(current)
        finalOrder.addAll(queuedIndices)
        finalOrder.addAll(shuffledContext)

        return finalOrder.toIntArray()
    }
}
