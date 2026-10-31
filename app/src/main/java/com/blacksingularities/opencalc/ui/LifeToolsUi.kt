package com.blacksingularities.opencalc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opencalc.engine.Finance
import com.blacksingularities.opencalc.engine.Fmt
import com.blacksingularities.opencalc.ui.theme.Palette
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

private fun AppViewModel.money(v: Double) = Fmt.money(v, settings.style)
private fun AppViewModel.pct(v: Double) = Fmt.short(v, 2, settings.style) + "%"
private fun AppViewModel.n(text: String): Double? =
    parseNum(text) ?: if (text.isBlank()) null else runCatching { evalText(text.replace(',', '.')) }.getOrNull()

@Composable
private fun SwitchLine(text: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(value, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Color.White, checkedThumbColor = Color.Black))
    }
}

@Composable
fun PercentTool(vm: AppViewModel) {
    var a by rememberSaveable { mutableStateOf("15") }
    var b by rememberSaveable { mutableStateOf("240") }
    Section("Dane") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(a, { a = it }, "A", Modifier.weight(1f))
            NumField(b, { b = it }, "B", Modifier.weight(1f))
        }
    }
    val x = vm.n(a)
    val y = vm.n(b)
    if (x == null || y == null) return
    Section("Wyniki") {
        ResultRow("A% z B", vm.format(x / 100 * y), strong = true)
        ResultRow("A to ile % B", if (y == 0.0) "—" else vm.pct(x / y * 100))
        ResultRow("Zmiana z A na B", if (x == 0.0) "—" else (if (y >= x) "+" else "") + vm.pct((y - x) / abs(x) * 100))
        ResultRow("B powiększone o A%", vm.format(y * (1 + x / 100)))
        ResultRow("B pomniejszone o A% (rabat)", vm.format(y * (1 - x / 100)))
        ResultRow("B to wartość po rabacie A% – cena pierwotna", if (x == 100.0) "—" else vm.format(y / (1 - x / 100)))
        ResultRow("Narzut A% na koszt B – cena", vm.format(y * (1 + x / 100)))
        ResultRow("Marża A% – cena przy koszcie B", if (x >= 100.0) "—" else vm.format(y / (1 - x / 100)))
        ResultRow("Punkty procentowe B − A", Fmt.short(y - x, 2, vm.settings.style) + " p.p.")
    }
}

@Composable
fun VatTool(vm: AppViewModel) {
    var amount by rememberSaveable { mutableStateOf("1000") }
    var rate by rememberSaveable { mutableStateOf("23") }
    var isNet by rememberSaveable { mutableStateOf(true) }
    ChipRow(listOf("23", "8", "5", "0"), rate, { "$it%" }, { rate = it })
    Section("Kwota") {
        NumField(amount, { amount = it }, if (isNet) "Netto" else "Brutto", Modifier.fillMaxWidth(), suffix = "zł")
        NumField(rate, { rate = it }, "Stawka VAT", Modifier.fillMaxWidth(), suffix = "%")
        SwitchLine("Podaję kwotę netto", isNet) { isNet = it }
    }
    val v = vm.n(amount) ?: return
    val r = (vm.n(rate) ?: return) / 100
    val net = if (isNet) v else v / (1 + r)
    val gross = if (isNet) v * (1 + r) else v
    Section("Wynik") {
        ResultRow("Netto", vm.money(net), strong = !isNet)
        ResultRow("VAT", vm.money(gross - net))
        ResultRow("Brutto", vm.money(gross), strong = isNet)
    }
}

@Composable
fun LoanTool(vm: AppViewModel) {
    var amount by rememberSaveable { mutableStateOf("300000") }
    var rate by rememberSaveable { mutableStateOf("7,5") }
    var years by rememberSaveable { mutableStateOf("25") }
    var decreasing by rememberSaveable { mutableStateOf(false) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    Section("Kredyt") {
        NumField(amount, { amount = it }, "Kwota kredytu", Modifier.fillMaxWidth(), suffix = "zł")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(rate, { rate = it }, "Oprocentowanie roczne", Modifier.weight(1f), suffix = "%")
            NumField(years, { years = it }, "Okres", Modifier.weight(1f), suffix = "lat")
        }
        SwitchLine("Raty malejące", decreasing) { decreasing = it }
    }
    val a = vm.n(amount) ?: return
    val r = vm.n(rate) ?: return
    val y = vm.n(years) ?: return
    val months = Math.round(y * 12).toInt()
    if (a <= 0 || months !in 1..600) {
        ErrorText("Podaj kwotę i okres do 50 lat")
        return
    }
    val res = Finance.loan(a, r, months, decreasing)
    Section("Podsumowanie") {
        if (decreasing) {
            ResultRow("Pierwsza rata", vm.money(res.firstPayment), strong = true)
            ResultRow("Ostatnia rata", vm.money(res.lastPayment))
        } else {
            ResultRow("Rata miesięczna", vm.money(res.firstPayment), strong = true)
        }
        ResultRow("Liczba rat", months.toString())
        ResultRow("Suma odsetek", vm.money(res.interest))
        ResultRow("Całkowity koszt", vm.money(res.total))
        ResultRow("Koszt na 1 zł kredytu", Fmt.short(res.total / a, 3, vm.settings.style) + " zł")
    }
    Section("Harmonogram") {
        Row(Modifier.fillMaxWidth()) {
            listOf("Nr", "Rata", "Kapitał", "Odsetki", "Saldo").forEach { Text(it, color = Palette.TextFaint, fontSize = 11.sp, modifier = Modifier.weight(1f)) }
        }
        val rows = if (showAll) res.schedule else res.schedule.filter { it.month <= 12 || it.month % 12 == 0 || it.month == months }
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                listOf(
                    row.month.toString(),
                    Fmt.short(row.payment, 2, vm.settings.style),
                    Fmt.short(row.capital, 2, vm.settings.style),
                    Fmt.short(row.interest, 2, vm.settings.style),
                    Fmt.short(row.balance, 0, vm.settings.style),
                ).forEach { Text(it, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f)) }
            }
        }
        PillButton(if (showAll) "Pokaż skrót" else "Pokaż wszystkie raty", primary = false) { showAll = !showAll }
    }
}

@Composable
fun SavingsTool(vm: AppViewModel) {
    var initial by rememberSaveable { mutableStateOf("10000") }
    var monthly by rememberSaveable { mutableStateOf("500") }
    var rate by rememberSaveable { mutableStateOf("5") }
    var years by rememberSaveable { mutableStateOf("10") }
    var periods by rememberSaveable { mutableIntStateOf(12) }
    var belka by rememberSaveable { mutableStateOf(true) }
    Section("Oszczędzanie") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(initial, { initial = it }, "Kapitał początkowy", Modifier.weight(1f), suffix = "zł")
            NumField(monthly, { monthly = it }, "Wpłata co miesiąc", Modifier.weight(1f), suffix = "zł")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(rate, { rate = it }, "Oprocentowanie", Modifier.weight(1f), suffix = "%")
            NumField(years, { years = it }, "Czas", Modifier.weight(1f), suffix = "lat")
        }
        Text("Kapitalizacja", color = Palette.TextDim, fontSize = 13.sp)
        ChipRow(listOf(12, 4, 2, 1), periods, { mapOf(12 to "miesięczna", 4 to "kwartalna", 2 to "półroczna", 1 to "roczna").getValue(it) }, { periods = it })
        SwitchLine("Podatek Belki 19%", belka) { belka = it }
    }
    val p = vm.n(initial) ?: return
    val m = vm.n(monthly) ?: 0.0
    val r = vm.n(rate) ?: return
    val y = vm.n(years) ?: return
    if (y <= 0 || y > 100) {
        ErrorText("Czas od 1 miesiąca do 100 lat")
        return
    }
    val res = Finance.savings(p, m, r, y, periods, if (belka) 19.0 else 0.0)
    Section("Wynik") {
        ResultRow("Wartość końcowa", vm.money(res.finalValue), strong = true)
        ResultRow("Suma wpłat", vm.money(res.deposits))
        ResultRow("Odsetki brutto", vm.money(res.grossInterest))
        if (belka) ResultRow("Podatek", vm.money(res.tax))
        ResultRow("Zysk netto", vm.money(res.netInterest))
        if (res.deposits > 0) ResultRow("Zwrot", vm.pct(res.netInterest / res.deposits * 100))
        val eff = ((1 + r / 100 / periods).pow(periods) - 1) * 100
        ResultRow("Oprocentowanie efektywne", vm.pct(eff))
        if (r > 0) ResultRow("Podwojenie kapitału (reguła 72)", Fmt.short(72 / r, 1, vm.settings.style) + " lat")
    }
}

private val dateFmt = DateTimeFormatter.ofPattern("d.MM.yyyy")
private fun parseDate(t: String): LocalDate? = runCatching { LocalDate.parse(t.trim(), dateFmt) }.getOrNull()
    ?: runCatching { LocalDate.parse(t.trim()) }.getOrNull()

private fun dayName(d: LocalDate) = d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("pl"))

@Composable
fun DatesTool(vm: AppViewModel) {
    val today = LocalDate.now().format(dateFmt)
    var from by rememberSaveable { mutableStateOf(today) }
    var to by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(100).format(dateFmt)) }
    var add by rememberSaveable { mutableStateOf("30") }
    Section("Różnica między datami") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(from, { from = it }, "Od (dd.mm.rrrr)", Modifier.weight(1f), keyboard = androidx.compose.ui.text.input.KeyboardType.Text)
            NumField(to, { to = it }, "Do", Modifier.weight(1f), keyboard = androidx.compose.ui.text.input.KeyboardType.Text)
        }
        val a = parseDate(from)
        val b = parseDate(to)
        if (a == null || b == null) ErrorText("Wpisz daty w formacie 31.12.2026")
        else {
            val days = ChronoUnit.DAYS.between(a, b)
            val p = Period.between(minOf(a, b), maxOf(a, b))
            ResultRow("Dni", days.toString(), strong = true)
            ResultRow("Tygodnie", "${days / 7} tyg. ${abs(days % 7)} dni")
            ResultRow("Lata, miesiące, dni", "${p.years} l. ${p.months} mies. ${p.days} dni")
            ResultRow("Godziny", (days * 24).toString())
            var work = 0
            var d = minOf(a, b)
            val end = maxOf(a, b)
            while (d < end && work < 100_000) {
                if (d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY) work++
                d = d.plusDays(1)
            }
            ResultRow("Dni robocze (pn–pt)", work.toString())
            ResultRow("Dzień tygodnia „od”", dayName(a))
            ResultRow("Dzień tygodnia „do”", dayName(b))
        }
    }
    Section("Dodaj lub odejmij dni") {
        NumField(add, { add = it }, "Liczba dni (ujemna odejmuje)", Modifier.fillMaxWidth(), keyboard = androidx.compose.ui.text.input.KeyboardType.Text)
        val a = parseDate(from)
        val k = add.trim().replace('−', '-').toLongOrNull()
        if (a != null && k != null) {
            val r = a.plusDays(k)
            ResultRow("Data", r.format(dateFmt) + " (${dayName(r)})", strong = true)
            ResultRow("Dzień roku", r.dayOfYear.toString())
            ResultRow("Rok przestępny", if (r.isLeapYear) "tak" else "nie")
        }
    }
}

@Composable
fun FuelTool(vm: AppViewModel) {
    var dist by rememberSaveable { mutableStateOf("350") }
    var cons by rememberSaveable { mutableStateOf("6,5") }
    var price by rememberSaveable { mutableStateOf("6,20") }
    var people by rememberSaveable { mutableStateOf("1") }
    var round by rememberSaveable { mutableStateOf(false) }
    Section("Trasa") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(dist, { dist = it }, "Dystans", Modifier.weight(1f), suffix = "km")
            NumField(cons, { cons = it }, "Spalanie", Modifier.weight(1f), suffix = "l/100")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(price, { price = it }, "Cena paliwa", Modifier.weight(1f), suffix = "zł/l")
            NumField(people, { people = it }, "Osób", Modifier.weight(1f))
        }
        SwitchLine("W obie strony", round) { round = it }
    }
    val d = (vm.n(dist) ?: return) * if (round) 2 else 1
    val c = vm.n(cons) ?: return
    val p = vm.n(price) ?: return
    val n = (vm.n(people) ?: 1.0).coerceAtLeast(1.0)
    val litres = d * c / 100
    Section("Koszt") {
        ResultRow("Paliwo", Fmt.short(litres, 2, vm.settings.style) + " l")
        ResultRow("Koszt całkowity", vm.money(litres * p), strong = true)
        ResultRow("Na osobę", vm.money(litres * p / n), strong = n > 1)
        ResultRow("Koszt 1 km", vm.money(c / 100 * p))
    }
}

@Composable
fun TipTool(vm: AppViewModel) {
    var bill by rememberSaveable { mutableStateOf("240") }
    var tip by rememberSaveable { mutableStateOf("10") }
    var people by rememberSaveable { mutableStateOf("3") }
    ChipRow(listOf("0", "5", "10", "15", "20"), tip, { "$it%" }, { tip = it })
    Section("Rachunek") {
        NumField(bill, { bill = it }, "Kwota", Modifier.fillMaxWidth(), suffix = "zł")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(tip, { tip = it }, "Napiwek", Modifier.weight(1f), suffix = "%")
            NumField(people, { people = it }, "Osób", Modifier.weight(1f))
        }
    }
    val b = vm.n(bill) ?: return
    val t = vm.n(tip) ?: return
    val n = (vm.n(people) ?: 1.0).coerceAtLeast(1.0)
    val total = b * (1 + t / 100)
    Section("Podział") {
        ResultRow("Napiwek", vm.money(total - b))
        ResultRow("Razem", vm.money(total), strong = true)
        ResultRow("Na osobę", vm.money(total / n), strong = true)
        ResultRow("Na osobę (zaokrąglone w górę)", vm.money(kotlin.math.ceil(total / n)))
    }
}

@Composable
fun HealthTool(vm: AppViewModel) {
    var weight by rememberSaveable { mutableStateOf("75") }
    var height by rememberSaveable { mutableStateOf("180") }
    var age by rememberSaveable { mutableStateOf("30") }
    var male by rememberSaveable { mutableStateOf(true) }
    var activity by rememberSaveable { mutableStateOf("1.55") }
    Section("Dane") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(weight, { weight = it }, "Waga", Modifier.weight(1f), suffix = "kg")
            NumField(height, { height = it }, "Wzrost", Modifier.weight(1f), suffix = "cm")
            NumField(age, { age = it }, "Wiek", Modifier.weight(1f), suffix = "lat")
        }
        SwitchLine("Mężczyzna", male) { male = it }
        Text("Aktywność", color = Palette.TextDim, fontSize = 13.sp)
        ChipRow(
            listOf("1.2", "1.375", "1.55", "1.725", "1.9"), activity,
            { mapOf("1.2" to "siedząca", "1.375" to "lekka", "1.55" to "umiarkowana", "1.725" to "duża", "1.9" to "bardzo duża").getValue(it) },
            { activity = it },
        )
    }
    val w = vm.n(weight) ?: return
    val h = (vm.n(height) ?: return) / 100
    val a = vm.n(age) ?: return
    if (h <= 0) return
    val bmi = w / (h * h)
    val cat = when {
        bmi < 16 -> "wygłodzenie"
        bmi < 17 -> "wychudzenie"
        bmi < 18.5 -> "niedowaga"
        bmi < 25 -> "waga prawidłowa"
        bmi < 30 -> "nadwaga"
        bmi < 35 -> "otyłość I stopnia"
        bmi < 40 -> "otyłość II stopnia"
        else -> "otyłość III stopnia"
    }
    // Mifflin-St Jeor.
    val bmr = 10 * w + 6.25 * h * 100 - 5 * a + if (male) 5 else -161
    val tdee = bmr * activity.toDouble()
    Section("Wynik") {
        ResultRow("BMI", Fmt.short(bmi, 1, vm.settings.style), strong = true)
        ResultRow("Kategoria", cat)
        ResultRow("Prawidłowa waga (BMI 18,5–25)", "${Fmt.short(18.5 * h * h, 1, vm.settings.style)}–${Fmt.short(25 * h * h, 1, vm.settings.style)} kg")
        ResultRow("Podstawowa przemiana materii", Fmt.short(bmr, 0, vm.settings.style) + " kcal")
        ResultRow("Dzienne zapotrzebowanie", Fmt.short(tdee, 0, vm.settings.style) + " kcal", strong = true)
        Hint("Wartości orientacyjne (wzór Mifflina-St Jeora), nie zastępują porady lekarza ani dietetyka.")
    }
}

@Composable
fun WordsTool(vm: AppViewModel) {
    var text by rememberSaveable { mutableStateOf("1234,56") }
    Section("Liczba") {
        NumField(text, { text = it }, "Kwota", Modifier.fillMaxWidth())
    }
    val v = parseNum(text) ?: return
    if (abs(v) >= 1e15) {
        ErrorText("Maksymalnie 999 bilionów")
        return
    }
    val whole = abs(v).toLong()
    val grosze = Math.round((abs(v) - whole) * 100).toInt().coerceIn(0, 99)
    val sign = if (v < 0) "minus " else ""
    val words = sign + PolishWords.say(whole)
    Section("Słownie") {
        // Kwota zawsze w złotych, a grosze zawsze również słownie – bez przełączników.
        ResultRow("", "$words ${PolishWords.plural(whole, "złoty", "złote", "złotych")} ${"%02d".format(grosze)}/100", strong = true)
        ResultRow("Grosze słownie", PolishWords.say(grosze.toLong()) + " " + PolishWords.plural(grosze.toLong(), "grosz", "grosze", "groszy"))
    }
}

/** Liczebniki główne po polsku z poprawną odmianą rzędów wielkości. */
object PolishWords {
    private val ones = listOf("", "jeden", "dwa", "trzy", "cztery", "pięć", "sześć", "siedem", "osiem", "dziewięć")
    private val teens = listOf("dziesięć", "jedenaście", "dwanaście", "trzynaście", "czternaście", "piętnaście", "szesnaście", "siedemnaście", "osiemnaście", "dziewiętnaście")
    private val tens = listOf("", "", "dwadzieścia", "trzydzieści", "czterdzieści", "pięćdziesiąt", "sześćdziesiąt", "siedemdziesiąt", "osiemdziesiąt", "dziewięćdziesiąt")
    private val hundreds = listOf("", "sto", "dwieście", "trzysta", "czterysta", "pięćset", "sześćset", "siedemset", "osiemset", "dziewięćset")
    private val groups = listOf(
        Triple("", "", ""),
        Triple("tysiąc", "tysiące", "tysięcy"),
        Triple("milion", "miliony", "milionów"),
        Triple("miliard", "miliardy", "miliardów"),
        Triple("bilion", "biliony", "bilionów"),
    )

    fun plural(n: Long, one: String, few: String, many: String): String {
        if (n == 1L) return one
        val d = n % 10
        val dd = n % 100
        return if (d in 2..4 && dd !in 12..14) few else many
    }

    private fun triple(n: Int): String {
        val h = n / 100
        val t = n % 100 / 10
        val o = n % 10
        val parts = mutableListOf(hundreds[h])
        if (t == 1) parts += teens[o] else {
            parts += tens[t]
            parts += ones[o]
        }
        return parts.filter { it.isNotEmpty() }.joinToString(" ")
    }

    fun say(n: Long): String {
        if (n == 0L) return "zero"
        val parts = mutableListOf<String>()
        var rest = n
        var g = 0
        while (rest > 0) {
            val chunk = (rest % 1000).toInt()
            if (chunk > 0) {
                val (one, few, many) = groups[g]
                val word = when {
                    g == 0 -> triple(chunk)
                    chunk == 1 -> one
                    else -> triple(chunk) + " " + plural(chunk.toLong(), one, few, many)
                }
                parts.add(0, word)
            }
            rest /= 1000
            g++
        }
        return parts.joinToString(" ")
    }
}
