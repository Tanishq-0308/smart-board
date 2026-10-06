package com.smartboard.teach.core.util

/**
 * Turns the LaTeX / mhchem the AI writes (`\( \frac{1}{2}mv^2 \)`,
 * `\ce{H2SO4}`) into readable Unicode maths (`½mv²`, `H₂SO₄`) for display.
 *
 * No renderer dependency and no WebView: board panels show plain text, and
 * school maths and chemistry is overwhelmingly fractions, powers, roots,
 * Greek letters and formulas, which Unicode covers.
 * ponytail: no 2-D layout (stacked fractions, matrices); add a native maths
 * renderer (AndroidMath, MIT, evaluated in docs/evaluations) if teachers need it.
 */
object MathText {

    /** Text with no LaTeX in it is returned untouched. */
    fun readable(text: String): String {
        if ('\\' !in text && '$' !in text) return text
        var s = text
        // Delimiters.
        s = s.replace(Regex("""\\\(\s*|\s*\\\)|\\\[\s*|\s*\\\]|\$\$|\$"""), "")
        // mhchem first: its digits mean subscripts, not numbers.
        s = replaceGroups(s, "ce") { chem(it) }
        // Wrappers whose content is plain text.
        for (cmd in listOf("text", "mathrm", "mathbf", "mathit", "operatorname", "textbf", "boldsymbol")) {
            s = replaceGroups(s, cmd) { it }
        }
        s = s.replace(Regex("""\\left\s*|\\right\s*"""), "")
        // Innermost-first so nested fractions and roots resolve.
        repeat(MAX_NESTING) {
            val before = s
            s = Regex("""\\[dt]?frac\{([^{}]*)\}\{([^{}]*)\}""").replace(s) { m ->
                fraction(m.groupValues[1].trim(), m.groupValues[2].trim())
            }
            s = Regex("""\\sqrt\[([^\]]*)\]\{([^{}]*)\}""").replace(s) { m ->
                superscript(m.groupValues[1]) + "√" + group(m.groupValues[2])
            }
            s = Regex("""\\sqrt\{([^{}]*)\}""").replace(s) { m -> "√" + group(m.groupValues[1]) }
            s = Regex("""\^\{([^{}]*)\}""").replace(s) { m -> power(m.groupValues[1]) }
            s = Regex("""_\{([^{}]*)\}""").replace(s) { m -> index(m.groupValues[1]) }
            if (s == before) return@repeat
        }
        s = Regex("""\^(\\circ|[A-Za-z0-9+\-])""").replace(s) { m ->
            if (m.groupValues[1] == "\\circ") "°" else power(m.groupValues[1])
        }
        s = Regex("""_([A-Za-z0-9])""").replace(s) { m -> index(m.groupValues[1]) }
        // Named symbols, longest first so \leq is not read as \le + q.
        for ((cmd, symbol) in SYMBOLS.entries.sortedByDescending { it.key.length }) {
            s = s.replace(Regex("""\\$cmd(?![A-Za-z])"""), symbol)
        }
        s = s.replace(Regex("""\\[,;:! ]|\\quad|\\qquad"""), " ")
        // Unknown commands (\sin, \log, …) read fine as their names.
        s = s.replace(Regex("""\\([A-Za-z]+)"""), "$1")
        s = s.replace("{", "").replace("}", "")
        return s.replace(Regex("""[ \t]{2,}"""), " ").trim()
    }

    private fun replaceGroups(s: String, cmd: String, transform: (String) -> String): String =
        // One level of nested braces, so \ce{Fe^{3+}} is matched whole.
        Regex("""\\$cmd\{((?:[^{}]|\{[^{}]*\})*)\}""").replace(s) { m -> transform(m.groupValues[1]) }

    private fun fraction(top: String, bottom: String): String =
        VULGAR["$top/$bottom"] ?: "${group(top)}/${group(bottom)}"

    /** Wraps anything longer than one token in brackets, so a/(b+c) stays right. */
    private fun group(x: String): String =
        if (x.length <= 1 || x.all { it.isLetterOrDigit() || it == '.' } || x.startsWith("(")) x else "($x)"

    private fun power(x: String): String =
        if (x.trim() == "\\circ") "°" else superscript(x) ?: "^($x)"

    private fun index(x: String): String = subscript(x) ?: "_($x)"

    private fun superscript(x: String): String? =
        if (x.all { it in SUPER }) x.map { SUPER.getValue(it) }.joinToString("") else null

    private fun subscript(x: String): String? =
        if (x.all { it in SUB }) x.map { SUB.getValue(it) }.joinToString("") else null

    /** H2SO4 -> H₂SO₄, Fe^{3+} -> Fe³⁺, -> -> →. */
    private fun chem(x: String): String {
        var s = x.replace("<=>", "⇌").replace("->", "→").replace("<-", "←")
        s = Regex("""\^\{?([0-9]*[+\-])\}?""").replace(s) { m -> superscript(m.groupValues[1]) ?: m.value }
        s = Regex("""([A-Za-z)\]])(\d+)""").replace(s) { m -> m.groupValues[1] + (subscript(m.groupValues[2]) ?: m.groupValues[2]) }
        return s
    }

    private const val MAX_NESTING = 6

    private val SUPER = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵', '6' to '⁶',
        '7' to '⁷', '8' to '⁸', '9' to '⁹', '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'n' to 'ⁿ', 'i' to 'ⁱ', 'x' to 'ˣ', 'y' to 'ʸ', 'a' to 'ᵃ', 'b' to 'ᵇ', 'c' to 'ᶜ', 'd' to 'ᵈ',
        'e' to 'ᵉ', 'k' to 'ᵏ', 'm' to 'ᵐ', 'o' to 'ᵒ', 'p' to 'ᵖ', 'r' to 'ʳ', 's' to 'ˢ', 't' to 'ᵗ',
        'T' to 'ᵀ',
    )

    private val SUB = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄', '5' to '₅', '6' to '₆',
        '7' to '₇', '8' to '₈', '9' to '₉', '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
        'a' to 'ₐ', 'e' to 'ₑ', 'i' to 'ᵢ', 'n' to 'ₙ', 'o' to 'ₒ', 'r' to 'ᵣ', 'x' to 'ₓ', 'm' to 'ₘ',
        'k' to 'ₖ', 's' to 'ₛ', 't' to 'ₜ',
    )

    private val VULGAR = mapOf(
        "1/2" to "½", "1/3" to "⅓", "2/3" to "⅔", "1/4" to "¼", "3/4" to "¾",
        "1/5" to "⅕", "1/8" to "⅛",
    )

    private val SYMBOLS = mapOf(
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ε",
        "varepsilon" to "ε", "theta" to "θ", "lambda" to "λ", "mu" to "μ", "pi" to "π",
        "rho" to "ρ", "sigma" to "σ", "tau" to "τ", "phi" to "φ", "varphi" to "φ", "omega" to "ω",
        "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ", "Pi" to "Π",
        "Sigma" to "Σ", "Phi" to "Φ", "Omega" to "Ω",
        "times" to "×", "cdot" to "·", "div" to "÷", "pm" to "±", "mp" to "∓",
        "leq" to "≤", "le" to "≤", "geq" to "≥", "ge" to "≥", "neq" to "≠", "ne" to "≠",
        "approx" to "≈", "equiv" to "≡", "propto" to "∝", "infty" to "∞",
        "rightarrow" to "→", "to" to "→", "leftarrow" to "←", "Rightarrow" to "⇒",
        "Leftrightarrow" to "⇔", "leftrightarrow" to "↔", "rightleftharpoons" to "⇌",
        "degree" to "°", "circ" to "°", "angle" to "∠", "triangle" to "△", "perp" to "⊥",
        "parallel" to "∥", "sum" to "Σ", "int" to "∫", "partial" to "∂", "nabla" to "∇",
        "in" to "∈", "notin" to "∉", "subset" to "⊂", "cup" to "∪", "cap" to "∩",
        "therefore" to "∴", "because" to "∵", "ldots" to "…", "cdots" to "⋯", "prime" to "′",
        "%" to "%",
    )
}
