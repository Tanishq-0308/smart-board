package com.smartboard.teach.feature.maths3d

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.smartboard.teach.R
import kotlin.math.min

/** A drawn shape and how it became a solid. */
data class Piece(val shape: Shape2D, val mode: Mode, val height: Double)

/** Screen state for 3D Maths. Pure in-memory: a lesson aid, nothing to persist. */
class Maths3DViewModel : ViewModel() {
    // The SELECTED piece. Mode chips, height slider and the maths panel act on it.
    var shape by mutableStateOf<Shape2D?>(null); private set
    var mode by mutableStateOf<Mode?>(null); private set
    var height by mutableDoubleStateOf(4.0); private set

    /** Every other piece on the board, kept when Multi is on. */
    var others by mutableStateOf<List<Piece>>(emptyList()); private set

    /** On: each new shape is added. Off: a new shape replaces everything. */
    var multi by mutableStateOf(false); private set

    var pi by mutableStateOf(PiValue.DECIMAL); private set
    var message by mutableStateOf<Txt>(NOTHING); private set

    /** Bumped to (re)play the grow animation. */
    var growKey by mutableIntStateOf(0); private set

    private data class Snapshot(val shape: Shape2D?, val mode: Mode?, val height: Double, val others: List<Piece>)

    private val history = ArrayDeque<Snapshot>()

    val canUndo get() = history.isNotEmpty()

    fun onStroke(points: List<P>) {
        // With several shapes on the board, a tap picks which one to work on.
        if (multi && pathLength(points) < TAP_CM) {
            points.firstOrNull()?.let(::select)
            return
        }
        val recognised = recognise(points)
        if (recognised == null) message = Txt.Res(R.string.m3d_msg_unreadable)
        else setShape(recognised)
    }

    fun preset(p: Preset) = setShape(p.shape, p.mode, p.h)

    fun clear() = setShape(null)

    fun toggleMulti() {
        multi = !multi
        if (multi) message = Txt.Res(R.string.m3d_msg_multi)
    }

    fun undo() {
        val prev = history.removeLastOrNull() ?: return
        shape = prev.shape
        mode = prev.mode
        height = prev.height
        others = prev.others
        message = prev.shape?.let(::describe) ?: NOTHING
        growKey++
    }

    fun selectMode(m: Mode) {
        mode = m
        growKey++
    }

    fun changeHeight(h: Double) { height = h }

    fun togglePi() {
        pi = if (pi == PiValue.DECIMAL) PiValue.FRACTION else PiValue.DECIMAL
    }

    fun grow() { growKey++ }

    private fun current(): Piece? {
        val s = shape ?: return null
        val m = mode ?: return null
        return Piece(s, m, height)
    }

    private fun record() = history.addLast(Snapshot(shape, mode, height, others))

    /** Makes the topmost piece under [at] the selected one. */
    private fun select(at: P) {
        val hit = others.indexOfLast { contains(it.shape, at) }
        if (hit < 0) return
        record()
        val picked = others[hit]
        others = others.toMutableList().apply {
            removeAt(hit)
            current()?.let(::add)
        }
        shape = picked.shape
        mode = picked.mode
        height = picked.height
        message = describe(picked.shape)
    }

    private fun setShape(next: Shape2D?, nextMode: Mode? = null, h: Double? = null) {
        record()
        others = if (next != null && multi) others + listOfNotNull(current()) else emptyList()
        shape = next
        if (next == null) {
            mode = null
            message = NOTHING
        } else {
            mode = nextMode ?: modesFor(next).first().mode
            height = when {
                h != null -> h
                next is Shape2D.Rect && next.square -> next.w
                next is Shape2D.Circle -> min(10.0, snap(next.r * 2))
                else -> height
            }.coerceIn(0.5, 10.0)
            message = describe(next)
        }
        growKey++
    }

    private fun describe(s: Shape2D) = Txt.Res(R.string.m3d_msg_recognised, Txt.Res(s.name), when (s) {
        is Shape2D.Circle -> "r = ${fmt(s.r)} cm".raw
        is Shape2D.Rect -> if (s.square) Txt.Res(R.string.m3d_detail_side, fmt(s.w)) else "l = ${fmt(s.w)}, b = ${fmt(s.h)} cm".raw
        is Shape2D.Profile -> Txt.Res(R.string.m3d_detail_profile)
        is Shape2D.Polygon -> Txt.Plural(R.plurals.m3d_sides, s.pts.size)
    })

    private companion object {
        val NOTHING = Txt.Res(R.string.m3d_msg_nothing)

        /** A stroke shorter than this (cm) is a tap, not a drawing. */
        const val TAP_CM = 0.5
    }
}
