package com.blacksingularities.opencalc.engine

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Ustawienia wyświetlania liczb. */
data class NumberStyle(
    val digits: Int = 12,
    val decimalComma: Boolean = true,
    val grouping: Boolean = true,
    val mode: NumMode = NumMode.AUTO,
)

enum class NumMode(val label: String) { AUTO("Auto"), FIXED("Stałoprzecinkowy"), SCI("Naukowy"), ENG("Inżynierski") }

object Fmt {
    private const val SUP = "⁰¹²³⁴⁵⁶⁷⁸⁹"

    fun superscript(n: Int): String {
        val s = n.toString()
        return buildString {
            for (ch in s) append(if (ch == '-') '⁻' else SUP[ch - '0'])
        }
    }

    /** Wynik do wyświetlenia: 12 cyfr znaczących, separator dziesiętny i grupowanie wg ustawień. */
    fun number(v: Double, style: NumberStyle = NumberStyle()): String {
        if (v.isNaN()) return "Nie liczba"
        if (v.isInfinite()) return if (v > 0) "∞" else "−∞"
        if (v == 0.0) return "0"
        val a = abs(v)
        val exp = floor(log10(a)).toInt()
        val mode = when {
            style.mode != NumMode.AUTO -> style.mode
            exp >= 15 || exp < -9 -> NumMode.SCI
            else -> NumMode.FIXED
        }
        return when (mode) {
            NumMode.SCI -> scientific(v, style, engineering = false)
            NumMode.ENG -> scientific(v, style, engineering = true)
            else -> fixed(v, style)
        }
    }

    private fun fixed(v: Double, style: NumberStyle): String {
        val bd = BigDecimal(v).round(MathContext(style.digits, RoundingMode.HALF_EVEN)).stripTrailingZeros()
        val plain = bd.toPlainString()
        return decorate(plain, style)
    }

    private fun scientific(v: Double, style: NumberStyle, engineering: Boolean): String {
        var bd = BigDecimal(v).round(MathContext(style.digits, RoundingMode.HALF_EVEN))
        var exp = bd.precision() - bd.scale() - 1
        if (engineering) exp = Math.floorDiv(exp, 3) * 3
        val mant = bd.movePointLeft(exp).stripTrailingZeros()
        bd = mant
        val m = decorate(bd.toPlainString(), style.copy(grouping = false))
        return if (exp == 0) m else "$m×10${superscript(exp)}"
    }

    private fun decorate(plain: String, style: NumberStyle): String {
        val neg = plain.startsWith("-")
        val body = plain.removePrefix("-")
        val intPart = body.substringBefore('.')
        val frac = body.substringAfter('.', "")
        val grouped = if (style.grouping && intPart.length > 3) {
            intPart.reversed().chunked(3).joinToString(" ").reversed()
        } else intPart
        val sep = if (style.decimalComma) "," else "."
        return (if (neg) "−" else "") + grouped + (if (frac.isNotEmpty()) sep + frac else "")
    }

    /** Liczba do wstawienia z powrotem do wyrażenia (zawsze z kropką, bez spacji). */
    fun plain(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return "0"
        if (v == Math.rint(v) && abs(v) < 1e15) return v.toLong().toString()
        val bd = BigDecimal(v).round(MathContext(15, RoundingMode.HALF_EVEN)).stripTrailingZeros()
        val a = abs(v)
        return if (a >= 1e15 || a < 1e-9) bd.toString().replace("E+", "E") else bd.toPlainString()
    }

    /** Krótka liczba do etykiet i narzędzi (np. 2 miejsca dla kwot). */
    fun short(v: Double, decimals: Int, style: NumberStyle = NumberStyle()): String {
        if (v.isNaN() || v.isInfinite()) return number(v, style)
        val bd = BigDecimal(v).setScale(decimals, RoundingMode.HALF_UP)
        return decorate(bd.toPlainString(), style)
    }

    fun money(v: Double, style: NumberStyle = NumberStyle()) = short(v, 2, style) + " zł"

    /** Wyrażenie wpisane w kalkulatorze w zapisie wybranym przez użytkownika (przecinek dziesiętny). */
    fun displayExpr(expr: String, decimalComma: Boolean): String =
        if (!decimalComma) expr else buildString(expr.length) {
            for (ch in expr) append(
                when (ch) {
                    '.' -> ','
                    ',' -> ';'
                    else -> ch
                },
            )
        }
}

/** Przybliżenie ułamkiem zwykłym (ułamki łańcuchowe), np. 0.375 → 3/8. */
data class Fraction(val num: Long, val den: Long) {
    override fun toString() = if (den == 1L) "$num" else "$num/$den"

    /** Liczba mieszana: 7/4 → 1 3/4. */
    fun mixed(): String {
        if (den == 1L || abs(num) < den) return toString()
        val whole = num / den
        val rest = abs(num % den)
        return if (rest == 0L) "$whole" else "$whole $rest/$den"
    }

    companion object {
        fun of(v: Double, maxDen: Long = 100_000, tolerance: Double = 1e-10): Fraction? {
            if (v.isNaN() || v.isInfinite() || abs(v) > 1e12) return null
            var h1 = 1L; var h0 = 0L
            var k1 = 0L; var k0 = 1L
            var x = v
            repeat(40) {
                val a = floor(x)
                val h2 = a.toLong() * h1 + h0
                val k2 = a.toLong() * k1 + k0
                if (k2 > maxDen) return null
                h0 = h1; h1 = h2
                k0 = k1; k1 = k2
                if (abs(v - h1.toDouble() / k1) <= tolerance * maxOf(1.0, abs(v))) {
                    return if (k1 < 0) Fraction(-h1, -k1) else Fraction(h1, k1)
                }
                val r = x - a
                if (r == 0.0) return null
                x = 1 / r
            }
            return null
        }
    }
}

/** Ładny krok siatki: 1, 2 lub 5 × 10ⁿ. */
fun niceStep(range: Double, targetLines: Int): Double {
    val raw = range / targetLines
    val mag = 10.0.pow(floor(log10(raw)))
    val n = raw / mag
    return when {
        n < 1.5 -> 1.0
        n < 3.5 -> 2.0
        n < 7.5 -> 5.0
        else -> 10.0
    } * mag
}
