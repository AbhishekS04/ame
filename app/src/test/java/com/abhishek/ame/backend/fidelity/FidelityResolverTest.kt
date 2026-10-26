package com.abhishek.ame.backend.fidelity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FidelityResolverTest {

    @Test
    fun strictMatchAcceptsExactTrackAndDuration() {
        assertTrue(
            FidelityResolver.isStrictCandidateMatch(
                targetTitle = "Blinding Lights",
                targetArtist = "The Weeknd",
                targetDurationSec = 200,
                candidateTitle = "Blinding Lights",
                candidateArtists = listOf("The Weeknd"),
                candidateDurationSec = 200,
            ),
        )
    }

    @Test
    fun strictMatchRejectsDifferentArtistAndLongDurationDrift() {
        assertFalse(
            FidelityResolver.isStrictCandidateMatch(
                targetTitle = "Blinding Lights",
                targetArtist = "The Weeknd",
                targetDurationSec = 200,
                candidateTitle = "Blinding Lights",
                candidateArtists = listOf("A Different Artist"),
                candidateDurationSec = 200,
            ),
        )
        assertFalse(
            FidelityResolver.isStrictCandidateMatch(
                targetTitle = "Blinding Lights",
                targetArtist = "The Weeknd",
                targetDurationSec = 200,
                candidateTitle = "Blinding Lights",
                candidateArtists = listOf("The Weeknd"),
                candidateDurationSec = 240,
            ),
        )
    }

    @Test
    fun strictMatchRejectsUnrequestedAlternateVersion() {
        assertFalse(
            FidelityResolver.isStrictCandidateMatch(
                targetTitle = "Blinding Lights",
                targetArtist = "The Weeknd",
                targetDurationSec = 200,
                candidateTitle = "Blinding Lights Remix",
                candidateArtists = listOf("The Weeknd"),
                candidateDurationSec = 200,
            ),
        )
    }
}
