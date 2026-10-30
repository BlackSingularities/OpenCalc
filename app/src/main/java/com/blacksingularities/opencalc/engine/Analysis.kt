package com.blacksingularities.opencalc.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** Funkcja jednej zmiennej zbudowana z wyrażenia; błędy dziedziny dają NaN. */
class Func(val node: Node, private val angle: AngleMode, private val vars: Map<String, Double> = emptyMap()) {
    private val base = EvalContext(angle, vars)

    operator fun invoke(x: Double): Double = try {
        node.eval(base.with("x", x))
    } catch (_: CalcException) {
        Double.NaN
    } catch (_: ArithmeticException) {
        Double.NaN
    }

    companion object {
        fun parse(text: String, angle: AngleMode = AngleMode.RAD, vars: Map<String, Double> = emptyMap()): Func =
            Func(Parser.parse(text, vars.keys + "x"), angle, vars)
    }
}

enum class PointKind(val label: String) { ROOT("Miejsce zerowe"), MIN("Minimum"), MAX("Maksimum"), CROSS("Przecięcie"), Y_AXIS("Przecięcie z osią Y") }

data class KeyPoint(val x: Double, val y: Double, val kind: PointKind, val owner: Int = 0, val other: Int = -1)

object Analysis {
    fun derivative(f: (Double) -> Double, x: Double): Double {
        val h = 1e-5 * max(1.0, abs(x))
        // Pięciopunktowy iloraz różnicowy – błąd rzędu h⁴.
        return (-f(x + 2 * h) + 8 * f(x + h) - 8 * f(x - h) + f(x - 2 * h)) / (12 * h)
    }

    fun secondDerivative(f: (Double) -> Double, x: Double): Double {
        val h = 1e-4 * max(1.0, abs(x))
        return (f(x + h) - 2 * f(x) + f(x - h)) / (h * h)
    }

    /** Całka oznaczona adaptacyjną metodą Simpsona. */
    fun integral(f: (Double) -> Double, a: Double, b: Double, eps: Double = 1e-10): Double {
        if (a == b) return 0.0
        if (a > b) return -integral(f, b, a, eps)
        val n = 64
        val h = (b - a) / n
        var total = 0.0
        for (i in 0 until n) {
            val lo = a + i * h
            val hi = lo + h
            val fa = f(lo)
            val fb = f(hi)
            val fm = f((lo + hi) / 2)
            total += simpson(f, lo, hi, fa, fm, fb, simpsonRule(lo, hi, fa, fm, fb), eps / n, 18)
        }
        return total
    }

    private fun simpsonRule(a: Double, b: Double, fa: Double, fm: Double, fb: Double) = (b - a) / 6 * (fa + 4 * fm + fb)

    private fun simpson(f: (Double) -> Double, a: Double, b: Double, fa: Double, fm: Double, fb: Double, whole: Double, eps: Double, depth: Int): Double {
        val m = (a + b) / 2
        val lm = (a + m) / 2
        val rm = (m + b) / 2
        val flm = f(lm)
        val frm = f(rm)
        val left = simpsonRule(a, m, fa, flm, fm)
        val right = simpsonRule(m, b, fm, frm, fb)
        val diff = left + right - whole
        if (depth <= 0 || abs(diff) <= 15 * eps || diff.isNaN()) return left + right + diff / 15
        return simpson(f, a, m, fa, flm, fm, left, eps / 2, depth - 1) + simpson(f, m, b, fm, frm, fb, right, eps / 2, depth - 1)
    }

    /** Miejsce zerowe w przedziale ze zmianą znaku (bisekcja zakończona siecznymi). */
    fun bisect(f: (Double) -> Double, a0: Double, b0: Double): Double? {
        var a = a0
        var b = b0
        var fa = f(a)
        val fb = f(b)
        if (fa.isNaN() || fb.isNaN()) return null
        if (fa == 0.0) return a
        if (fb == 0.0) return b
        if ((fa > 0) == (fb > 0)) return null
        repeat(200) {
            val m = (a + b) / 2
            val fm = f(m)
            if (fm.isNaN()) return null
            if (fm == 0.0 || (b - a) / 2 < 1e-14 * max(1.0, abs(m))) return m
            if ((fm > 0) == (fa > 0)) {
                a = m
                fa = fm
            } else {
                b = m
            }
        }
        return (a + b) / 2
    }

    /** Wszystkie miejsca zerowe w [a, b]: skanowanie z gęstą siatką i doprecyzowanie. */
    fun roots(f: (Double) -> Double, a: Double, b: Double, samples: Int = 2000): List<Double> {
        val out = mutableListOf<Double>()
        val step = (b - a) / samples
        var px = a
        var py = f(px)
        for (i in 1..samples) {
            val x = a + i * step
            val y = f(x)
            if (!py.isNaN() && !y.isNaN()) {
                if (py == 0.0) {
                    out.add(px)
                } else if ((py > 0) != (y > 0) && y != 0.0) {
                    val r = bisect(f, px, x)
                    // Odrzucamy bieguny (tan x przy π/2) – wartość przy „zerze” musi być mała.
                    if (r != null && abs(f(r)) < 1e-6 * max(1.0, abs(py) + abs(y))) out.add(r)
                }
            }
            px = x
            py = y
        }
        if (!py.isNaN() && py == 0.0) out.add(px)
        // Pierwiastki podwójne (x² w zerze) – minimum |f| bliskie zeru bez zmiany znaku.
        for (e in extremaRaw(f, a, b, samples)) {
            val v = f(e)
            if (abs(v) < 1e-9 && out.none { abs(it - e) < step * 2 }) out.add(e)
        }
        return dedupe(out.sorted(), step)
    }

    private fun extremaRaw(f: (Double) -> Double, a: Double, b: Double, samples: Int): List<Double> {
        val d = { x: Double -> derivative(f, x) }
        val out = mutableListOf<Double>()
        val step = (b - a) / samples
        var px = a
        var py = d(px)
        for (i in 1..samples) {
            val x = a + i * step
            val y = d(x)
            if (!py.isNaN() && !y.isNaN() && (py > 0) != (y > 0)) {
                bisect(d, px, x)?.let { out.add(it) }
            }
            px = x
            py = y
        }
        return out
    }

    fun extrema(f: (Double) -> Double, a: Double, b: Double, samples: Int = 1500): List<KeyPoint> {
        val step = (b - a) / samples
        return dedupe(extremaRaw(f, a, b, samples), step).mapNotNull { x ->
            val y = f(x)
            if (y.isNaN() || y.isInfinite()) return@mapNotNull null
            val l = f(x - step / 2)
            val r = f(x + step / 2)
            when {
                l > y && r > y -> KeyPoint(x, y, PointKind.MIN)
                l < y && r < y -> KeyPoint(x, y, PointKind.MAX)
                else -> null
            }
        }
    }

    private fun dedupe(xs: List<Double>, step: Double): List<Double> {
        val out = mutableListOf<Double>()
        for (x in xs) if (out.isEmpty() || abs(out.last() - x) > step / 2) out.add(snap(x))
        return out
    }

    /** 1.9999999999997 → 2: wynik numeryczny bliski „ładnej” liczbie. */
    fun snap(x: Double): Double {
        val r = Math.rint(x * 1e9) / 1e9
        return if (abs(r - x) < 1e-10 * max(1.0, abs(x))) r else x
    }

    /**
     * Rozwiązanie równania „lewa = prawa” (lub „wyrażenie = 0”) względem x
     * w przedziale [a, b]. Zwraca listę rozwiązań.
     */
    fun solve(equation: String, angle: AngleMode, a: Double, b: Double): List<Double> {
        val parts = equation.split('=')
        if (parts.size > 2) throw CalcException("Za dużo znaków =")
        val lhs = Func.parse(parts[0], angle)
        val rhs = if (parts.size == 2) Func.parse(parts[1].ifBlank { "0" }, angle) else null
        val f: (Double) -> Double = if (rhs == null) { x -> lhs(x) } else { x -> lhs(x) - rhs(x) }
        return roots(f, a, b, 4000)
    }

    /** Pierwiastki równania kwadratowego (także zespolone). */
    fun quadratic(a: Double, b: Double, c: Double): List<Complex> {
        if (a == 0.0) {
            if (b == 0.0) throw CalcException(if (c == 0.0) "Nieskończenie wiele rozwiązań" else "Brak rozwiązań")
            return listOf(Complex(-c / b, 0.0))
        }
        val d = b * b - 4 * a * c
        return if (d >= 0) {
            val s = sqrt(d)
            // Stabilna numerycznie postać (bez odejmowania bliskich liczb).
            val q = -0.5 * (b + if (b >= 0) s else -s)
            val x1 = if (q != 0.0) c / q else 0.0
            val x2 = q / a
            listOf(Complex(snap(minOf(x1, x2)), 0.0), Complex(snap(maxOf(x1, x2)), 0.0)).distinct()
        } else {
            val re = -b / (2 * a)
            val im = sqrt(-d) / (2 * abs(a))
            listOf(Complex(snap(re), -snap(im)), Complex(snap(re), snap(im)))
        }
    }

    /** Pierwiastki wielomianu trzeciego stopnia metodą Cardana (rzeczywiste i zespolone). */
    fun cubic(a: Double, b: Double, c: Double, d: Double): List<Complex> {
        if (a == 0.0) return quadratic(b, c, d)
        val p = (3 * a * c - b * b) / (3 * a * a)
        val q = (2 * b * b * b - 9 * a * b * c + 27 * a * a * d) / (27 * a * a * a)
        val shift = -b / (3 * a)
        val disc = q * q / 4 + p * p * p / 27
        val roots = mutableListOf<Complex>()
        if (abs(disc) < 1e-14) {
            val u = Math.cbrt(-q / 2)
            roots += Complex(snap(2 * u + shift), 0.0)
            roots += Complex(snap(-u + shift), 0.0)
        } else if (disc > 0) {
            val sd = sqrt(disc)
            val u = Math.cbrt(-q / 2 + sd)
            val v = Math.cbrt(-q / 2 - sd)
            roots += Complex(snap(u + v + shift), 0.0)
            val re = -(u + v) / 2 + shift
            val im = (u - v) * sqrt(3.0) / 2
            roots += Complex(snap(re), -snap(abs(im)))
            roots += Complex(snap(re), snap(abs(im)))
        } else {
            val r = sqrt(-p * p * p / 27)
            val phi = kotlin.math.acos((-q / 2 / r).coerceIn(-1.0, 1.0))
            val m = 2 * Math.cbrt(r)
            for (k in 0..2) roots += Complex(snap(m * kotlin.math.cos((phi + 2 * Math.PI * k) / 3) + shift), 0.0)
            roots.sortBy { it.re }
        }
        return roots.distinct()
    }
}

data class Complex(val re: Double, val im: Double) {
    fun format(style: NumberStyle): String = when {
        im == 0.0 -> Fmt.number(re, style)
        re == 0.0 -> Fmt.number(im, style) + "i"
        else -> Fmt.number(re, style) + (if (im < 0) " − " else " + ") + Fmt.number(abs(im), style) + "i"
    }
}
