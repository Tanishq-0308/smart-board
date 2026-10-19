package com.smartboard.teach.feature.whiteboard

import com.smartboard.teach.domain.model.DrawTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AreaTemplatesTest {

    @Test
    fun `every layout fits the space it is given, centred`() {
        AreaTemplate.entries.forEach { template ->
            val marks = AreaTemplates.place(template, cx = 500f, cy = 300f, maxW = 800f, maxH = 400f)
            val xs = marks.flatMap { m -> m.xy.filterIndexed { i, _ -> i % 2 == 0 } }
            val ys = marks.flatMap { m -> m.xy.filterIndexed { i, _ -> i % 2 == 1 } }
            assertTrue("$template", xs.all { it.isFinite() } && ys.all { it.isFinite() })
            assertTrue("$template width", xs.max() - xs.min() <= 800.5f)
            assertTrue("$template height", ys.max() - ys.min() <= 400.5f)
            assertEquals("$template x", 500f, (xs.max() + xs.min()) / 2f, 0.5f)
            assertEquals("$template y", 300f, (ys.max() + ys.min()) / 2f, 0.5f)
        }
    }

    @Test
    fun `straight marks are two-point lines`() {
        AreaTemplate.entries.forEach { template ->
            AreaTemplates.marksOf(template).filter { it.tool == DrawTool.LINE }.forEach {
                assertEquals("$template", 4, it.xy.size)
            }
        }
    }

    @Test
    fun `a Go board has nineteen lines each way and nine star points`() {
        val marks = AreaTemplates.marksOf(AreaTemplate.GO)
        assertEquals(38 + 4, marks.count { it.tool == DrawTool.LINE })
        assertEquals(9, marks.count { it.xy.size == 2 })
    }
}
