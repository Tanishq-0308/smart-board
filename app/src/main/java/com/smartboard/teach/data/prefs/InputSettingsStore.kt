package com.smartboard.teach.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
    /**
     * Snap rough freehand circles, rectangles and lines to clean shapes.
     *
     * Default ON because it makes diagrams look right, but a toggle because a
     * geometry teacher demonstrating why a freehand circle ISN'T a circle
     * would be actively sabotaged by silent correction.
     */
    val shapeRecognition: Boolean = true,
    val use24HourClock: Boolean = false,
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
        val shapeRecognition = booleanPreferencesKey("shape_recognition")
    }

    val settings: Flow<InputSettings> = context.inputSettingsDataStore.data.map { prefs ->
        InputSettings(
            stylusOnlyMode = prefs[Keys.stylusOnly] ?: false,
            pressureSensitivity = prefs[Keys.pressure] ?: true,
            honourEraserButton = prefs[Keys.eraserButton] ?: true,
            showPointerDebug = prefs[Keys.pointerDebug] ?: false,
            use24HourClock = prefs[Keys.clock24h] ?: false,
            shapeRecognition = prefs[Keys.shapeRecognition] ?: true,
        )
    }

    suspend fun setStylusOnly(value: Boolean) = put(Keys.stylusOnly, value)
    suspend fun setPressureSensitivity(value: Boolean) = put(Keys.pressure, value)
    suspend fun setHonourEraserButton(value: Boolean) = put(Keys.eraserButton, value)
    suspend fun setPointerDebug(value: Boolean) = put(Keys.pointerDebug, value)
    suspend fun setUse24HourClock(value: Boolean) = put(Keys.clock24h, value)
    suspend fun setShapeRecognition(value: Boolean) = put(Keys.shapeRecognition, value)

    private suspend fun put(
        key: androidx.datastore.preferences.core.Preferences.Key<Boolean>,
        value: Boolean,
    ) = withContext(ioDispatcher) {
        context.inputSettingsDataStore.edit { it[key] = value }
        Unit
    }
}
