package com.blacksingularities.opencalc

import com.blacksingularities.opencalc.engine.Analysis
import com.blacksingularities.opencalc.engine.AngleMode
import com.blacksingularities.opencalc.engine.CalcException
import com.blacksingularities.opencalc.engine.EvalContext
import com.blacksingularities.opencalc.engine.Finance
import com.blacksingularities.opencalc.engine.Fmt
import com.blacksingularities.opencalc.engine.Fraction
import com.blacksingularities.opencalc.engine.Matrix
import com.blacksingularities.opencalc.engine.NumberStyle
import com.blacksingularities.opencalc.engine.Roman
import com.blacksingularities.opencalc.engine.Stats
import com.blacksingularities.opencalc.engine.Units
import com.blacksingularities.opencalc.engine.convert
import com.blacksingularities.opencalc.engine.evaluate
import com.blacksingularities.opencalc.ui.PolishWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EngineTest {
    private val deg = EvalContext(AngleMode.DEG)
    private fun ev(s: String, c: EvalContext = EvalContext()) = evaluate(s, c)

    @Test fun arithmetic() {
        assertEquals(14.0, ev("2+3×4"), 1e-12)
        assertEquals(20.0, ev("(2+3)×4"), 1e-12)
        assertEquals(-4.0, ev("−2^2"), 1e-12)
        assertEquals(512.0, ev("2^3^2"), 1e-12)
        assertEquals(2.5, ev("5÷2"), 1e-12)
        assertEquals(1.0, ev("7 mod 3"), 1e-12)
        assertEquals(0.0015, ev("1.5E−3"), 1e-15)
    }

    @Test fun implicitAndPostfix() {
        assertEquals(2 * Math.PI, ev("2π"), 1e-12)
        assertEquals(15.0, ev("3(4+1)"), 1e-12)
        assertEquals(120.0, ev("5!"), 1e-12)
        assertEquals(9.0, ev("3²"), 1e-12)
        assertEquals(0.25, ev("4⁻¹"), 1e-12)
        assertEquals(6.0, ev("|−6|"), 1e-12)
        assertEquals(4.0, ev("√16"), 1e-12)
        assertEquals(5.0, ev("(2+3"), 1e-12)
    }

    @Test fun percent() {
        assertEquals(220.0, ev("200+10%"), 1e-9)
        assertEquals(180.0, ev("200−10%"), 1e-9)
        assertEquals(20.0, ev("200×10%"), 1e-9)
    }

    @Test fun trigonometry() {
        assertEquals(0.5, ev("sin(30)", deg), 1e-12)
        assertEquals(0.0, ev("sin(180)", deg), 0.0)
        assertEquals(1.0, ev("sin(π/2)"), 1e-12)
        assertEquals(30.0, ev("asin(0.5)", deg), 1e-9)
        assertEquals(0.5, ev("sin30", deg), 1e-12)
        assertEquals(0.5, ev("sin(30°)"), 1e-12)
    }

    @Test fun functionsAndVariables() {
        assertEquals(3.0, ev("log(1000)"), 1e-12)
        assertEquals(3.0, ev("log(8, 2)"), 1e-12)
        assertEquals(10.0, ev("nCr(5, 2)"), 1e-12)
        assertEquals(12.0, ev("gcd(36, 48)"), 1e-12)
        assertEquals(-2.0, ev("∛(−8)"), 1e-12)
        val c = EvalContext(vars = mapOf("x" to 3.0, "Ans" to 10.0))
        assertEquals(9.0 * Math.sin(3.0) / 2 + 9.0, ev("3xsin(x)/2 + x²", c), 1e-12)
        assertEquals(20.0, ev("2Ans", c), 1e-12)
    }

    @Test fun errors() {
        assertThrows(CalcException::class.java) { ev("1/0") }
        assertThrows(CalcException::class.java) { ev("2+") }
        assertThrows(CalcException::class.java) { ev("foo(2)") }
        assertThrows(CalcException::class.java) { ev("ln(−1)") }
    }

    @Test fun formatting() {
        val s = NumberStyle()
        assertEquals("1 234,5", Fmt.number(1234.5, s))
        assertEquals("0,3", Fmt.number(0.1 + 0.2, s))
        assertEquals("−2", Fmt.number(-2.0, s))
        assertEquals("1,5×10⁻¹²", Fmt.number(1.5e-12, s))
        assertEquals("3/8", Fraction.of(0.375).toString())
        assertEquals("1 3/4", Fraction.of(1.75)!!.mixed())
    }

    @Test fun analysis() {
        val roots = Analysis.solve("x^2 − 2", AngleMode.RAD, -5.0, 5.0)
        assertEquals(2, roots.size)
        assertEquals(Math.sqrt(2.0), roots[1], 1e-9)
        assertEquals(1.0 / 3, Analysis.integral({ it * it }, 0.0, 1.0), 1e-9)
        assertEquals(3.0, Analysis.derivative({ it * it * it }, 1.0), 1e-6)
        val q = Analysis.quadratic(1.0, -3.0, 2.0)
        assertEquals(listOf(1.0, 2.0), q.map { it.re })
        val c = Analysis.cubic(1.0, -6.0, 11.0, -6.0)
        assertEquals(listOf(1.0, 2.0, 3.0), c.map { Math.rint(it.re * 1e6) / 1e6 })
    }

    @Test fun tools() {
        val st = Stats.of(listOf(1.0, 2.0, 3.0, 4.0))
        assertEquals(2.5, st.mean, 1e-12)
        assertEquals(2.5, st.median, 1e-12)
        val m = Matrix(2, 2, arrayOf(doubleArrayOf(4.0, 7.0), doubleArrayOf(2.0, 6.0)))
        assertEquals(10.0, m.determinant(), 1e-12)
        assertEquals(0.6, m.inverse()[0, 0], 1e-12)
        assertEquals("MMXXVI", Roman.toRoman(2026))
        assertEquals(1994, Roman.fromRoman("MCMXCIV"))
        val loan = Finance.loan(100_000.0, 6.0, 120, decreasing = false)
        assertEquals(1110.21, loan.firstPayment, 0.01)
        val temp = Units.categories.first { it.id == "temperature" }
        assertEquals(212.0, convert(100.0, temp.units[0], temp.units[1]), 1e-9)
        assertEquals("dwa tysiące dwadzieścia sześć", PolishWords.say(2026))
        assertEquals("milion pięćset tysięcy", PolishWords.say(1_500_000))
    }
}
