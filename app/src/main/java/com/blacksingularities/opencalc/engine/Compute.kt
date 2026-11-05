package com.blacksingularities.opencalc.engine

import java.math.BigDecimal
import kotlin.math.abs

/**
 * Wynik obliczenia z kalkulatora: wartość zmiennoprzecinkowa (do wykresów, pamięci, Ans),
 * opcjonalnie dokładna wartość dziesiętna i liczba zespolona, gdy wyrażenie zawiera i.
 */
data class Computed(val value: Double, val exact: BigDecimal?, val complex: Complex?) {
    val isComplex get() = complex != null

    fun shown(style: NumberStyle): String = when {
        complex != null -> formatComplex(complex, style)
        exact != null -> Fmt.number(exact, style)
        else -> Fmt.number(value, style)
    }

    /** Zapis do wstawienia z powrotem w wyrażenie (z kropką dziesiętną). */
    fun plain(): String = when {
        complex != null -> plainComplex(complex)
        exact != null -> Fmt.plain(exact)
        else -> Fmt.plain(value)
    }
}

object Calc {
    /**
     * Liczy wyrażenie tak dokładnie, jak się da: najpierw arytmetyka dziesiętna (0,1 + 0,2 = 0,3,
     * 30! co do cyfry), potem zmiennoprzecinkowa; z jednostką i – na liczbach zespolonych.
     */
    fun compute(text: String, ctx: EvalContext): Computed {
        val node = Parser.parse(text, ctx.vars.keys)
        if (node.hasImag()) {
            val z = node.cx(ctx).clean()
            if (z.re.isNaN() || z.im.isNaN()) throw CalcException("Wynik nieokreślony")
            // Część urojona znikoma względem rzeczywistej – wynik jest rzeczywisty.
            return if (z.im == 0.0 || abs(z.im) < 1e-14 * abs(z.re)) Computed(z.re, null, null) else Computed(z.re, null, z)
        }
        val exact = try {
            node.exact(ctx)
        } catch (e: CalcException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (exact != null) return Computed(exact.toDouble(), exact, null)
        return Computed(node.eval(ctx), null, null)
    }
}
