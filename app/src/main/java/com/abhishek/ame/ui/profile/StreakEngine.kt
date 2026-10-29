package com.abhishek.ame.ui.profile

import com.abhishek.ame.data.ListeningEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * High-performance analytics engine for calculating listening streaks and daily aggregates
 * directly from local listening history events.
 */
object StreakEngine {

    /**
     * Aggregates listening events into a day-by-day song play count map.
     * Counts qualified events, plays with at least 5 seconds of listening time,
     * or completed songs.
     */
    fun computeDailyListening(
        events: List<ListeningEvent>,
        savedActivity: Map<String, Int> = emptyMap(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Map<LocalDate, Int> {
        val dailyMap = mutableMapOf<LocalDate, Int>()
        // 1. Seed with previously saved/cloud activity
        for ((dateStr, count) in savedActivity) {
            val date = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: continue
            dailyMap[date] = maxOf(dailyMap[date] ?: 0, count)
        }
        // 2. Add local events from listening history
        for (event in events) {
            if (event.qualified || event.listenedMs >= 5_000L || event.endedAt >= event.startedAt) {
                val date = Instant.ofEpochMilli(event.startedAt)
                    .atZone(zoneId)
                    .toLocalDate()
                dailyMap[date] = (dailyMap[date] ?: 0) + 1
            }
        }
        return dailyMap
    }

    /**
     * Serializes daily listening map to ISO-8601 date string keys for Supabase storage.
     */
    fun formatForCloud(daily: Map<LocalDate, Int>): Map<String, Int> {
        return daily.mapKeys { it.key.toString() }
    }

    /**
     * Parses cloud JSON map back into LocalDate keys.
     */
    fun parseFromCloud(cloud: Map<String, Int>): Map<LocalDate, Int> {
        return cloud.mapNotNull { (dateStr, count) ->
            runCatching { LocalDate.parse(dateStr) to count }.getOrNull()
        }.toMap()
    }

    /**
     * Calculates the user's current continuous active streak in days.
     * If user hasn't played a song today yet, yesterday's streak is still considered active.
     */
    fun calculateCurrentStreak(
        dailyListening: Map<LocalDate, Int>,
        today: LocalDate = LocalDate.now(),
    ): Int {
        if (dailyListening.isEmpty()) return 0

        var currentDay = today
        // If no plays today, start checking from yesterday so the streak isn't reset before day ends
        if ((dailyListening[currentDay] ?: 0) == 0) {
            currentDay = currentDay.minusDays(1)
        }

        var streak = 0
        while ((dailyListening[currentDay] ?: 0) > 0) {
            streak++
            currentDay = currentDay.minusDays(1)
        }
        return streak
    }

    /**
     * Calculates the all-time longest continuous daily streak.
     */
    fun calculateBestStreak(dailyListening: Map<LocalDate, Int>): Int {
        if (dailyListening.isEmpty()) return 0

        val sortedDates = dailyListening.keys.sorted()
        var maxStreak = 0
        var currentStreak = 0
        var previousDate: LocalDate? = null

        for (date in sortedDates) {
            if ((dailyListening[date] ?: 0) > 0) {
                if (previousDate != null && previousDate.plusDays(1) == date) {
                    currentStreak++
                } else {
                    currentStreak = 1
                }
                if (currentStreak > maxStreak) {
                    maxStreak = currentStreak
                }
                previousDate = date
            }
        }
        return maxStreak
    }

    /**
     * Calculates the total number of qualified songs played across all recorded history.
     */
    fun calculateTotalTracks(dailyListening: Map<LocalDate, Int>): Int {
        return dailyListening.values.sum()
    }
}
