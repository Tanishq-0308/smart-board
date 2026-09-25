package com.smartboard.teach.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smartboard.teach.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.inputSettingsDataStore by preferencesDataStore(name = "input_settings")

/**
 * Input behaviour toggles — the escape hatch for OEM stylus quirks.
 *
 * Board vendors (SMART, Promethean, ViewSonic, BenQ) ship proprietary pen
 * stacks with well-known failure modes: pressure pinned to 1.0, the pen
 * reported as TOOL_TYPE_FINGER, eraser buttons swallowed by firmware. These
 * settings let a teacher or installer correct bad hardware reporting on site
 * without a code change and a reinstall.
 */
data class InputSettings(
    /** Hard-reject all touch input; only the pen draws. */
    val stylusOnlyMode: Boolean = false,
    /** Off for boards that report a constant or nonsense pressure. */
    val pressureSensitivity: Boolean = true,
    /** Honour PointerType.Eraser as an eraser regardless of selected tool. */
    val honourEraserButton: Boolean = true,
    /** Live pointer telemetry overlay — the hardware bring-up tool. */
    val showPointerDebug: Boolean = false,
    val use24HourClock: Boolean = false,
    /** Pen colours the teacher mixed in the picker, newest first, as ARGB. */
    val customPenColors: List<Int> = emptyList(),
)

@Singleton
class InputSettingsStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private object Keys {
        val stylusOnly = booleanPreferencesKey("stylus_only")
        val pressure = booleanPreferencesKey("pressure_sensitivity")
        val eraserButton = booleanPreferencesKey("honour_eraser_button")
        val pointerDebug = booleanPreferencesKey("pointer_debug")
        val clock24h = booleanPreferencesKey("clock_24h")
        val customPenColors = stringPreferencesKey("custom_pen_colors")
    }

    val settings: Flow<InputSettings> = context.inputSettingsDataStore.data.map { prefs ->
        InputSettings(
            stylusOnlyMode = prefs[Keys.stylusOnly] ?: false,
            pressureSensitivity = prefs[Keys.pressure] ?: true,
            honourEraserButton = prefs[Keys.eraserButton] ?: true,
            showPointerDebug = prefs[Keys.pointerDebug] ?: false,
            use24HourClock = prefs[Keys.clock24h] ?: false,
            customPenColors = parseColors(prefs[Keys.customPenColors]),
        )
    }

    suspend fun setStylusOnly(value: Boolean) = put(Keys.stylusOnly, value)
    suspend fun setPressureSensitivity(value: Boolean) = put(Keys.pressure, value)
    suspend fun setHonourEraserButton(value: Boolean) = put(Keys.eraserButton, value)
    suspend fun setPointerDebug(value: Boolean) = put(Keys.pointerDebug, value)
    suspend fun setUse24HourClock(value: Boolean) = put(Keys.clock24h, value)

    /**
     * Saves a picked colour to the pen's Extras: newest first, no duplicates,
     * and only the last [MAX_CUSTOM_PEN_COLORS] kept so the row stays one
     * tidy block instead of growing down the panel.
     */
    suspend fun addCustomPenColor(argb: Int) = withContext(ioDispatcher) {
        context.inputSettingsDataStore.edit { prefs ->
            val kept = listOf(argb) + parseColors(prefs[Keys.customPenColors]).filter { it != argb }
            prefs[Keys.customPenColors] = kept.take(MAX_CUSTOM_PEN_COLORS).joinToString(",")
        }
        Unit
    }

    /** Drops a colour from the pen's Extras. */
    suspend fun removeCustomPenColor(argb: Int) = withContext(ioDispatcher) {
        context.inputSettingsDataStore.edit { prefs ->
            prefs[Keys.customPenColors] =
                parseColors(prefs[Keys.customPenColors]).filter { it != argb }.joinToString(",")
        }
        Unit
    }

    // A comma list of ARGB ints; anything unreadable is dropped, never fatal.
    private fun parseColors(raw: String?): List<Int> =
        raw?.split(',')?.mapNotNull { it.trim().toLongOrNull()?.toInt() } ?: emptyList()

    private suspend fun put(
        key: androidx.datastore.preferences.core.Preferences.Key<Boolean>,
        value: Boolean,
    ) = withContext(ioDispatcher) {
        context.inputSettingsDataStore.edit { it[key] = value }
        Unit
    }
}

const val MAX_CUSTOM_PEN_COLORS = 5
