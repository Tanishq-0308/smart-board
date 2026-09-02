package com.smartboard.teach.data.labs

import kotlinx.serialization.Serializable

/**
 * The lab catalogue, as the labs site publishes it at `labs.json`.
 *
 * The board does not know the name of a single lab. It reads this, and shows
 * what it finds — so a lab added to the site appears on the board without this
 * app being touched, rebuilt or released. That is the whole point of the file.
 *
 * Every field the app does not recognise is ignored (the injected [Json] is
 * configured with `ignoreUnknownKeys`), which matters more than it looks: a
 * later version of the manifest will carry fields this build has never heard
 * of, and a board in a classroom has to go on working rather than throw.
 */
@Serializable
data class LabManifest(
    val name: String = "",
    val digest: String = "",
    val bridgeVersion: Int = 1,
    val shelf: String = "#/embed",
    val subjects: List<LabSubject> = emptyList(),
    val labs: List<LabEntry> = emptyList(),
) {
    /** Entries with no component behind them yet render a placeholder, not a lab. */
    val ready: List<LabEntry> get() = labs.filter { it.status == STATUS_READY }

    fun subject(id: String): LabSubject? = subjects.firstOrNull { it.id == id }

    companion object {
        const val STATUS_READY = "ready"

        /** The protocol this app speaks. See LabBridge. */
        const val SUPPORTED_BRIDGE = 1
    }
}

@Serializable
data class LabSubject(
    val id: String,
    val name: String,
    /** An `#rrggbb` the board can tint a card with, so subjects stay tellable apart. */
    val accent: String = "#2F6FED",
    val blurb: String = "",
)

@Serializable
data class LabEntry(
    val slug: String,
    val title: String,
    val topic: String = "",
    val subject: String = "",
    val level: String = "",
    val status: String = "",
    val blurb: String = "",
    val summary: String = "",
    val tags: List<String> = emptyList(),
    val teaches: List<String> = emptyList(),
    /**
     * Where to open this lab, relative to the site root — `#/embed/optics`.
     *
     * Join it onto the base rather than building the address here. Then a
     * change to how labs are addressed stays a change to the site, and does
     * not need a release of this app.
     */
    val embed: String = "",
    val page: String = "",
)
