package com.blacksingularities.opencalc.engine

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sinh

/** Liczba zespolona re + im·i; działania potrzebne kalkulatorowi naukowemu. */
data class Complex(val re: Double, val im: Double = 0.0) {
    val isReal get() = im == 0.0
    val abs get() = hypot(re, im)
    val arg get() = atan2(im, re)

    operator fun plus(o: Complex) = Complex(re + o.re, im + o.im)
    operator fun minus(o: Complex) = Complex(re - o.re, im - o.im)
    operator fun unaryMinus() = Complex(-re, -im)
    operator fun times(o: Complex) = Complex(re * o.re - im * o.im, re * o.im + im * o.re)
    operator fun times(k: Double) = Complex(re * k, im * k)

    operator fun div(o: Complex): Complex {
        val d = o.re * o.re + o.im * o.im
        if (d == 0.0) throw CalcException("Dzielenie przez zero")
        return Complex((re * o.re + im * o.im) / d, (im * o.re - re * o.im) / d)
    }

    fun conj() = Complex(re, -im)

    fun format(style: NumberStyle): String = formatComplex(this, style)

    /** Usuwa śmieci zmiennoprzecinkowe: i² = −1, a nie −1 + 1,2·10⁻¹⁶i. */
    fun clean(): Complex {
        val scale = maxOf(1.0, abs(re), abs(im))
        fun c(v: Double) = if (abs(v) < 1e-13 * scale) 0.0 else v
        return Complex(c(re), c(im))
    }

    companion object {
        val I = Complex(0.0, 1.0)
        val ONE = Complex(1.0)

        fun exp(z: Complex): Complex {
            val m = exp(z.re)
            return Complex(m * cos(z.im), m * sin(z.im))
        }

        fun ln(z: Complex): Complex {
            if (z.re == 0.0 && z.im == 0.0) throw CalcException("Logarytm z zera")
            return Complex(ln(z.abs), z.arg)
        }

        fun sqrt(z: Complex): Complex {
            if (z.isReal && z.re >= 0) return Complex(kotlin.math.sqrt(z.re))
            if (z.isReal) return Complex(0.0, kotlin.math.sqrt(-z.re))
            val r = z.abs
            val a = kotlin.math.sqrt((r + z.re) / 2)
            val b = kotlin.math.sqrt((r - z.re) / 2)
            return Complex(a, if (z.im < 0) -b else b)
        }

        fun pow(x: Complex, y: Complex): Complex {
            if (y.isReal && y.re == Math.rint(y.re) && abs(y.re) <= 64) {
                // Potęga całkowita mnożeniem – dokładniejsza niż exp(y·ln x): i² = −1.
                var n = abs(y.re).toInt()
                var base = x
                var r = ONE
                while (n > 0) {
                    if (n and 1 == 1) r *= base
                    base *= base
                    n = n shr 1
                }
                return if (y.re < 0) ONE / r else r
            }
            if (x.re == 0.0 && x.im == 0.0) {
                if (y.re > 0) return Complex(0.0)
                throw CalcException("Dzielenie przez zero")
            }
            return exp(y * ln(x))
        }

        fun sin(z: Complex) = Complex(sin(z.re) * cosh(z.im), cos(z.re) * sinh(z.im))
        fun cos(z: Complex) = Complex(cos(z.re) * cosh(z.im), -sin(z.re) * sinh(z.im))
        fun tan(z: Complex) = sin(z) / cos(z)
        fun sinh(z: Complex) = Complex(sinh(z.re) * cos(z.im), cosh(z.re) * sin(z.im))
        fun cosh(z: Complex) = Complex(cosh(z.re) * cos(z.im), sinh(z.re) * sin(z.im))
        fun tanh(z: Complex) = sinh(z) / cosh(z)
    }
}

/** Zapis liczby zespolonej do wyświetlenia, np. „3 + 4i”, „−2i”. */
fun formatComplex(z: Complex, style: NumberStyle): String {
    val re = z.re
    val im = z.im
    if (im == 0.0) return Fmt.number(re, style)
    val imAbs = abs(im)
    val imText = if (imAbs == 1.0) "i" else Fmt.number(imAbs, style) + "i"
    if (re == 0.0) return (if (im < 0) "−" else "") + imText
    return Fmt.number(re, style) + (if (im < 0) " − " else " + ") + imText
}

/** Zapis do wstawienia w wyrażenie, np. „(3+4i)”. */
fun plainComplex(z: Complex): String {
    if (z.im == 0.0) return Fmt.plain(z.re)
    val im = if (abs(z.im) == 1.0) "i" else Fmt.plain(abs(z.im)) + "i"
    if (z.re == 0.0) return if (z.im < 0) "(−$im)" else im
    return "(" + Fmt.plain(z.re) + (if (z.im < 0) "−" else "+") + im + ")"
}
