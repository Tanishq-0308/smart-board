package com.smartboard.teach.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MathTextTest {

    private fun check(latex: String, expected: String) = assertEquals(expected, MathText.readable(latex))

    @Test fun plainTextIsUntouched() = check("Area of a circle", "Area of a circle")

    @Test fun delimitersGo() = check("""The area is \( A \).""", "The area is A.")

    @Test fun powersAndFractions() {
        check("""\( E = \frac{1}{2}mv^2 \)""", "E = ½mv²")
        check("""\( A = \pi r^{2} \)""", "A = π r²")
        check("""\[ x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a} \]""", "x = (-b ± √(b² - 4ac))/2a")
    }

    @Test fun nestedFraction() = check("""\frac{a}{\frac{b}{c}}""", "a/(b/c)")

    @Test fun subscriptsAndDegrees() {
        check("""\( v_0 + a t \)""", "v₀ + a t")
        check("""\( 90^\circ \)""", "90°")
        check("""\( \angle ABC = 60^{\circ} \)""", "∠ ABC = 60°")
    }

    @Test fun chemistry() {
        check("""\( \ce{H2SO4} \)""", "H₂SO₄")
        check("""\ce{2H2 + O2 -> 2H2O}""", "2H₂ + O₂ → 2H₂O")
        check("""\ce{Fe^{3+}}""", "Fe³⁺")
    }

    @Test fun unknownCommandsKeepTheirName() = check("""\( \sin\theta \)""", "sinθ")

    @Test fun powersWithoutSuperscriptFormStayReadable() = check("""\( e^{i\pi} \)""", "e^(iπ)")
}
