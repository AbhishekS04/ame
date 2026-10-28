package com.abhishek.ame.ui.story

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Dispatches sharing intents to Snapchat, Instagram Stories, and Android system share sheets.
 */
object StoryShareHelper {

    private const val INSTAGRAM_PACKAGE = "com.instagram.android"
    private const val SNAPCHAT_PACKAGE = "com.snapchat.android"
    private const val ADD_TO_STORY_ACTION = "com.instagram.share.ADD_TO_STORY"

    fun isInstagramInstalled(context: Context): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(INSTAGRAM_PACKAGE, 0)
            true
        }.getOrDefault(false)
    }

    fun isSnapchatInstalled(context: Context): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(SNAPCHAT_PACKAGE, 0)
            true
        }.getOrDefault(false)
    }

    /**
     * Shares directly to Instagram Stories:
     * - Places the Ame Liquid Glass card as an interactive sticker in the center.
     * - Configures the dynamic top & bottom background gradient matching the song artwork.
     * - Passes deep link to open the track in Ame.
     */
    fun shareToInstagramStory(
        context: Context,
        stickerFile: File,
        topColorHex: String,
        bottomColorHex: String,
        contentUrl: String = "https://ame.app",
    ): Boolean {
        return runCatching {
            val authority = "${context.packageName}.reports"
            val uri: Uri = FileProvider.getUriForFile(context, authority, stickerFile)

            val intent = Intent(ADD_TO_STORY_ACTION).apply {
                type = "image/png"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("interactive_asset_uri", uri)
                putExtra("top_background_color", topColorHex)
                putExtra("bottom_background_color", bottomColorHex)
                putExtra("content_url", contentUrl)
                putExtra("source_application", "com.abhishek.ame")
            }

            context.grantUriPermission(INSTAGRAM_PACKAGE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

            if (isInstagramInstalled(context)) {
                intent.setPackage(INSTAGRAM_PACKAGE)
                context.startActivity(intent)
                true
            } else {
                shareImage(context, stickerFile, "Ame Music Story")
                false
            }
        }.getOrElse {
            shareImage(context, stickerFile, "Ame Music Story")
            false
        }
    }

    /**
     * Shares a story directly to Snapchat (opens Snapchat with the image/video ready to post).
     */
    fun shareToSnapchat(
        context: Context,
        file: File,
        isVideo: Boolean = false,
        title: String = "Ame Music Story",
    ): Boolean {
        return runCatching {
            val authority = "${context.packageName}.reports"
            val uri: Uri = FileProvider.getUriForFile(context, authority, file)

            val mimeType = if (isVideo) "video/mp4" else "image/png"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (isSnapchatInstalled(context)) {
                intent.setPackage(SNAPCHAT_PACKAGE)
                context.grantUriPermission(SNAPCHAT_PACKAGE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(intent)
                true
            } else {
                context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                false
            }
        }.getOrElse {
            false
        }
    }

    /**
     * Shares a generated video story (with sound) to Instagram Stories, Snapchat, or the system share sheet.
     */
    fun shareStoryVideo(
        context: Context,
        videoFile: File,
        title: String = "Ame Music Story",
    ) {
        runCatching {
            val authority = "${context.packageName}.reports"
            val uri: Uri = FileProvider.getUriForFile(context, authority, videoFile)

            if (isInstagramInstalled(context)) {
                val intent = Intent(ADD_TO_STORY_ACTION).apply {
                    setDataAndType(uri, "video/mp4")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setPackage(INSTAGRAM_PACKAGE)
                    putExtra("source_application", "com.abhishek.ame")
                }
                context.grantUriPermission(INSTAGRAM_PACKAGE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(intent)
            } else {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, title)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    /**
     * Shares a high-resolution image poster to any installed application via Android Share Sheet.
     */
    fun shareImage(
        context: Context,
        imageFile: File,
        title: String = "Ame Music Story",
    ) {
        runCatching {
            val authority = "${context.packageName}.reports"
            val uri: Uri = FileProvider.getUriForFile(context, authority, imageFile)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
