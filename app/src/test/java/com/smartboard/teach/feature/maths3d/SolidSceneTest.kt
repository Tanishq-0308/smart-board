package com.smartboard.teach.feature.maths3d

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class SolidSceneTest {

    @Test
    fun `a scene reads back exactly as it was saved`() {
        val scene = SolidScene(
            pieces = listOf(
                ScenePiece("circle", Mode.CYLINDER, 4.0, cx = 1.0, cy = 2.0, r = 1.5),
                ScenePiece("polygon", Mode.PRISM, 6.0, pts = listOf(0.0, 0.0, 4.0, 0.0, 2.0, 3.0), name = "m3d_shape_equilateral_triangle", regular = true),
            ),
            yaw = 0.7f,
            pitch = -0.2f,
            pi = PiValue.FRACTION,
        )
        val json = Json.encodeToString(SolidScene.serializer(), scene)
        assertEquals(scene, Json.decodeFromString(SolidScene.serializer(), json))
    }

    @Test
    fun `the board draws a solid from the picture beside its scene`() {
        assertEquals("/x/solids/abc_view.png", SolidSceneFiles.posterPathFor("/x/solids/abc.json"))
    }
}
