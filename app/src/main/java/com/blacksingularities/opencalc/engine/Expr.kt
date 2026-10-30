package com.blacksingularities.opencalc.engine

import kotlin.math.PI
import kotlin.math.E
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh
import kotlin.math.truncate
import kotlin.random.Random

class CalcException(message: String) : Exception(message)

enum class AngleMode { DEG, RAD, GRAD }

/** Kontekst obliczeń: tryb kątów i zmienne (x, Ans, M, A–F). */
class EvalContext(
    val angle: AngleMode = AngleMode.RAD,
    val vars: Map<String, Double> = emptyMap(),
) {
    fun toRad(v: Double) = when (angle) {
        AngleMode.RAD -> v
        AngleMode.DEG -> v * PI / 180.0
        AngleMode.GRAD -> v * PI / 200.0
    }

    fun fromRad(v: Double) = when (angle) {
        AngleMode.RAD -> v
        AngleMode.DEG -> v * 180.0 / PI
        AngleMode.GRAD -> v * 200.0 / PI
    }

    fun with(name: String, value: Double) = EvalContext(angle, vars + (name to value))
}

/** Węzeł drzewa wyrażenia – parsowanie raz, liczenie wiele razy (wykresy). */
sealed interface Node {
    fun eval(c: EvalContext): Double

    data class Num(val v: Double) : Node {
        override fun eval(c: EvalContext) = v
    }

    data class Var(val name: String) : Node {
        override fun eval(c: EvalContext) = c.vars[name] ?: throw CalcException("Nieznana zmienna $name")
    }

    data class Neg(val a: Node) : Node {
        override fun eval(c: EvalContext) = -a.eval(c)
    }

    data class Percent(val a: Node) : Node {
        override fun eval(c: EvalContext) = a.eval(c) / 100.0
    }

    data class Fact(val a: Node) : Node {
        override fun eval(c: EvalContext) = factorial(a.eval(c))
    }

    data class Bin(val op: Char, val a: Node, val b: Node) : Node {
        override fun eval(c: EvalContext): Double {
            val x = a.eval(c)
            // „200 + 10%” = 220: procent przy dodawaniu i odejmowaniu liczy się od lewej strony.
            if ((op == '+' || op == '-') && b is Percent) {
                val p = x * b.eval(c)
                return if (op == '+') x + p else x - p
            }
            val y = b.eval(c)
            return when (op) {
                '+' -> x + y
                '-' -> x - y
                '*' -> x * y
                '/' -> if (y == 0.0) throw CalcException("Dzielenie przez zero") else x / y
                '^' -> power(x, y)
                'm' -> if (y == 0.0) throw CalcException("Dzielenie przez zero") else x - y * floor(x / y)
                else -> throw CalcException("Nieznany operator")
            }
        }
    }

    data class Call(val fn: Fn, val args: List<Node>) : Node {
        override fun eval(c: EvalContext) = fn.impl(c, args.map { it.eval(c) })
    }
}

/** Definicja funkcji: nazwa, liczba argumentów (-1 = dowolna), opis do listy funkcji. */
class Fn(
    val name: String,
    val minArgs: Int,
    val maxArgs: Int,
    val usage: String,
    val description: String,
    val impl: (EvalContext, List<Double>) -> Double,
)

object Functions {
    private fun f1(name: String, usage: String, desc: String, g: (EvalContext, Double) -> Double) =
        Fn(name, 1, 1, usage, desc) { c, a -> g(c, a[0]) }

    private fun trig(name: String, desc: String, g: (Double) -> Double) =
        f1(name, "$name(x)", desc) { c, x -> cleanTrig(g(c.toRad(x))) }

    private fun arc(name: String, desc: String, g: (Double) -> Double) =
        f1(name, "$name(x)", desc) { c, x -> c.fromRad(g(x)).also { if (it.isNaN()) throw CalcException("Poza dziedziną $name") } }

    val all: List<Fn> = listOf(
        trig("sin", "Sinus") { sin(it) },
        trig("cos", "Cosinus") { cos(it) },
        f1("tan", "tan(x)", "Tangens") { c, x ->
            val r = c.toRad(x)
            if (abs(cos(r)) < 1e-15) throw CalcException("Tangens nieokreślony") else cleanTrig(tan(r))
        },
        f1("cot", "cot(x)", "Cotangens") { c, x ->
            val r = c.toRad(x)
            if (abs(sin(r)) < 1e-15) throw CalcException("Cotangens nieokreślony") else cleanTrig(1 / tan(r))
        },
        trig("sec", "Secans") { 1 / cos(it) },
        trig("csc", "Cosecans") { 1 / sin(it) },
        arc("asin", "Arcus sinus") { asin(it) },
        arc("acos", "Arcus cosinus") { acos(it) },
        arc("atan", "Arcus tangens") { atan(it) },
        arc("acot", "Arcus cotangens") { PI / 2 - atan(it) },
        Fn("atan2", 2, 2, "atan2(y, x)", "Kąt punktu (x, y)") { c, a -> c.fromRad(atan2(a[0], a[1])) },
        f1("sinh", "sinh(x)", "Sinus hiperboliczny") { _, x -> sinh(x) },
        f1("cosh", "cosh(x)", "Cosinus hiperboliczny") { _, x -> cosh(x) },
        f1("tanh", "tanh(x)", "Tangens hiperboliczny") { _, x -> tanh(x) },
        f1("asinh", "asinh(x)", "Area sinus hiperboliczny") { _, x -> ln(x + sqrt(x * x + 1)) },
        f1("acosh", "acosh(x)", "Area cosinus hiperboliczny") { _, x ->
            if (x < 1) throw CalcException("acosh wymaga x ≥ 1") else ln(x + sqrt(x * x - 1))
        },
        f1("atanh", "atanh(x)", "Area tangens hiperboliczny") { _, x ->
            if (abs(x) >= 1) throw CalcException("atanh wymaga |x| < 1") else 0.5 * ln((1 + x) / (1 - x))
        },
        f1("ln", "ln(x)", "Logarytm naturalny") { _, x -> if (x <= 0) throw CalcException("Logarytm z liczby ≤ 0") else ln(x) },
        Fn("log", 1, 2, "log(x) · log(x, podstawa)", "Logarytm dziesiętny lub o dowolnej podstawie") { _, a ->
            if (a[0] <= 0) throw CalcException("Logarytm z liczby ≤ 0")
            if (a.size == 1) log10(a[0]) else {
                if (a[1] <= 0 || a[1] == 1.0) throw CalcException("Zła podstawa logarytmu")
                ln(a[0]) / ln(a[1])
            }
        },
        f1("log2", "log2(x)", "Logarytm o podstawie 2") { _, x -> if (x <= 0) throw CalcException("Logarytm z liczby ≤ 0") else log2(x) },
        f1("exp", "exp(x)", "eˣ") { _, x -> exp(x) },
        f1("sqrt", "sqrt(x)", "Pierwiastek kwadratowy") { _, x -> if (x < 0) throw CalcException("Pierwiastek z liczby ujemnej") else sqrt(x) },
        f1("cbrt", "cbrt(x)", "Pierwiastek sześcienny") { _, x -> cbrt(x) },
        Fn("root", 2, 2, "root(x, n)", "Pierwiastek n-tego stopnia") { _, a -> nthRoot(a[0], a[1]) },
        f1("abs", "abs(x)", "Wartość bezwzględna") { _, x -> abs(x) },
        f1("floor", "floor(x)", "Zaokrąglenie w dół") { _, x -> floor(x) },
        f1("ceil", "ceil(x)", "Zaokrąglenie w górę") { _, x -> ceil(x) },
        Fn("round", 1, 2, "round(x) · round(x, miejsca)", "Zaokrąglenie") { _, a ->
            if (a.size == 1) round(a[0]) else {
                val m = 10.0.pow(a[1].toInt())
                round(a[0] * m) / m
            }
        },
        f1("trunc", "trunc(x)", "Część całkowita") { _, x -> truncate(x) },
        f1("frac", "frac(x)", "Część ułamkowa") { _, x -> x - truncate(x) },
        f1("sign", "sign(x)", "Znak liczby") { _, x -> sign(x) },
        f1("deg", "deg(x)", "Radiany → stopnie") { _, x -> x * 180 / PI },
        f1("rad", "rad(x)", "Stopnie → radiany") { _, x -> x * PI / 180 },
        f1("gamma", "gamma(x)", "Funkcja gamma Γ(x)") { _, x -> gamma(x) },
        Fn("mod", 2, 2, "mod(a, b)", "Reszta z dzielenia") { _, a ->
            if (a[1] == 0.0) throw CalcException("Dzielenie przez zero") else a[0] - a[1] * floor(a[0] / a[1])
        },
        Fn("nCr", 2, 2, "nCr(n, k)", "Kombinacje (symbol Newtona)") { _, a -> combinations(a[0], a[1]) },
        Fn("nPr", 2, 2, "nPr(n, k)", "Wariacje bez powtórzeń") { _, a -> permutations(a[0], a[1]) },
        Fn("gcd", 2, -1, "gcd(a, b, …)", "Największy wspólny dzielnik") { _, a -> a.map { integer(it) }.reduce { x, y -> gcd(x, y) }.toDouble() },
        Fn("lcm", 2, -1, "lcm(a, b, …)", "Najmniejsza wspólna wielokrotność") { _, a -> a.map { integer(it) }.reduce { x, y -> lcm(x, y) }.toDouble() },
        Fn("min", 1, -1, "min(a, b, …)", "Najmniejsza wartość") { _, a -> a.min() },
        Fn("max", 1, -1, "max(a, b, …)", "Największa wartość") { _, a -> a.max() },
        Fn("sum", 1, -1, "sum(a, b, …)", "Suma") { _, a -> a.sum() },
        Fn("avg", 1, -1, "avg(a, b, …)", "Średnia arytmetyczna") { _, a -> a.average() },
        Fn("hypot", 2, 2, "hypot(a, b)", "Przeciwprostokątna √(a²+b²)") { _, a -> kotlin.math.hypot(a[0], a[1]) },
        Fn("rand", 0, 0, "rand()", "Losowa liczba z [0, 1)") { _, _ -> Random.nextDouble() },
        Fn("randint", 2, 2, "randint(a, b)", "Losowa liczba całkowita z [a, b]") { _, a ->
            val lo = minOf(a[0], a[1]).toLong()
            val hi = maxOf(a[0], a[1]).toLong()
            Random.nextLong(lo, hi + 1).toDouble()
        },
    )

    private val byName = all.associateBy { it.name.lowercase() }

    fun find(name: String) = byName[name.lowercase()]
}

object Constants {
    val values = linkedMapOf(
        "π" to PI,
        "pi" to PI,
        "e" to E,
        "φ" to (1 + sqrt(5.0)) / 2,
        "phi" to (1 + sqrt(5.0)) / 2,
        "τ" to 2 * PI,
        "tau" to 2 * PI,
        "∞" to Double.POSITIVE_INFINITY,
    )
}

/** Usuwa śmieci zmiennoprzecinkowe: sin(180°) = 0, a nie 1.2e-16. */
private fun cleanTrig(v: Double) = if (abs(v) < 1e-14) 0.0 else if (abs(v - Math.rint(v)) < 1e-14) Math.rint(v) else v

private fun power(x: Double, y: Double): Double {
    if (x < 0 && y != Math.rint(y)) {
        // Nieparzysty pierwiastek z liczby ujemnej, np. (-8)^(1/3) = -2.
        val inv = 1 / y
        if (abs(inv - Math.rint(inv)) < 1e-9 && Math.rint(inv).toLong() % 2 != 0L) return -(-x).pow(y)
        throw CalcException("Potęga ujemnej liczby o niecałkowitym wykładniku")
    }
    if (x == 0.0 && y < 0) throw CalcException("Dzielenie przez zero")
    return x.pow(y)
}

fun nthRoot(x: Double, n: Double): Double {
    if (n == 0.0) throw CalcException("Pierwiastek stopnia 0")
    if (x < 0) {
        if (n == Math.rint(n) && n.toLong() % 2 != 0L) return -(-x).pow(1 / n)
        throw CalcException("Parzysty pierwiastek z liczby ujemnej")
    }
    return x.pow(1 / n)
}

private fun integer(v: Double): Long {
    if (v != Math.rint(v)) throw CalcException("Wymagana liczba całkowita")
    return v.toLong()
}

fun gcd(a: Long, b: Long): Long {
    var x = abs(a)
    var y = abs(b)
    while (y != 0L) {
        val t = x % y
        x = y
        y = t
    }
    return x
}

fun lcm(a: Long, b: Long): Long = if (a == 0L || b == 0L) 0 else abs(a / gcd(a, b) * b)

fun factorial(v: Double): Double {
    if (v < 0 && v == Math.rint(v)) throw CalcException("Silnia liczby ujemnej")
    if (v == Math.rint(v)) {
        if (v > 170) return Double.POSITIVE_INFINITY
        var r = 1.0
        for (i in 2..v.toInt()) r *= i
        return r
    }
    return gamma(v + 1)
}

/** Przybliżenie Lanczosa (g = 7) z odbiciem dla x < 0.5. */
fun gamma(x: Double): Double {
    if (x == Math.rint(x) && x <= 0) throw CalcException("Γ nieokreślona dla liczb całkowitych ≤ 0")
    if (x < 0.5) return PI / (sin(PI * x) * gamma(1 - x))
    val g = 7.0
    val c = doubleArrayOf(
        0.99999999999980993, 676.5203681218851, -1259.1392167224028, 771.32342877765313,
        -176.61502916214059, 12.507343278686905, -0.13857109526572012, 9.9843695780195716e-6,
        1.5056327351493116e-7,
    )
    val xx = x - 1
    var a = c[0]
    val t = xx + g + 0.5
    for (i in 1 until 9) a += c[i] / (xx + i)
    return sqrt(2 * PI) * t.pow(xx + 0.5) * exp(-t) * a
}

fun combinations(n: Double, k: Double): Double {
    val nn = integer(n)
    val kk = integer(k)
    if (nn < 0 || kk < 0) throw CalcException("nCr wymaga liczb ≥ 0")
    if (kk > nn) return 0.0
    val m = minOf(kk, nn - kk)
    var r = 1.0
    for (i in 1..m) r = r * (nn - m + i) / i
    return Math.rint(r)
}

fun permutations(n: Double, k: Double): Double {
    val nn = integer(n)
    val kk = integer(k)
    if (nn < 0 || kk < 0) throw CalcException("nPr wymaga liczb ≥ 0")
    if (kk > nn) return 0.0
    var r = 1.0
    for (i in 0 until kk) r *= (nn - i)
    return r
}

/**
 * Parser wyrażeń metodą zejść rekurencyjnych. Obsługuje: + − × ÷ ^ mod, nawiasy, |x|,
 * mnożenie domyślne (2π, 3(4+1), 2x, xsin(x)), procenty, silnię, potęgi ², ³, ⁻¹,
 * √ i ∛ jako przedrostki, notację 1.5E-3 oraz przecinek lub średnik jako separator argumentów.
 */
class Parser private constructor(private val src: String, private val variables: Set<String>) {
    private var pos = 0

    companion object {
        fun parse(text: String, variables: Set<String> = emptySet()): Node {
            val normalized = normalize(text)
            if (normalized.isBlank()) throw CalcException("Puste wyrażenie")
            val p = Parser(normalized, variables)
            val node = p.parseExpr()
            p.skipSpaces()
            if (p.pos < p.src.length) {
                val ch = p.src[p.pos]
                throw CalcException(if (ch == ')') "Nadmiarowy nawias" else "Nieoczekiwany znak „$ch”")
            }
            return node
        }

        /** Ujednolica zapis z klawiatury aplikacji i z wklejonego tekstu. */
        fun normalize(text: String): String {
            val sb = StringBuilder()
            for (ch in text) {
                when (ch) {
                    '×', '·', '∙', '⋅' -> sb.append('*')
                    '÷', ':' -> sb.append('/')
                    '−', '–', '—' -> sb.append('-')
                    ';' -> sb.append(',')
                    ' ', ' ', ' ' -> {}
                    else -> sb.append(ch)
                }
            }
            // Brakujące nawiasy zamykające dopisujemy na końcu, jak w kalkulatorach naukowych.
            var depth = 0
            for (ch in sb) if (ch == '(') depth++ else if (ch == ')') depth--
            repeat(maxOf(0, depth)) { sb.append(')') }
            return sb.toString()
        }

        private const val SUPERSCRIPTS = "⁰¹²³⁴⁵⁶⁷⁸⁹"
    }

    private fun skipSpaces() {
        while (pos < src.length && src[pos].isWhitespace()) pos++
    }

    private fun peek(): Char? {
        skipSpaces()
        return if (pos < src.length) src[pos] else null
    }

    private fun eat(ch: Char): Boolean {
        if (peek() == ch) {
            pos++
            return true
        }
        return false
    }

    private fun parseExpr(): Node {
        var left = parseTerm()
        while (true) {
            left = when {
                eat('+') -> Node.Bin('+', left, parseTerm())
                eat('-') -> Node.Bin('-', left, parseTerm())
                else -> return left
            }
        }
    }

    private fun startsOperand(): Boolean {
        val ch = peek() ?: return false
        return ch.isDigit() || ch == '.' || ch == '(' || ch == '√' || ch == '∛' || ch.isLetter() || ch in "πφτ∞"
    }

    private fun parseTerm(): Node {
        var left = parseUnary()
        while (true) {
            left = when {
                eat('*') -> Node.Bin('*', left, parseUnary())
                eat('/') -> Node.Bin('/', left, parseUnary())
                matchWord("mod") -> Node.Bin('m', left, parseUnary())
                startsOperand() -> Node.Bin('*', left, parsePower())
                else -> return left
            }
        }
    }

    private fun matchWord(word: String): Boolean {
        skipSpaces()
        if (src.startsWith(word, pos, ignoreCase = true)) {
            val after = pos + word.length
            if (after >= src.length || !src[after].isLetter()) {
                pos = after
                return true
            }
        }
        return false
    }

    private fun parseUnary(): Node = when {
        eat('-') -> Node.Neg(parseUnary())
        eat('+') -> parseUnary()
        else -> parsePower()
    }

    private fun parsePower(): Node {
        val base = parsePostfix()
        if (eat('^')) {
            // Prawostronnie łączne: 2^3^2 = 2^9; wykładnik może mieć znak.
            return Node.Bin('^', base, parseUnary())
        }
        return base
    }

    private fun parsePostfix(): Node {
        var node = parsePrimary()
        while (true) {
            skipSpaces()
            if (pos >= src.length) return node
            val ch = src[pos]
            node = when {
                ch == '!' -> { pos++; Node.Fact(node) }
                ch == '%' -> { pos++; Node.Percent(node) }
                ch == '⁻' && pos + 1 < src.length && src[pos + 1] == '¹' -> { pos += 2; Node.Bin('/', Node.Num(1.0), node) }
                ch in SUPERSCRIPTS -> {
                    var exp = 0
                    while (pos < src.length && src[pos] in SUPERSCRIPTS) {
                        exp = exp * 10 + SUPERSCRIPTS.indexOf(src[pos])
                        pos++
                    }
                    Node.Bin('^', node, Node.Num(exp.toDouble()))
                }
                ch == '°' -> { pos++; DegLiteral(Node.Bin('*', node, Node.Num(PI / 180))) }
                else -> return node
            }
        }
    }

    private fun parsePrimary(): Node {
        val ch = peek() ?: throw CalcException("Niekompletne wyrażenie")
        when {
            ch.isDigit() || ch == '.' -> return Node.Num(parseNumber())
            ch == '(' -> {
                pos++
                if (peek() == ')') throw CalcException("Puste nawiasy")
                val inner = parseExpr()
                if (!eat(')')) throw CalcException("Brak nawiasu zamykającego")
                return inner
            }
            ch == '|' -> {
                pos++
                val inner = parseExpr()
                if (!eat('|')) throw CalcException("Brak zamykającego |")
                return Node.Call(Functions.find("abs")!!, listOf(inner))
            }
            ch == '√' -> {
                pos++
                return Node.Call(Functions.find("sqrt")!!, listOf(parsePowerOperand()))
            }
            ch == '∛' -> {
                pos++
                return Node.Call(Functions.find("cbrt")!!, listOf(parsePowerOperand()))
            }
            ch == ')' -> throw CalcException("Nieoczekiwany nawias")
            ch.isLetter() || ch in "πφτ∞" -> return parseIdentifier()
            else -> throw CalcException("Nieoczekiwany znak „$ch”")
        }
    }

    /** Argument √ bez nawiasu: √2π = √(2)·π, √(x) – zwykły nawias, √-4 – błąd dziedziny. */
    private fun parsePowerOperand(): Node {
        if (eat('-')) return Node.Neg(parsePowerOperand())
        val base = parsePostfix()
        return if (eat('^')) Node.Bin('^', base, parseUnary()) else base
    }

    private fun parseNumber(): Double {
        val start = pos
        while (pos < src.length && (src[pos].isDigit() || src[pos] == '.')) pos++
        val mantissa = src.substring(start, pos)
        if (mantissa.count { it == '.' } > 1) throw CalcException("Liczba z dwoma przecinkami")
        if (mantissa == ".") throw CalcException("Niepełna liczba")
        var text = mantissa
        // Notacja naukowa: 1.5E3, 2E-4 (wielka litera E, żeby nie mylić ze stałą e).
        if (pos < src.length && src[pos] == 'E') {
            var p = pos + 1
            if (p < src.length && (src[p] == '+' || src[p] == '-')) p++
            if (p < src.length && src[p].isDigit()) {
                while (p < src.length && src[p].isDigit()) p++
                text += src.substring(pos, p)
                pos = p
            }
        }
        return text.toDoubleOrNull() ?: throw CalcException("Niepoprawna liczba")
    }

    private fun parseIdentifier(): Node {
        skipSpaces()
        val start = pos
        while (pos < src.length && (src[pos].isLetterOrDigit() || src[pos] in "πφτ∞_")) {
            // Cyfra kończy nazwę, chyba że należy do nazwy funkcji (log2, atan2).
            if (src[pos].isDigit() && !isNamePrefix(src.substring(start, pos + 1))) break
            pos++
        }
        val word = src.substring(start, pos)
        // Najdłuższa znana nazwa od początku słowa; reszta wraca do strumienia (xsin → x·sin).
        for (len in word.length downTo 1) {
            pos = start
            return resolve(word.substring(0, len)) ?: continue
        }
        pos = start
        throw CalcException("Nieznana nazwa „$word”")
    }

    private fun isNamePrefix(s: String) = Functions.all.any { it.name.startsWith(s, ignoreCase = true) }

    /** Rozpoznaje nazwę zaczynającą się w `pos`; przy sukcesie przesuwa `pos` za nią (i za argumenty). */
    private fun resolve(name: String): Node? {
        val start = pos
        val fn = Functions.find(name)
        if (fn != null && peek(afterName = name.length) == '(') {
            pos += name.length
            eat('(')
            val args = mutableListOf<Node>()
            if (peek() != ')') {
                do args += parseExpr() while (eat(','))
            }
            if (!eat(')')) throw CalcException("Brak nawiasu w ${fn.name}(")
            if (args.size < fn.minArgs || (fn.maxArgs >= 0 && args.size > fn.maxArgs)) {
                throw CalcException("${fn.name}: zła liczba argumentów")
            }
            return Node.Call(fn, args)
        }
        if (fn != null && fn.maxArgs == 1) {
            // sin30, ln2, sinx: argument bez nawiasu obejmuje najbliższy czynnik.
            pos += name.length
            if (startsOperand() || peek() == '-') return Node.Call(fn, listOf(parsePowerOperand()))
            pos = start
        }
        val constant = Constants.values[name] ?: if (name != "E") Constants.values[name.lowercase()] else null
        if (constant != null) {
            pos += name.length
            return Node.Num(constant)
        }
        if (name in variables) {
            pos += name.length
            return Node.Var(name)
        }
        return null
    }

    private fun peek(afterName: Int): Char? {
        var p = pos + afterName
        while (p < src.length && src[p].isWhitespace()) p++
        return if (p < src.length) src[p] else null
    }

    /** Liczba z symbolem stopnia: 30° zawsze w stopniach, niezależnie od trybu kątów. */
    private class DegLiteral(val scaled: Node) : Node {
        override fun eval(c: EvalContext): Double = c.fromRad(scaled.eval(c))
    }
}

/** Wygodne liczenie tekstu w jednym kroku. */
fun evaluate(text: String, ctx: EvalContext = EvalContext()): Double =
    Parser.parse(text, ctx.vars.keys).eval(ctx)
