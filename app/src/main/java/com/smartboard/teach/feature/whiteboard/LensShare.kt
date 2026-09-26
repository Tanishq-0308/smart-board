package com.smartboard.teach.feature.whiteboard

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.smartboard.teach.R

/**
 * Hands a cropped board region to an external visual-search app.
 *
 * There is no public Google Lens SDK or API, so "search with Lens" cannot be
 * an in-app panel. What IS supported, and what Google Photos itself relies on,
 * is an image share intent: Lens (via the Google app) and Photos both register
 * as ACTION_SEND receivers for image MIME types, so they appear in the
 * chooser alongside anything else the board has installed.
 *
 * The chooser is shown deliberately rather than resolving a hard-coded Google
 * package. Education boards vary wildly in what is installed, some ship
 * without Google apps entirely, and a hard-coded component would simply fail
 * to resolve there.
 */
object LensShare {

    /**
     * @return false when the board has nothing that can receive an image, so
     *         the caller can say so instead of failing silently.
     */
    fun shareImage(context: Context, uri: Uri, title: String = context.getString(R.string.panel_lens_share_title)): Boolean {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            // Without this the receiving app gets a URI it is not permitted
            // to open; the FileProvider grant is per-intent.
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(send, title).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return try {
            context.startActivity(chooser)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    /** Something on the board can receive an image (Lens, Photos, ...). */
    fun canShareImage(context: Context): Boolean =
        Intent(Intent.ACTION_SEND).setType("image/*")
            .resolveActivity(context.packageManager) != null

    /** A browser is installed to open a web search in. */
    fun canBrowse(context: Context): Boolean =
        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
            .resolveActivity(context.packageManager) != null

    /**
     * Opens a plain web search for [query].
     *
     * Complements the image share: once the model has read a scrawled
     * equation and produced proper terminology, a text search on that
     * terminology is usually more useful than an image search on the
     * handwriting itself.
     */
    fun searchWeb(context: Context, query: String): Boolean {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)),
        )
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
