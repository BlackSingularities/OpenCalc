package com.blacksingularities.opencalc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opencalc.engine.Analysis
import com.blacksingularities.opencalc.engine.CalcException
import com.blacksingularities.opencalc.engine.Fmt
import com.blacksingularities.opencalc.engine.Fraction
import com.blacksingularities.opencalc.engine.Func
import com.blacksingularities.opencalc.engine.Matrix
import com.blacksingularities.opencalc.engine.Ratio
import com.blacksingularities.opencalc.engine.Regression
import com.blacksingularities.opencalc.engine.Roman
import com.blacksingularities.opencalc.engine.Stats
import com.blacksingularities.opencalc.engine.divisors
import com.blacksingularities.opencalc.engine.gcd
import com.blacksingularities.opencalc.engine.isPrime
import com.blacksingularities.opencalc.engine.primeFactors
import com.blacksingularities.opencalc.engine.solveLinear
import com.blacksingularities.opencalc.ui.theme.Palette
import java.math.BigInteger

/** Liczba z pola: przecinek lub kropka, a także proste wyrażenia („1/3”, „2π”). */
private fun AppViewModel.num(text: String): Double? =
    parseNum(text) ?: if (text.isBlank()) null else runCatching { evalText(text.replace(',', '.')) }.getOrNull()

/** Wynik albo komunikat błędu z obliczeń narzędzia. */
private inline fun <T> attempt(block: () -> T): Pair<T?, String?> = try {
    block() to null
} catch (e: CalcException) {
    null to e.message
} catch (e: ArithmeticException) {
    null to "Za duże liczby"
} catch (e: Exception) {
    null to (e.message ?: "Błąd")
}

@Composable
fun EquationsTool(vm: AppViewModel) {
    var kind by rememberSaveable { mutableStateOf("Kwadratowe") }
    ChipRow(listOf("Liniowe", "Kwadratowe", "Sześcienne", "Układ 2×2", "Układ 3×3"), kind, { it }, { kind = it })
    when (kind) {
        "Liniowe" -> {
            var a by rememberSaveable { mutableStateOf("2") }
            var b by rememberSaveable { mutableStateOf("-4") }
            Section("a·x + b = 0") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField(a, { a = it }, "a", Modifier.weight(1f))
                    NumField(b, { b = it }, "b", Modifier.weight(1f))
                }
                val (r, err) = attempt { Analysis.quadratic(0.0, vm.num(a) ?: throw CalcException("Podaj a"), vm.num(b) ?: throw CalcException("Podaj b")) }
                r?.forEach { ResultRow("x", it.format(vm.settings.style), strong = true) }
                ErrorText(err)
            }
        }
        "Kwadratowe" -> {
            var a by rememberSaveable { mutableStateOf("1") }
            var b by rememberSaveable { mutableStateOf("-3") }
            var c by rememberSaveable { mutableStateOf("2") }
            Section("a·x² + b·x + c = 0") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField(a, { a = it }, "a", Modifier.weight(1f))
                    NumField(b, { b = it }, "b", Modifier.weight(1f))
                    NumField(c, { c = it }, "c", Modifier.weight(1f))
                }
                val av = vm.num(a)
                val bv = vm.num(b)
                val cv = vm.num(c)
                if (av != null && bv != null && cv != null) {
                    val (r, err) = attempt { Analysis.quadratic(av, bv, cv) }
                    r?.forEachIndexed { i, z -> ResultRow(if (r.size == 1) "x" else "x${i + 1}", z.format(vm.settings.style), strong = true) }
                    ErrorText(err)
                    if (av != 0.0) {
                        val delta = bv * bv - 4 * av * cv
                        ResultRow("Δ = b² − 4ac", vm.format(delta))
                        ResultRow("Wierzchołek (p; q)", "(${vm.format(-bv / (2 * av))}; ${vm.format(-delta / (4 * av))})")
                        Hint(if (delta > 0) "Dwa pierwiastki rzeczywiste" else if (delta == 0.0) "Jeden pierwiastek podwójny" else "Pierwiastki zespolone sprzężone")
                    }
                }
            }
        }
        "Sześcienne" -> {
            val coef = listOf("a", "b", "c", "d")
            val values = coef.map { name -> rememberSaveable(name) { mutableStateOf(if (name == "a") "1" else if (name == "d") "-6" else if (name == "b") "-6" else "11") } }
            Section("a·x³ + b·x² + c·x + d = 0") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    coef.forEachIndexed { i, n -> NumField(values[i].value, { values[i].value = it }, n, Modifier.weight(1f)) }
                }
                val v = values.map { vm.num(it.value) }
                if (v.all { it != null }) {
                    val (r, err) = attempt { Analysis.cubic(v[0]!!, v[1]!!, v[2]!!, v[3]!!) }
                    r?.forEachIndexed { i, z -> ResultRow("x${i + 1}", z.format(vm.settings.style), strong = true) }
                    ErrorText(err)
                }
            }
        }
        else -> {
            val n = if (kind == "Układ 2×2") 2 else 3
            LinearSystem(vm, n)
        }
    }
}

@Composable
private fun LinearSystem(vm: AppViewModel, n: Int) {
    val names = listOf("x", "y", "z")
    val cells = (0 until n).map { r ->
        (0..n).map { c -> rememberSaveable("ls$n-$r-$c") { mutableStateOf(if (c == r) "1" else if (c == n) "${r + 1}" else "0") } }
    }
    Section("Układ $n równań") {
        cells.forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                row.forEachIndexed { c, cell ->
                    NumField(cell.value, { cell.value = it }, if (c < n) names[c] else "=", Modifier.weight(1f))
                }
            }
        }
        val nums = cells.map { row -> row.map { vm.num(it.value) } }
        if (nums.all { row -> row.all { it != null } }) {
            val (res, err) = attempt {
                val a = Array(n) { r -> DoubleArray(n) { c -> nums[r][c]!! } }
                val b = DoubleArray(n) { r -> nums[r][n]!! }
                solveLinear(a, b)
            }
            res?.forEachIndexed { i, v -> ResultRow(names[i], vm.format(v) + (Fraction.of(v, 10_000)?.takeIf { it.den > 1 }?.let { "  ($it)" } ?: ""), strong = true) }
            ErrorText(err)
        }
    }
}

@Composable
fun SolverTool(vm: AppViewModel) {
    var expr by rememberSaveable { mutableStateOf("x^3 − 2x − 5") }
    var from by rememberSaveable { mutableStateOf("-10") }
    var to by rememberSaveable { mutableStateOf("10") }
    var at by rememberSaveable { mutableStateOf("1") }
    val angle = vm.settings.angle
    Section("Funkcja lub równanie") {
        TextArea(expr, { expr = it }, "f(x) lub lewa = prawa", minLines = 1, mono = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(from, { from = it }, "Od", Modifier.weight(1f))
            NumField(to, { to = it }, "Do", Modifier.weight(1f))
            NumField(at, { at = it }, "Punkt x₀", Modifier.weight(1f))
        }
        Hint("Kąty w trybie ${angle.name} (zmień na kalkulatorze). Równanie może mieć postać „cos(x) = x”.")
    }
    val a = vm.num(from)
    val b = vm.num(to)
    val x0 = vm.num(at)
    if (a == null || b == null || a >= b) {
        ErrorText("Podaj przedział: od < do")
        return
    }
    val isEquation = expr.contains('=')
    val (roots, err) = attempt { Analysis.solve(expr, angle, a, b) }
    Section(if (isEquation) "Rozwiązania" else "Miejsca zerowe") {
        ErrorText(err)
        if (roots != null) {
            if (roots.isEmpty()) Hint("Brak rozwiązań w przedziale [${vm.format(a)}; ${vm.format(b)}]")
            roots.take(30).forEachIndexed { i, x -> ResultRow("x${i + 1}", vm.format(x), strong = true) }
        }
    }
    if (!isEquation) {
        val (f, ferr) = attempt { Func.parse(expr, angle) }
        if (f != null) {
            Section("Analiza") {
                if (x0 != null) {
                    ResultRow("f(x₀)", vm.format(f(x0)))
                    ResultRow("f′(x₀)", vm.format(Analysis.snap(Analysis.derivative({ f(it) }, x0))))
                    ResultRow("f″(x₀)", vm.format(Analysis.snap(Analysis.secondDerivative({ f(it) }, x0))))
                }
                val integral = Analysis.integral({ f(it) }, a, b)
                ResultRow("∫ f(x) dx od ${vm.format(a)} do ${vm.format(b)}", vm.format(Analysis.snap(integral)), strong = true)
                Analysis.extrema({ f(it) }, a, b).take(10).forEach { k ->
                    ResultRow(k.kind.label, "(${vm.format(k.x)}; ${vm.format(Analysis.snap(k.y))})")
                }
            }
        }
        ErrorText(ferr)
    }
}

@Composable
fun StatsTool(vm: AppViewModel) {
    var text by rememberSaveable { mutableStateOf("4 8 15 16 23 42") }
    var ys by rememberSaveable { mutableStateOf("") }
    Section("Dane") {
        TextArea(text, { text = it }, "Liczby (spacja, enter lub średnik)", mono = true)
        TextArea(ys, { ys = it }, "Opcjonalnie Y – do regresji y = a·x + b", minLines = 1, mono = true)
    }
    val (st, err) = attempt { Stats.of(Stats.parseList(text)) }
    ErrorText(err)
    if (st != null) {
        val f = { v: Double -> vm.format(v) }
        Section("Wyniki") {
            ResultRow("Liczebność n", st.n.toString())
            ResultRow("Suma", f(st.sum))
            ResultRow("Średnia", f(st.mean), strong = true)
            ResultRow("Mediana", f(st.median), strong = true)
            ResultRow("Dominanta", if (st.modes.isEmpty()) "brak" else st.modes.take(5).joinToString("; ") { f(it) })
            ResultRow("Minimum", f(st.min))
            ResultRow("Maksimum", f(st.max))
            ResultRow("Rozstęp", f(st.range))
            ResultRow("Kwartyl Q1", f(st.q1))
            ResultRow("Kwartyl Q3", f(st.q3))
            ResultRow("Rozstęp międzykwartylowy", f(st.iqr))
            ResultRow("Wariancja (populacja)", f(st.variancePop))
            ResultRow("Wariancja (próba)", f(st.varianceSample))
            ResultRow("Odchylenie σ (populacja)", f(st.stdPop), strong = true)
            ResultRow("Odchylenie s (próba)", f(st.stdSample))
            ResultRow("Współczynnik zmienności", if (st.cv.isNaN()) "—" else Fmt.short(st.cv, 2, vm.settings.style) + "%")
            ResultRow("Średnia geometryczna", if (st.geometricMean.isNaN()) "tylko dla liczb > 0" else f(st.geometricMean))
            ResultRow("Średnia harmoniczna", if (st.harmonicMean.isNaN()) "tylko dla liczb > 0" else f(st.harmonicMean))
            ResultRow("Suma kwadratów", f(st.sumSquares))
        }
    }
    if (ys.isNotBlank()) {
        val (reg, rerr) = attempt { Regression.of(Stats.parseList(text), Stats.parseList(ys)) }
        Section("Regresja liniowa") {
            ErrorText(rerr)
            if (reg != null) {
                ResultRow("a (nachylenie)", vm.format(reg.a), strong = true)
                ResultRow("b (wyraz wolny)", vm.format(reg.b), strong = true)
                ResultRow("Korelacja r", vm.format(reg.r))
                ResultRow("Determinacja r²", vm.format(reg.r * reg.r))
                Hint("Dotknij „a” lub „b”, by skopiować. Wzór y = ${vm.format(reg.a)}·x + ${vm.format(reg.b)} możesz wkleić na wykresie.")
            }
        }
    }
}

@Composable
fun MatrixTool(vm: AppViewModel) {
    var size by rememberSaveable { mutableIntStateOf(3) }
    var op by rememberSaveable { mutableStateOf("det A") }
    ChipRow(listOf(2, 3, 4), size, { "$it×$it" }, { size = it })
    val a = matrixCells("A", size)
    val needsB = op in listOf("A + B", "A − B", "A × B")
    val b = matrixCells("B", size)
    ChipRow(listOf("det A", "A⁻¹", "Aᵀ", "rząd A", "ślad A", "A²", "A + B", "A − B", "A × B"), op, { it }, { op = it })
    MatrixInput("Macierz A", a)
    if (needsB) MatrixInput("Macierz B", b)
    val ma = toMatrix(vm, a, size)
    val mb = toMatrix(vm, b, size)
    Section("Wynik: $op") {
        if (ma == null || (needsB && mb == null)) {
            ErrorText("Uzupełnij wszystkie pola liczbami")
        } else {
            val (res, err) = attempt<Any> {
                when (op) {
                    "det A" -> ma.determinant()
                    "A⁻¹" -> ma.inverse()
                    "Aᵀ" -> ma.transpose()
                    "rząd A" -> ma.rank().toDouble()
                    "ślad A" -> ma.trace()
                    "A²" -> ma.power(2)
                    "A + B" -> ma + mb!!
                    "A − B" -> ma - mb!!
                    else -> ma * mb!!
                }
            }
            ErrorText(err)
            when (res) {
                is Double -> ResultRow(op, vm.format(res), strong = true)
                is Matrix -> MatrixView(vm, res)
            }
        }
    }
}

@Composable
private fun matrixCells(name: String, n: Int) = (0 until n).map { r ->
    (0 until n).map { c -> rememberSaveable("$name$n-$r-$c") { mutableStateOf(if (name == "A") (if (r == c) "2" else "1") else (if (r == c) "1" else "0")) } }
}

@Composable
private fun MatrixInput(title: String, cells: List<List<androidx.compose.runtime.MutableState<String>>>) {
    Section(title) {
        cells.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { cell -> NumField(cell.value, { cell.value = it }, "", Modifier.weight(1f)) }
            }
        }
    }
}

private fun toMatrix(vm: AppViewModel, cells: List<List<androidx.compose.runtime.MutableState<String>>>, n: Int): Matrix? {
    val data = Array(n) { DoubleArray(n) }
    for (r in 0 until n) for (c in 0 until n) data[r][c] = vm.num(cells[r][c].value) ?: return null
    return Matrix(n, n, data)
}

@Composable
private fun MatrixView(vm: AppViewModel, m: Matrix) {
    val style = vm.settings.style.copy(digits = 6, grouping = false)
    for (r in 0 until m.rows) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (c in 0 until m.cols) {
                val v = m[r, c]
                val frac = Fraction.of(v, 1000)?.takeIf { it.den > 1 }?.toString()
                Text(frac ?: Fmt.number(v, style), color = androidx.compose.ui.graphics.Color.White, fontFamily = FontFamily.Monospace, fontSize = 15.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun BasesTool(vm: AppViewModel) {
    var base by rememberSaveable { mutableIntStateOf(10) }
    var text by rememberSaveable { mutableStateOf("255") }
    var other by rememberSaveable { mutableStateOf("15") }
    val bases = listOf(2, 8, 10, 16)
    val names = mapOf(2 to "BIN", 8 to "OCT", 10 to "DEC", 16 to "HEX")
    ChipRow(bases, base, { names.getValue(it) }, { nb ->
        // Przy zmianie podstawy przepisujemy liczbę, żeby się nie zgubiła.
        runCatching { BigInteger(text.trim().replace(" ", ""), base) }.getOrNull()?.let { text = it.toString(nb).uppercase() }
        runCatching { BigInteger(other.trim().replace(" ", ""), base) }.getOrNull()?.let { other = it.toString(nb).uppercase() }
        base = nb
    })
    val kb = if (base == 16) androidx.compose.ui.text.input.KeyboardType.Ascii else androidx.compose.ui.text.input.KeyboardType.Number
    val value = runCatching { BigInteger(text.trim().replace(" ", "").ifEmpty { "0" }, base) }.getOrNull()
    Section("Liczba (${names[base]})") {
        NumField(text, { text = it.uppercase() }, "Wartość", Modifier.fillMaxWidth(), keyboard = kb)
        if (value == null) ErrorText("Niepoprawne cyfry dla podstawy $base")
    }
    if (value != null) {
        Section("Zapis") {
            ResultRow("DEC", value.toString(), strong = true)
            ResultRow("HEX", value.toString(16).uppercase())
            ResultRow("OCT", value.toString(8))
            ResultRow("BIN", groupBits(value.toString(2)))
            listOf(3, 5, 6, 7, 12, 20, 36).forEach { b -> ResultRow("Podstawa $b", value.toString(b).uppercase()) }
            ResultRow("Liczba bitów", value.bitLength().toString())
            ResultRow("Ustawione bity", value.bitCount().toString())
        }
        val o = runCatching { BigInteger(other.trim().replace(" ", "").ifEmpty { "0" }, base) }.getOrNull()
        Section("Operacje bitowe") {
            NumField(other, { other = it.uppercase() }, "Druga liczba (${names[base]})", Modifier.fillMaxWidth(), keyboard = kb)
            if (o != null) {
                val shift = o.toInt().coerceIn(0, 256)
                fun show(v: BigInteger) = v.toString(base).uppercase() + if (base != 10) "  (${v})" else ""
                ResultRow("A AND B", show(value.and(o)))
                ResultRow("A OR B", show(value.or(o)))
                ResultRow("A XOR B", show(value.xor(o)))
                ResultRow("NOT A (32 bity)", show(value.not().and(BigInteger.ONE.shiftLeft(32).subtract(BigInteger.ONE))))
                ResultRow("A << B", show(value.shiftLeft(shift)))
                ResultRow("A >> B", show(value.shiftRight(shift)))
                ResultRow("A + B", show(value.add(o)))
                ResultRow("A × B", show(value.multiply(o)))
            } else ErrorText("Niepoprawna druga liczba")
        }
    }
}

private fun groupBits(s: String) = s.reversed().chunked(4).joinToString(" ").reversed()

@Composable
fun FractionsTool(vm: AppViewModel) {
    var a by rememberSaveable { mutableStateOf("3/4") }
    var b by rememberSaveable { mutableStateOf("5/6") }
    var dec by rememberSaveable { mutableStateOf("0,625") }
    fun parse(s: String): Ratio {
        val t = s.trim().replace(" ", "")
        if (t.contains('/')) {
            val (n, d) = t.split('/', limit = 2)
            return Ratio(n.replace('−', '-').toLongOrNull() ?: throw CalcException("Zły licznik"), d.toLongOrNull() ?: throw CalcException("Zły mianownik")).reduce()
        }
        val v = parseNum(t) ?: throw CalcException("Wpisz ułamek, np. 3/4")
        val f = Fraction.of(v, 1_000_000) ?: throw CalcException("Nie da się zamienić na ułamek")
        return Ratio(f.num, f.den)
    }
    Section("Działania na ułamkach") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(a, { a = it }, "A", Modifier.weight(1f), keyboard = androidx.compose.ui.text.input.KeyboardType.Text)
            NumField(b, { b = it }, "B", Modifier.weight(1f), keyboard = androidx.compose.ui.text.input.KeyboardType.Text)
        }
        val (pair, err) = attempt { parse(a) to parse(b) }
        ErrorText(err)
        if (pair != null) {
            val (x, y) = pair
            listOf<Pair<String, () -> Ratio>>("A + B" to { x + y }, "A − B" to { x - y }, "A × B" to { x * y }, "A ÷ B" to { x / y }).forEach { (label, op) ->
                val (r, e) = attempt { op() }
                ResultRow(label, r?.let { "$it" + (if (it.d != 1L && kotlin.math.abs(it.n) > it.d) " = ${it.mixed()}" else "") + " ≈ ${vm.format(it.value())}" } ?: (e ?: ""))
            }
        }
    }
    Section("Dziesiętny → ułamek") {
        NumField(dec, { dec = it }, "Liczba dziesiętna", Modifier.fillMaxWidth())
        val v = vm.num(dec)
        val f = v?.let { Fraction.of(it, 1_000_000) }
        if (f != null) {
            ResultRow("Ułamek", f.toString(), strong = true)
            ResultRow("Liczba mieszana", f.mixed())
            ResultRow("Procent", vm.format(v * 100) + "%")
        } else if (v != null) Hint("Brak dokładnego ułamka o mianowniku do miliona")
    }
}

@Composable
fun NumberTheoryTool(vm: AppViewModel) {
    var text by rememberSaveable { mutableStateOf("360") }
    var second by rememberSaveable { mutableStateOf("84") }
    var roman by rememberSaveable { mutableStateOf("MMXXVI") }
    val n = text.trim().toLongOrNull()
    Section("Liczba całkowita") {
        NumField(text, { text = it }, "n", Modifier.fillMaxWidth(), keyboard = androidx.compose.ui.text.input.KeyboardType.Number)
        if (n == null) ErrorText("Wpisz liczbę całkowitą")
        else if (kotlin.math.abs(n) > 1_000_000_000_000L) ErrorText("Maksymalnie 10¹²")
        else {
            val factors = primeFactors(n)
            ResultRow("Liczba pierwsza", if (isPrime(n)) "tak" else "nie", strong = true)
            ResultRow("Rozkład", if (factors.isEmpty()) "—" else factors.joinToString(" · ") { (p, k) -> if (k == 1) "$p" else "$p${Fmt.superscript(k)}" })
            val divs = divisors(n)
            ResultRow("Liczba dzielników", divs.size.toString())
            ResultRow("Suma dzielników", divs.sum().toString())
            Text(divs.take(120).joinToString(", ") + if (divs.size > 120) " …" else "", color = Palette.TextDim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            ResultRow("Liczba doskonała", if (n > 1 && divs.sum() - n == n) "tak" else "nie")
            if (n in 1..3999) ResultRow("Rzymska", Roman.toRoman(n.toInt()))
        }
    }
    Section("NWD i NWW") {
        NumField(second, { second = it }, "Druga liczba", Modifier.fillMaxWidth(), keyboard = androidx.compose.ui.text.input.KeyboardType.Number)
        val m = second.trim().toLongOrNull()
        if (n != null && m != null) {
            ResultRow("NWD($n, $m)", gcd(n, m).toString(), strong = true)
            val (l, e) = attempt { Math.multiplyExact(kotlin.math.abs(n) / gcd(n, m).coerceAtLeast(1), kotlin.math.abs(m)) }
            ResultRow("NWW($n, $m)", if (n == 0L || m == 0L) "0" else l?.toString() ?: (e ?: ""))
        }
    }
    Section("Liczby rzymskie") {
        NumField(roman, { roman = it.uppercase() }, "Liczba rzymska lub arabska", Modifier.fillMaxWidth(), keyboard = androidx.compose.ui.text.input.KeyboardType.Ascii)
        val t = roman.trim()
        val (r, e) = attempt { t.toIntOrNull()?.let { Roman.toRoman(it) } ?: Roman.fromRoman(t).toString() }
        if (r != null) ResultRow("Wynik", r, strong = true)
        ErrorText(e)
    }
}
