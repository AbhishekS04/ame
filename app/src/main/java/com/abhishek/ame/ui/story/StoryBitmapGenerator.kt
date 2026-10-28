package com.abhishek.ame.ui.story

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.abhishek.ame.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/**
 * High-performance deterministic native renderer that produces pixel-perfect 1080p
 * Instagram Story posters and transparent stickers with Ame Liquid Glass design.
 */
object StoryBitmapGenerator {

    suspend fun renderStoryImage(
        context: Context,
        info: StoryTrackInfo,
        style: StoryFrameStyle = StoryFrameStyle.GLASS_CARD,
        format: StoryFormat = StoryFormat.STORY_9_16,
        paletteScheme: StoryColorPalette = StoryColorPalette.AURA,
        stickerOnly: Boolean = false,
    ): Bitmap = withContext(Dispatchers.IO) {
        // 1. Fetch artwork bitmap asynchronously
        val coverBitmap = loadCoverBitmap(context, info.artworkUrl)

        // 2. Resolve background colors
        val (topColor, bottomColor) = resolveColors(coverBitmap, paletteScheme)

        val targetWidth = format.width
        val targetHeight = format.height
        val output = createBitmap(targetWidth, targetHeight)
        val canvas = Canvas(output)

        // 3. Draw Background Canvas (unless transparent sticker mode)
        if (!stickerOnly) {
            val bgPaint = Paint().apply {
                isDither = true
                shader = LinearGradient(
                    0f, 0f, 0f, targetHeight.toFloat(),
                    intArrayOf(
                        topColor,
                        blendColors(topColor, bottomColor, 0.40f),
                        bottomColor
                    ),
                    floatArrayOf(0f, 0.40f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat(), bgPaint)

            // Ethereal ambient spotlight illumination
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                isDither = true
                val glow = Color.argb(55, Color.red(topColor), Color.green(topColor), Color.blue(topColor))
                shader = RadialGradient(
                    targetWidth * 0.5f, targetHeight * 0.35f,
                    targetWidth * 0.85f,
                    intArrayOf(glow, Color.TRANSPARENT),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat(), glowPaint)
        }

        // 4. Render selected frame style
        when (style) {
            StoryFrameStyle.GLASS_CARD -> drawGlassCard(
                context = context,
                canvas = canvas,
                cover = coverBitmap,
                info = info,
                w = targetWidth,
                h = targetHeight,
            )
            StoryFrameStyle.VINYL_FLEX -> drawVinylFlex(
                context = context,
                canvas = canvas,
                cover = coverBitmap,
                info = info,
                w = targetWidth,
                h = targetHeight,
            )
            StoryFrameStyle.GLASS_PILL -> drawGlassPill(
                context = context,
                canvas = canvas,
                cover = coverBitmap,
                info = info,
                w = targetWidth,
                h = targetHeight,
            )
            StoryFrameStyle.HERO_POSTER -> drawHeroPoster(
                context = context,
                canvas = canvas,
                cover = coverBitmap,
                info = info,
                w = targetWidth,
                h = targetHeight,
                topColor = topColor,
                bottomColor = bottomColor,
            )
        }

        output
    }

    suspend fun saveStoryBitmapToFile(
        context: Context,
        bitmap: Bitmap,
        filename: String = "ame_story_${System.currentTimeMillis()}.png",
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "stories").apply { if (!exists()) mkdirs() }
        val file = File(dir, filename)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        file
    }

    private suspend fun loadCoverBitmap(context: Context, url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .build()
            val result = context.imageLoader.execute(request)
            (result as? SuccessResult)?.drawable?.toBitmap()
        }.getOrNull()
    }

    suspend fun resolveTrackColors(context: Context, info: StoryTrackInfo, paletteScheme: StoryColorPalette): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val cover = loadCoverBitmap(context, info.artworkUrl)
        resolveColors(cover, paletteScheme)
    }

    fun resolveColors(cover: Bitmap?, paletteScheme: StoryColorPalette): Pair<Int, Int> {
        return when (paletteScheme) {
            StoryColorPalette.TRUE_DARK -> Pair(Color.rgb(18, 18, 22), Color.rgb(6, 6, 8))
            StoryColorPalette.MIDNIGHT -> Pair(Color.rgb(20, 36, 70), Color.rgb(8, 14, 28))
            StoryColorPalette.SUNSET -> Pair(Color.rgb(155, 30, 68), Color.rgb(42, 10, 20))
            StoryColorPalette.AURA -> {
                if (cover != null) {
                    val palette = Palette.from(cover).generate()
                    val darkVibrant = palette.getDarkVibrantColor(0)
                    val vibrant = palette.getVibrantColor(0)
                    val dominant = palette.getDominantColor(0)
                    val muted = palette.getMutedColor(0)
                    val darkMuted = palette.getDarkMutedColor(0)

                    val topRaw = when {
                        darkVibrant != 0 -> darkVibrant
                        vibrant != 0 -> adjustColor(vibrant, 0.85f, 0.65f)
                        dominant != 0 -> adjustColor(dominant, 0.80f, 0.60f)
                        muted != 0 -> adjustColor(muted, 0.80f, 0.55f)
                        darkMuted != 0 -> darkMuted
                        else -> Color.rgb(55, 30, 42)
                    }
                    val topColor = boostWarmthAndRichness(topRaw)
                    val bottomColor = adjustColor(topColor, 0.90f, 0.32f)
                    Pair(topColor, bottomColor)
                } else {
                    Pair(Color.rgb(44, 46, 56), Color.rgb(16, 17, 22))
                }
            }
        }
    }

    private fun boostWarmthAndRichness(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] = (hsv[1] * 1.15f).coerceIn(0.40f, 0.92f)
        hsv[2] = (hsv[2] * 0.95f).coerceIn(0.35f, 0.75f)
        return Color.HSVToColor(hsv)
    }

    private fun blendColors(color1: Int, color2: Int, ratio: Float): Int {
        val inverseRatio = 1f - ratio
        val a = Color.alpha(color1) * inverseRatio + Color.alpha(color2) * ratio
        val r = Color.red(color1) * inverseRatio + Color.red(color2) * ratio
        val g = Color.green(color1) * inverseRatio + Color.green(color2) * ratio
        val b = Color.blue(color1) * inverseRatio + Color.blue(color2) * ratio
        return Color.argb(a.toInt(), r.toInt(), g.toInt(), b.toInt())
    }

    private fun adjustColor(color: Int, satFactor: Float, valFactor: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] = (hsv[1] * satFactor).coerceIn(0.2f, 0.95f)
        hsv[2] = (hsv[2] * valFactor).coerceIn(0.12f, 0.85f)
        return Color.HSVToColor(hsv)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // FRAME STYLE 1: LIQUID GLASS CARD (The Spotify-elevated Ame centerpiece)
    // ─────────────────────────────────────────────────────────────────────────────
    private fun drawGlassCard(
        context: Context,
        canvas: Canvas,
        cover: Bitmap?,
        info: StoryTrackInfo,
        w: Int,
        h: Int,
    ) {
        val cardWidth = 840f
        val cardHeight = 1100f
        val left = (w - cardWidth) / 2f
        val top = (h - cardHeight) / 2f - 40f
        val right = left + cardWidth
        val bottom = top + cardHeight
        val cornerRadius = 60f

        val cardBounds = RectF(left, top, right, bottom)
        val cardPath = Path().apply { addRoundRect(cardBounds, cornerRadius, cornerRadius, Path.Direction.CW) }

        // 1. Frosted Glass Body with Deep Shadow
        canvas.save()
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 0, 0, 0)
            setShadowLayer(55f, 0f, 28f, Color.argb(120, 0, 0, 0))
        }
        canvas.drawRoundRect(cardBounds, cornerRadius, cornerRadius, shadowPaint)

        // Frosted Glass Fill
        val glassFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                left, top, right, bottom,
                Color.argb(200, 24, 24, 30),
                Color.argb(235, 12, 12, 16),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(cardBounds, cornerRadius, cornerRadius, glassFill)

        // Ambient top glass sheen
        val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, top, 0f, top + cardHeight * 0.45f,
                Color.argb(45, 255, 255, 255),
                Color.argb(0, 255, 255, 255),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(cardBounds, cornerRadius, cornerRadius, sheenPaint)

        // Specular Rim Stroke (1.6px rim lighting: bright top-left, subtle bottom-right)
        val rimStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            shader = LinearGradient(
                left, top, right, bottom,
                Color.argb(180, 255, 255, 255),
                Color.argb(25, 255, 255, 255),
                Shader.TileMode.CLAMP
            )
        }
        val inset = rimStroke.strokeWidth / 2f
        val rimBounds = RectF(left + inset, top + inset, right - inset, bottom - inset)
        canvas.drawRoundRect(rimBounds, cornerRadius - inset, cornerRadius - inset, rimStroke)
        canvas.restore()

        // 2. Artwork inside card (720x720, rounded corners)
        val artPadding = 60f
        val artSize = cardWidth - (artPadding * 2f)
        val artLeft = left + artPadding
        val artTop = top + artPadding
        val artBounds = RectF(artLeft, artTop, artLeft + artSize, artTop + artSize)
        val artCorner = 40f

        if (cover != null) {
            drawRoundedBitmap(canvas, cover, artBounds, artCorner)
        } else {
            val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(40, 40, 48) }
            canvas.drawRoundRect(artBounds, artCorner, artCorner, emptyPaint)
        }

        // Artwork subtle specular border
        val artBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            shader = LinearGradient(
                artLeft, artTop, artLeft + artSize, artTop + artSize,
                Color.argb(120, 255, 255, 255),
                Color.argb(20, 255, 255, 255),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(artBounds, artCorner, artCorner, artBorder)

        // 3. Song Title (Bold, clean, 52px)
        val textX = artLeft + 8f
        var currentY = artTop + artSize + 68f
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 50f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val maxTextWidth = cardWidth - (artPadding * 2f) - 16f
        val ellipsizedTitle = ellipsizeText(info.title, titlePaint, maxTextWidth)
        canvas.drawText(ellipsizedTitle, textX, currentY, titlePaint)

        // 4. Artist Name (Translucent white, 35px)
        currentY += 50f
        val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(204, 255, 255, 255)
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val ellipsizedArtist = ellipsizeText(info.artist, artistPaint, maxTextWidth)
        canvas.drawText(ellipsizedArtist, textX, currentY, artistPaint)

        // 5. Ame Branding Footer (Mascot logo + "ame" + Lossless badge)
        val footerY = bottom - 52f
        drawAmeBranding(context, canvas, textX, footerY, info.isLossless)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // FRAME STYLE 2: VINYL FLEX (Retro 3D Sleeve & Grooved Vinyl Disc)
    // ─────────────────────────────────────────────────────────────────────────────
    private fun drawVinylFlex(
        context: Context,
        canvas: Canvas,
        cover: Bitmap?,
        info: StoryTrackInfo,
        w: Int,
        h: Int,
    ) {
        val centerY = h * 0.44f
        val sleeveSize = 580f
        val sleeveLeft = (w - sleeveSize) / 2f - 90f
        val sleeveTop = centerY - sleeveSize / 2f

        // 1. Draw Vinyl Record sliding out to the right
        val discRadius = sleeveSize / 2f - 10f
        val discCenterX = sleeveLeft + sleeveSize + 40f
        val discCenterY = centerY

        // Disc base
        val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(18, 18, 20)
            setShadowLayer(35f, 10f, 16f, Color.argb(140, 0, 0, 0))
        }
        canvas.drawCircle(discCenterX, discCenterY, discRadius, discPaint)

        // Concentric Vinyl Grooves
        val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
            color = Color.argb(45, 255, 255, 255)
        }
        for (r in listOf(0.48f, 0.58f, 0.68f, 0.78f, 0.86f, 0.94f)) {
            canvas.drawCircle(discCenterX, discCenterY, discRadius * r, groovePaint)
        }

        // Center Vinyl Label with miniature album artwork
        val labelRadius = discRadius * 0.36f
        val labelBounds = RectF(discCenterX - labelRadius, discCenterY - labelRadius, discCenterX + labelRadius, discCenterY + labelRadius)
        if (cover != null) {
            drawRoundedBitmap(canvas, cover, labelBounds, labelRadius)
        }
        // Spindle center hole
        val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(10, 10, 12) }
        canvas.drawCircle(discCenterX, discCenterY, 18f, holePaint)

        // 2. Draw Front Album Sleeve
        val sleeveBounds = RectF(sleeveLeft, sleeveTop, sleeveLeft + sleeveSize, sleeveTop + sleeveSize)
        val sleeveShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            setShadowLayer(48f, 0f, 24f, Color.argb(160, 0, 0, 0))
            color = Color.BLACK
        }
        canvas.drawRoundRect(sleeveBounds, 32f, 32f, sleeveShadow)
        if (cover != null) {
            drawRoundedBitmap(canvas, cover, sleeveBounds, 32f)
        }
        // Sleeve rim
        val sleeveRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            shader = LinearGradient(sleeveLeft, sleeveTop, sleeveLeft + sleeveSize, sleeveTop + sleeveSize,
                Color.argb(130, 255, 255, 255), Color.argb(20, 255, 255, 255), Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(sleeveBounds, 32f, 32f, sleeveRim)

        // 3. Floating Glass Metadata Capsule below
        val pillWidth = 840f
        val pillHeight = 220f
        val pillLeft = (w - pillWidth) / 2f
        val pillTop = centerY + sleeveSize / 2f + 70f
        val pillBounds = RectF(pillLeft, pillTop, pillLeft + pillWidth, pillTop + pillHeight)

        drawGlassPanel(canvas, pillBounds, 48f)

        // Text & Branding inside pill
        val textX = pillLeft + 48f
        val titleY = pillTop + 72f
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 46f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(ellipsizeText(info.title, titlePaint, pillWidth - 96f), textX, titleY, titlePaint)

        val artistY = titleY + 48f
        val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(204, 255, 255, 255)
            textSize = 32f
        }
        canvas.drawText(ellipsizeText(info.artist, artistPaint, pillWidth - 96f), textX, artistY, artistPaint)

        drawAmeBranding(context, canvas, textX, pillTop + pillHeight - 32f, info.isLossless)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // FRAME STYLE 3: MINIMAL GLASS PILL
    // ─────────────────────────────────────────────────────────────────────────────
    private fun drawGlassPill(
        context: Context,
        canvas: Canvas,
        cover: Bitmap?,
        info: StoryTrackInfo,
        w: Int,
        h: Int,
    ) {
        val pillWidth = 920f
        val pillHeight = 260f
        val pillLeft = (w - pillWidth) / 2f
        val pillTop = h * 0.45f
        val pillBounds = RectF(pillLeft, pillTop, pillLeft + pillWidth, pillTop + pillHeight)

        drawGlassPanel(canvas, pillBounds, 64f)

        // Circular album thumb on left
        val artSize = 180f
        val artLeft = pillLeft + 40f
        val artTop = pillTop + (pillHeight - artSize) / 2f
        val artBounds = RectF(artLeft, artTop, artLeft + artSize, artTop + artSize)
        if (cover != null) {
            drawRoundedBitmap(canvas, cover, artBounds, artSize / 2f)
        }

        // Text block
        val textLeft = artLeft + artSize + 36f
        val maxTextW = pillWidth - (artSize + 110f)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(ellipsizeText(info.title, titlePaint, maxTextW), textLeft, pillTop + 84f, titlePaint)

        val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(204, 255, 255, 255)
            textSize = 32f
        }
        canvas.drawText(ellipsizeText(info.artist, artistPaint, maxTextW), textLeft, pillTop + 132f, artistPaint)

        drawAmeBranding(context, canvas, textLeft, pillTop + 205f, info.isLossless)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // FRAME STYLE 4: HERO CINEMA POSTER
    // ─────────────────────────────────────────────────────────────────────────────
    private fun drawHeroPoster(
        context: Context,
        canvas: Canvas,
        cover: Bitmap?,
        info: StoryTrackInfo,
        w: Int,
        h: Int,
        topColor: Int,
        bottomColor: Int,
    ) {
        val heroHeight = h * 0.62f
        if (cover != null) {
            // Full-bleed top artwork
            val scaledCover = Bitmap.createScaledBitmap(cover, w, heroHeight.toInt(), true)
            canvas.drawBitmap(scaledCover, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            scaledCover.recycle()

            // Smooth vertical gradient dissolve at bottom of artwork
            val fadeHeight = heroHeight * 0.40f
            val fadePaint = Paint().apply {
                shader = LinearGradient(
                    0f, heroHeight - fadeHeight, 0f, heroHeight,
                    Color.TRANSPARENT,
                    bottomColor,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, heroHeight - fadeHeight, w.toFloat(), heroHeight + 2f, fadePaint)
        }

        // Bottom Glass Capsule for Title & Singer
        val pillWidth = 900f
        val pillHeight = 260f
        val pillLeft = (w - pillWidth) / 2f
        val pillTop = heroHeight + 50f
        val pillBounds = RectF(pillLeft, pillTop, pillLeft + pillWidth, pillTop + pillHeight)

        drawGlassPanel(canvas, pillBounds, 52f)

        val textX = pillLeft + 52f
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 50f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(ellipsizeText(info.title, titlePaint, pillWidth - 104f), textX, pillTop + 86f, titlePaint)

        val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(204, 255, 255, 255)
            textSize = 34f
        }
        canvas.drawText(ellipsizeText(info.artist, artistPaint, pillWidth - 104f), textX, pillTop + 140f, artistPaint)

        drawAmeBranding(context, canvas, textX, pillTop + 215f, info.isLossless)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // COMMON DRAWING PRIMITIVES & BRANDING
    // ─────────────────────────────────────────────────────────────────────────────

    private fun drawGlassPanel(canvas: Canvas, bounds: RectF, radius: Float) {
        // Shadow
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            setShadowLayer(44f, 0f, 22f, Color.argb(120, 0, 0, 0))
        }
        canvas.drawRoundRect(bounds, radius, radius, shadow)

        // Glass Fill
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                bounds.left, bounds.top, bounds.right, bounds.bottom,
                Color.argb(190, 26, 26, 32),
                Color.argb(230, 14, 14, 18),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(bounds, radius, radius, fill)

        // Specular Rim
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.4f
            shader = LinearGradient(
                bounds.left, bounds.top, bounds.right, bounds.bottom,
                Color.argb(170, 255, 255, 255),
                Color.argb(25, 255, 255, 255),
                Shader.TileMode.CLAMP
            )
        }
        val inset = stroke.strokeWidth / 2f
        canvas.drawRoundRect(
            RectF(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset),
            radius - inset, radius - inset, stroke
        )
    }

    private fun drawRoundedBitmap(canvas: Canvas, source: Bitmap, bounds: RectF, cornerRadius: Float) {
        val path = Path().apply { addRoundRect(bounds, cornerRadius, cornerRadius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(path)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, null, bounds, paint)
        canvas.restore()
    }

    private fun drawAmeBranding(
        context: Context,
        canvas: Canvas,
        x: Float,
        baselineY: Float,
        isLossless: Boolean,
    ) {
        // 1. Ame Mascot Vector Icon (`ic_app_mark`)
        val iconSize = 42
        val markDrawable = ContextCompat.getDrawable(context, R.drawable.ic_app_mark)
        if (markDrawable != null) {
            val markBitmap = markDrawable.toBitmap(iconSize, iconSize)
            canvas.drawBitmap(markBitmap, x, baselineY - iconSize + 6f, Paint(Paint.FILTER_BITMAP_FLAG))
        }

        // 2. "ame" wordmark
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val textStartX = x + iconSize + 16f
        canvas.drawText("ame", textStartX, baselineY, brandPaint)

        // 3. Lossless Waveform Badge (if lossless)
        if (isLossless) {
            val losslessX = textStartX + 95f
            val waveWidth = 32
            val waveHeight = 22
            val waveDrawable = ContextCompat.getDrawable(context, R.drawable.ic_apple_lossless)
            if (waveDrawable != null) {
                val waveBitmap = waveDrawable.toBitmap(waveWidth, waveHeight)
                canvas.drawBitmap(waveBitmap, losslessX, baselineY - waveHeight + 2f, Paint(Paint.FILTER_BITMAP_FLAG))
            }
            val losslessPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(200, 255, 255, 255)
                textSize = 24f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            canvas.drawText("Lossless", losslessX + waveWidth + 10f, baselineY, losslessPaint)
        }
    }

    private fun ellipsizeText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) {
            end--
        }
        return text.substring(0, end) + "…"
    }
}
