package com.abhishek.ame.ui.story

import androidx.compose.ui.graphics.Color
import java.io.File

/**
 * Metadata bundle describing the song being shared to a story or social poster.
 */
data class StoryTrackInfo(
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val uri: String = "",
    val isLossless: Boolean = false,
    val localAudioPath: String? = null,
)

/**
 * Visual frame styles available in Ame Story Studio.
 */
enum class StoryFrameStyle {
    /** The iconic centerpiece card: frosted liquid glass, specular rim, album art, Ame logo. */
    GLASS_CARD,
    /** Retro 3D album sleeve with a grooved circular vinyl record sliding out. */
    VINYL_FLEX,
    /** Minimalist floating liquid glass capsule with thumbnail and waveform. */
    GLASS_PILL,
    /** Immersive full-bleed hero artwork poster with smooth gradient dissolve. */
    HERO_POSTER,
}

/**
 * Aspect ratios for stories and social feeds.
 */
enum class StoryFormat(val aspect: Float, val width: Int, val height: Int) {
    STORY_9_16(9f / 16f, 1080, 1920),
    PORTRAIT_4_5(4f / 5f, 1080, 1350),
    SQUARE_1_1(1f, 1080, 1080),
}

/**
 * Ambient background color palette schemes.
 */
enum class StoryColorPalette {
    /** Dynamically extracted dominant dual-tone gradient from album artwork. */
    AURA,
    /** Pure pitch-black OLED background with subtle charcoal glow. */
    TRUE_DARK,
    /** Deep galactic midnight indigo. */
    MIDNIGHT,
    /** Warm dusk twilight sunset gradient. */
    SUNSET,
}
