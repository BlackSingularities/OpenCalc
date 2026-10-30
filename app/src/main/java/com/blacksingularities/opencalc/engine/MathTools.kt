package com.blacksingularities.opencalc.engine

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/** Statystyka opisowa listy liczb. */
data class Stats(
    val n: Int,
    val sum: Double,
    val mean: Double,
    val median: Double,
    val modes: List<Double>,
    val min: Double,
    val max: Double,
    val range: Double,
    val q1: Double,
    val q3: Double,
    val iqr: Double,
    val variancePop: Double,
    val varianceSample: Double,
    val stdPop: Double,
    val stdSample: Double,
    val geometricMean: Double,
    val harmonicMean: Double,
    val sumSquares: Double,
    val cv: Double,
) {
    companion object {
        /** Liczby oddzielone spacjami, średnikami, nowymi liniami lub przecinkami z odstępem. */
        fun parseList(text: String): List<Double> =
            text.replace(Regex(",(?=\\s)|;|\\n|\\t"), " ")
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .map { it.replace(',', '.').replace('−', '-').toDoubleOrNull() ?: throw CalcException("Niepoprawna liczba „$it”") }

        fun of(values: List<Double>): Stats {
            if (values.isEmpty()) throw CalcException("Wpisz co najmniej jedną liczbę")
            val s = values.sorted()
            val n = s.size
            val sum = s.sum()
            val mean = sum / n
            val ss = s.sumOf { (it - mean) * (it - mean) }
            val counts = s.groupingBy { it }.eachCount()
            val top = counts.values.max()
            val modes = if (top == 1) emptyList() else counts.filterValues { it == top }.keys.sorted()
            val q1 = quantile(s, 0.25)
            val q3 = quantile(s, 0.75)
            val positive = s.all { it > 0 }
            val stdPop = sqrt(ss / n)
            return Stats(
                n = n, sum = sum, mean = mean, median = quantile(s, 0.5), modes = modes,
                min = s.first(), max = s.last(), range = s.last() - s.first(), q1 = q1, q3 = q3, iqr = q3 - q1,
                variancePop = ss / n, varianceSample = if (n > 1) ss / (n - 1) else Double.NaN,
                stdPop = stdPop, stdSample = if (n > 1) sqrt(ss / (n - 1)) else Double.NaN,
                geometricMean = if (positive) kotlin.math.exp(s.sumOf { ln(it) } / n) else Double.NaN,
                harmonicMean = if (positive) n / s.sumOf { 1 / it } else Double.NaN,
                sumSquares = s.sumOf { it * it },
                cv = if (mean != 0.0) stdPop / abs(mean) * 100 else Double.NaN,
            )
        }

        /** Kwantyl metodą interpolacji liniowej (jak w arkuszach kalkulacyjnych). */
        fun quantile(sorted: List<Double>, p: Double): Double {
            if (sorted.size == 1) return sorted[0]
            val h = (sorted.size - 1) * p
            val lo = h.toInt()
            val hi = minOf(lo + 1, sorted.size - 1)
            return sorted[lo] + (h - lo) * (sorted[hi] - sorted[lo])
        }
    }
}

/** Regresja liniowa y = a·x + b z par (x, y). */
data class Regression(val a: Double, val b: Double, val r: Double) {
    companion object {
        fun of(xs: List<Double>, ys: List<Double>): Regression {
            if (xs.size != ys.size) throw CalcException("Listy X i Y muszą mieć tyle samo liczb")
            if (xs.size < 2) throw CalcException("Potrzeba co najmniej dwóch punktów")
            val n = xs.size
            val mx = xs.average()
            val my = ys.average()
            var sxy = 0.0
            var sxx = 0.0
            var syy = 0.0
            for (i in 0 until n) {
                sxy += (xs[i] - mx) * (ys[i] - my)
                sxx += (xs[i] - mx) * (xs[i] - mx)
                syy += (ys[i] - my) * (ys[i] - my)
            }
            if (sxx == 0.0) throw CalcException("Wszystkie X są równe")
            val a = sxy / sxx
            return Regression(a, my - a * mx, if (syy == 0.0) 1.0 else sxy / sqrt(sxx * syy))
        }
    }
}

/** Macierz kwadratowa lub prostokątna na liczbach zmiennoprzecinkowych. */
class Matrix(val rows: Int, val cols: Int, val data: Array<DoubleArray> = Array(rows) { DoubleArray(cols) }) {
    operator fun get(r: Int, c: Int) = data[r][c]

    fun transpose() = Matrix(cols, rows, Array(cols) { c -> DoubleArray(rows) { r -> data[r][c] } })

    operator fun plus(o: Matrix): Matrix {
        if (rows != o.rows || cols != o.cols) throw CalcException("Macierze muszą mieć ten sam wymiar")
        return Matrix(rows, cols, Array(rows) { r -> DoubleArray(cols) { c -> data[r][c] + o.data[r][c] } })
    }

    operator fun minus(o: Matrix): Matrix {
        if (rows != o.rows || cols != o.cols) throw CalcException("Macierze muszą mieć ten sam wymiar")
        return Matrix(rows, cols, Array(rows) { r -> DoubleArray(cols) { c -> data[r][c] - o.data[r][c] } })
    }

    operator fun times(o: Matrix): Matrix {
        if (cols != o.rows) throw CalcException("Liczba kolumn A musi równać się liczbie wierszy B")
        return Matrix(rows, o.cols, Array(rows) { r -> DoubleArray(o.cols) { c -> (0 until cols).sumOf { k -> data[r][k] * o.data[k][c] } } })
    }

    fun scale(k: Double) = Matrix(rows, cols, Array(rows) { r -> DoubleArray(cols) { c -> data[r][c] * k } })

    fun determinant(): Double {
        if (rows != cols) throw CalcException("Wyznacznik tylko dla macierzy kwadratowej")
        val m = Array(rows) { data[it].copyOf() }
        var det = 1.0
        for (i in 0 until rows) {
            var pivot = i
            for (r in i + 1 until rows) if (abs(m[r][i]) > abs(m[pivot][i])) pivot = r
            if (abs(m[pivot][i]) < 1e-14) return 0.0
            if (pivot != i) {
                val t = m[pivot]; m[pivot] = m[i]; m[i] = t
                det = -det
            }
            det *= m[i][i]
            for (r in i + 1 until rows) {
                val f = m[r][i] / m[i][i]
                for (c in i until cols) m[r][c] -= f * m[i][c]
            }
        }
        return Analysis.snap(det)
    }

    fun inverse(): Matrix {
        if (rows != cols) throw CalcException("Odwrotność tylko dla macierzy kwadratowej")
        val n = rows
        val a = Array(n) { r -> DoubleArray(2 * n) { c -> if (c < n) data[r][c] else if (c - n == r) 1.0 else 0.0 } }
        for (i in 0 until n) {
            var pivot = i
            for (r in i + 1 until n) if (abs(a[r][i]) > abs(a[pivot][i])) pivot = r
            if (abs(a[pivot][i]) < 1e-12) throw CalcException("Macierz osobliwa – brak odwrotności")
            val t = a[pivot]; a[pivot] = a[i]; a[i] = t
            val p = a[i][i]
            for (c in 0 until 2 * n) a[i][c] /= p
            for (r in 0 until n) if (r != i) {
                val f = a[r][i]
                for (c in 0 until 2 * n) a[r][c] -= f * a[i][c]
            }
        }
        return Matrix(n, n, Array(n) { r -> DoubleArray(n) { c -> Analysis.snap(a[r][c + n]) } })
    }

    fun rank(): Int {
        val m = Array(rows) { data[it].copyOf() }
        var rank = 0
        for (c in 0 until cols) {
            var pivot = -1
            for (r in rank until rows) if (abs(m[r][c]) > 1e-10 && (pivot < 0 || abs(m[r][c]) > abs(m[pivot][c]))) pivot = r
            if (pivot < 0) continue
            val t = m[pivot]; m[pivot] = m[rank]; m[rank] = t
            for (r in 0 until rows) if (r != rank) {
                val f = m[r][c] / m[rank][c]
                for (k in c until cols) m[r][k] -= f * m[rank][k]
            }
            rank++
            if (rank == rows) break
        }
        return rank
    }

    fun trace(): Double {
        if (rows != cols) throw CalcException("Ślad tylko dla macierzy kwadratowej")
        return (0 until rows).sumOf { data[it][it] }
    }

    fun power(k: Int): Matrix {
        if (rows != cols) throw CalcException("Potęga tylko dla macierzy kwadratowej")
        if (k < 0) return inverse().power(-k)
        var result = Matrix(rows, cols, Array(rows) { r -> DoubleArray(cols) { c -> if (r == c) 1.0 else 0.0 } })
        repeat(k) { result = result * this }
        return result
    }
}

/** Układ równań liniowych n×n metodą eliminacji Gaussa. */
fun solveLinear(a: Array<DoubleArray>, b: DoubleArray): DoubleArray {
    val n = b.size
    val m = Array(n) { r -> DoubleArray(n + 1) { c -> if (c < n) a[r][c] else b[r] } }
    for (i in 0 until n) {
        var pivot = i
        for (r in i + 1 until n) if (abs(m[r][i]) > abs(m[pivot][i])) pivot = r
        if (abs(m[pivot][i]) < 1e-12) throw CalcException("Układ sprzeczny lub nieoznaczony")
        val t = m[pivot]; m[pivot] = m[i]; m[i] = t
        for (r in 0 until n) if (r != i) {
            val f = m[r][i] / m[i][i]
            for (c in i..n) m[r][c] -= f * m[i][c]
        }
    }
    return DoubleArray(n) { Analysis.snap(m[it][n] / m[it][it]) }
}

/** Rozkład na czynniki pierwsze: 360 → [(2,3), (3,2), (5,1)]. */
fun primeFactors(n0: Long): List<Pair<Long, Int>> {
    var n = abs(n0)
    val out = mutableListOf<Pair<Long, Int>>()
    var p = 2L
    while (p * p <= n) {
        var k = 0
        while (n % p == 0L) {
            n /= p
            k++
        }
        if (k > 0) out += p to k
        p += if (p == 2L) 1 else 2
    }
    if (n > 1) out += n to 1
    return out
}

fun isPrime(n: Long): Boolean {
    if (n < 2) return false
    if (n < 4) return true
    if (n % 2 == 0L || n % 3 == 0L) return false
    var i = 5L
    while (i * i <= n) {
        if (n % i == 0L || n % (i + 2) == 0L) return false
        i += 6
    }
    return true
}

fun divisors(n0: Long): List<Long> {
    val n = abs(n0)
    if (n == 0L) return emptyList()
    val small = mutableListOf<Long>()
    val large = mutableListOf<Long>()
    var i = 1L
    while (i * i <= n) {
        if (n % i == 0L) {
            small += i
            if (i != n / i) large += n / i
        }
        i++
    }
    return small + large.reversed()
}

/** Zamiana liczby rzymskiej i arabskiej (1–3999). */
object Roman {
    private val table = listOf(
        1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC",
        50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I",
    )

    fun toRoman(n0: Int): String {
        if (n0 !in 1..3999) throw CalcException("Liczby rzymskie: od 1 do 3999")
        var n = n0
        return buildString {
            for ((v, s) in table) while (n >= v) {
                append(s)
                n -= v
            }
        }
    }

    fun fromRoman(text: String): Int {
        val s = text.trim().uppercase()
        if (s.isEmpty() || !s.all { it in "MDCLXVI" }) throw CalcException("Niepoprawna liczba rzymska")
        var i = 0
        var total = 0
        for ((v, sym) in table) while (s.startsWith(sym, i)) {
            total += v
            i += sym.length
        }
        if (i != s.length || toRoman(total) != s) throw CalcException("Niepoprawna liczba rzymska")
        return total
    }
}

/** Kredyt: raty równe (annuitetowe) albo malejące. */
data class LoanResult(val firstPayment: Double, val lastPayment: Double, val total: Double, val interest: Double, val schedule: List<LoanRow>)

data class LoanRow(val month: Int, val payment: Double, val capital: Double, val interest: Double, val balance: Double)

object Finance {
    fun loan(amount: Double, yearRate: Double, months: Int, decreasing: Boolean): LoanResult {
        if (amount <= 0 || months <= 0) throw CalcException("Podaj kwotę i liczbę rat")
        val r = yearRate / 100 / 12
        val rows = ArrayList<LoanRow>(months)
        var balance = amount
        val annuity = if (r == 0.0) amount / months else amount * r / (1 - (1 + r).pow(-months))
        val capitalPart = amount / months
        for (m in 1..months) {
            val interest = balance * r
            val capital = if (decreasing) capitalPart else annuity - interest
            val payment = capital + interest
            balance = maxOf(0.0, balance - capital)
            rows += LoanRow(m, payment, capital, interest, balance)
        }
        val total = rows.sumOf { it.payment }
        return LoanResult(rows.first().payment, rows.last().payment, total, total - amount, rows)
    }

    data class SavingsResult(val finalValue: Double, val deposits: Double, val grossInterest: Double, val tax: Double, val netInterest: Double)

    /**
     * Lokata / oszczędzanie z kapitalizacją: kapitał początkowy, opcjonalne miesięczne wpłaty,
     * podatek od zysków kapitałowych (w Polsce 19%) pobierany od odsetek.
     */
    fun savings(initial: Double, monthly: Double, yearRate: Double, years: Double, periodsPerYear: Int, taxPercent: Double): SavingsResult {
        val months = Math.round(years * 12).toInt()
        val monthsPerPeriod = 12 / periodsPerYear
        val periodRate = yearRate / 100 / periodsPerYear
        var balance = initial
        var pendingInterest = 0.0
        var deposits = initial
        var gross = 0.0
        var tax = 0.0
        for (m in 1..months) {
            // Odsetki narastają miesięcznie, dopisywane są na koniec okresu kapitalizacji.
            pendingInterest += balance * periodRate / monthsPerPeriod
            if (m % monthsPerPeriod == 0 || m == months) {
                val t = pendingInterest * taxPercent / 100
                gross += pendingInterest
                tax += t
                balance += pendingInterest - t
                pendingInterest = 0.0
            }
            if (m < months) {
                balance += monthly
                deposits += monthly
            }
        }
        return SavingsResult(balance, deposits, gross, tax, gross - tax)
    }
}

/** Ułamki zwykłe na liczbach całkowitych z automatycznym skracaniem. */
data class Ratio(val n: Long, val d: Long) {
    init {
        if (d == 0L) throw CalcException("Mianownik nie może być zerem")
    }

    fun reduce(): Ratio {
        val g = gcd(n, d).coerceAtLeast(1)
        val sign = if (d < 0) -1 else 1
        return Ratio(sign * n / g, sign * d / g)
    }

    operator fun plus(o: Ratio) = Ratio(Math.addExact(Math.multiplyExact(n, o.d), Math.multiplyExact(o.n, d)), Math.multiplyExact(d, o.d)).reduce()
    operator fun minus(o: Ratio) = Ratio(Math.subtractExact(Math.multiplyExact(n, o.d), Math.multiplyExact(o.n, d)), Math.multiplyExact(d, o.d)).reduce()
    operator fun times(o: Ratio) = Ratio(Math.multiplyExact(n, o.n), Math.multiplyExact(d, o.d)).reduce()
    operator fun div(o: Ratio): Ratio {
        if (o.n == 0L) throw CalcException("Dzielenie przez zero")
        return Ratio(Math.multiplyExact(n, o.d), Math.multiplyExact(d, o.n)).reduce()
    }

    fun value() = n.toDouble() / d

    override fun toString() = if (d == 1L) "$n" else "$n/$d"

    fun mixed(): String = Fraction(n, d).mixed()
}
