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
     * Only counts qualified events or events played for at least 30 seconds.
     */
    fun computeDailyListening(
        events: List<ListeningEvent>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Map<LocalDate, Int> {
        val dailyMap = mutableMapOf<LocalDate, Int>()
        for (event in events) {
            if (event.qualified || event.listenedMs >= 30_000L) {
                val date = Instant.ofEpochMilli(event.startedAt)
                    .atZone(zoneId)
                    .toLocalDate()
                dailyMap[date] = (dailyMap[date] ?: 0) + 1
            }
        }
        return dailyMap
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
