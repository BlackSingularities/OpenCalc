package com.blacksingularities.opencalc.ui

import com.blacksingularities.opencalc.data.Rates
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blacksingularities.opencalc.data.GraphFn
import com.blacksingularities.opencalc.data.HistoryItem
import com.blacksingularities.opencalc.data.Prefs
import com.blacksingularities.opencalc.data.Settings
import com.blacksingularities.opencalc.engine.Calc
import com.blacksingularities.opencalc.engine.CalcException
import com.blacksingularities.opencalc.engine.Complex
import com.blacksingularities.opencalc.engine.Computed
import com.blacksingularities.opencalc.engine.EvalContext
import com.blacksingularities.opencalc.engine.Fmt
import com.blacksingularities.opencalc.engine.Functions
import com.blacksingularities.opencalc.i18n.I18n
import com.blacksingularities.opencalc.update.UpdateManager
import com.blacksingularities.opencalc.update.UpdateState
import com.blacksingularities.opencalc.update.isNewer
import kotlinx.coroutines.launch

/** Stan całej aplikacji: kalkulator, historia, pamięć, wykresy, ustawienia i aktualizacje. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()
    val prefs = Prefs(app)

    var settings by mutableStateOf(prefs.settings)
        private set

    var tab by mutableStateOf(prefs.lastTab.coerceIn(0, 4))

    // ——— Kalkulator ———
    var expr by mutableStateOf(prefs.expression)
        private set
    var cursor by mutableStateOf(expr.length)
    var preview by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var lastExpr by mutableStateOf<String?>(null)
        private set
    var lastValue by mutableStateOf<Double?>(null)
        private set
    /** Wynik ostatniego „=” w postaci do wyświetlenia (dokładny lub zespolony). */
    var lastShown by mutableStateOf<String?>(null)
        private set
    var lastComplex by mutableStateOf<Complex?>(null)
        private set
    var evaluated by mutableStateOf(false)
        private set
    var second by mutableStateOf(false)
    var hyp by mutableStateOf(false)

    var history by mutableStateOf(prefs.history)
        private set
    var memory by mutableStateOf(prefs.memory)
        private set
    var ans by mutableStateOf(prefs.ans)
        private set
    var variables by mutableStateOf(prefs.variables)
        private set

    var message by mutableStateOf<String?>(null)

    // ——— Wykresy ———
    var graphs by mutableStateOf(prefs.graphs)
        private set

    init {
        I18n.setting = settings.language
        refreshPreview()
    }

    fun updateSettings(s: Settings) {
        settings = s
        prefs.settings = s
        I18n.setting = s.language
        refreshPreview()
    }

    fun selectTab(i: Int) {
        tab = i
        prefs.lastTab = i
    }

    private fun context(): EvalContext {
        val vars = HashMap<String, Double>()
        vars["Ans"] = ans
        vars["M"] = memory
        vars.putAll(variables)
        return EvalContext(settings.angle, vars)
    }

    fun evalText(text: String): Double {
        val r = Calc.compute(text, context())
        if (r.isComplex) throw CalcException("Wynik zespolony")
        return r.value
    }

    fun compute(text: String): Computed = Calc.compute(text, context())

    fun format(v: Double) = Fmt.number(v, settings.style)

    /** Liczba do wstawienia w wyrażenie, z minusem jak na klawiaturze. */
    fun plainOf(v: Double) = Fmt.plain(v).replace('-', '−')

    private fun setExpr(text: String, newCursor: Int) {
        expr = text
        cursor = newCursor.coerceIn(0, text.length)
        prefs.expression = text
        error = null
        refreshPreview()
    }

    private fun refreshPreview() {
        val t = expr
        preview = if (t.isBlank() || evaluated) null else try {
            val r = compute(t)
            val f = r.shown(settings.style)
            // Sama liczba nie potrzebuje podglądu.
            if (Fmt.displayExpr(t, settings.style.decimalComma) == f || t == r.plain().replace('-', '−')) null else f
        } catch (_: Exception) {
            null
        }
    }

    // ——— Edycja wyrażenia ———

    private fun isOperatorStart(s: String) = s.isNotEmpty() && s[0] in "+−×÷^%!²³⁻)" || s == "mod "

    /** Wstawia tekst w miejscu kursora; po „=” cyfra zaczyna nowe działanie, operator kontynuuje wynik. */
    fun insert(s: String) {
        if (evaluated) {
            evaluated = false
            if (isOperatorStart(s)) {
                setExpr(expr + s, expr.length + s.length)
            } else {
                setExpr(s, s.length)
            }
            return
        }
        val before = expr.substring(0, cursor)
        val after = expr.substring(cursor)
        var text = s
        // Dwa operatory obok siebie: nowy zastępuje poprzedni (oprócz minusa po ×, ÷, ^).
        if (s.length == 1 && s[0] in "+×÷^" && before.isNotEmpty() && before.last() in "+−×÷^") {
            setExpr(before.dropLast(1) + text + after, cursor)
            return
        }
        if (s == "−" && before.endsWith("−")) return
        if (s == "." ) {
            val num = before.takeLastWhile { it.isDigit() || it == '.' }
            if (num.contains('.')) return
            if (num.isEmpty()) text = "0."
        }
        setExpr(before + text + after, cursor + text.length)
    }

    fun insertFunction(name: String) = insert("$name(")

    fun parenSmart() {
        val before = expr.substring(0, if (evaluated) expr.length else cursor)
        val open = before.count { it == '(' } - before.count { it == ')' }
        val last = before.lastOrNull()
        if (open > 0 && last != null && (last.isLetterOrDigit() || last in ")πeφ!%²³")) insert(")") else insert("(")
    }

    fun backspace() {
        if (evaluated) {
            evaluated = false
            refreshPreview()
            return
        }
        if (cursor == 0) return
        val before = expr.substring(0, cursor)
        val after = expr.substring(cursor)
        // Całe nazwy funkcji i Ans usuwane jednym dotknięciem.
        val token = Regex("([A-Za-z][A-Za-z0-9]*\\(|Ans|mod )$").find(before)?.value
        val cut = if (token != null && (token.endsWith("(").not() || Functions.find(token.dropLast(1)) != null)) token.length else 1
        setExpr(before.dropLast(cut) + after, cursor - cut)
    }

    fun clear() {
        evaluated = false
        lastExpr = null
        lastValue = null
        lastShown = null
        lastComplex = null
        setExpr("", 0)
    }

    fun moveCursor(delta: Int) {
        if (evaluated) evaluated = false
        cursor = (cursor + delta).coerceIn(0, expr.length)
    }

    fun setCursorAt(i: Int) {
        if (evaluated) evaluated = false
        cursor = i.coerceIn(0, expr.length)
    }

    /** Zmiana znaku liczby przed kursorem: 12 → −12 → 12. */
    fun negate() {
        if (evaluated) {
            evaluated = false
            val e = if (expr.startsWith("−")) expr.drop(1) else "−$expr"
            setExpr(e, e.length)
            return
        }
        val before = expr.substring(0, cursor)
        var start = before.length
        while (start > 0 && (before[start - 1].isLetterOrDigit() || before[start - 1] == '.' || before[start - 1] in "πφ")) start--
        val prev = before.getOrNull(start - 1)
        val prevPrev = before.getOrNull(start - 2)
        if (prev == '−' && (prevPrev == null || prevPrev in "+−×÷^(")) {
            setExpr(expr.removeRange(start - 1, start), cursor - 1)
        } else {
            setExpr(expr.substring(0, start) + "−" + expr.substring(start), cursor + 1)
        }
    }

    fun setExpression(text: String) {
        evaluated = false
        setExpr(text, text.length)
    }

    fun equals() {
        if (expr.isBlank()) return
        if (evaluated) return
        try {
            val r = compute(expr)
            val v = r.value
            if (v.isNaN()) throw CalcException("Wynik nieokreślony")
            val shown = r.shown(settings.style)
            val item = HistoryItem(expr, shown, if (r.isComplex) Double.NaN else v, System.currentTimeMillis())
            history = (listOf(item) + history.filterNot { it.expr == expr && !it.pinned }).take(300)
            prefs.history = history
            if (!r.isComplex) {
                ans = v
                prefs.ans = v
            }
            lastExpr = expr
            lastValue = if (r.isComplex) null else v
            lastShown = shown
            lastComplex = r.complex
            val plain = r.plain().replace('-', '−')
            expr = plain
            cursor = plain.length
            prefs.expression = plain
            evaluated = true
            preview = null
            error = null
        } catch (e: CalcException) {
            error = e.message
        } catch (e: Exception) {
            error = "Błąd wyrażenia"
        }
    }

    // ——— Pamięć i zmienne ———
    private fun currentValue(): Double? = if (evaluated) lastValue else runCatching { evalText(expr) }.getOrNull()

    fun memoryAdd(sign: Int) {
        val v = currentValue() ?: run { message = "Brak wartości do zapamiętania"; return }
        memory += sign * v
        prefs.memory = memory
        message = "Pamięć: ${format(memory)}"
    }

    fun memoryClear() {
        memory = 0.0
        prefs.memory = 0.0
        message = "Pamięć wyczyszczona"
    }

    fun memoryRecall() = insert("M")

    fun store(name: String) {
        val v = currentValue() ?: run { message = "Brak wartości do zapisania"; return }
        variables = variables + (name to v)
        prefs.variables = variables
        message = "$name = ${format(v)}"
    }

    fun clearVariables() {
        variables = emptyMap()
        prefs.variables = emptyMap()
    }

    // ——— Historia ———
    fun useHistory(item: HistoryItem, asResult: Boolean) {
        if (asResult && !item.value.isNaN()) {
            if (evaluated || expr.isEmpty()) setExpression(plainOf(item.value)) else insert(plainOf(item.value))
        } else {
            setExpression(item.expr)
        }
    }

    fun togglePin(item: HistoryItem) {
        history = history.map { if (it == item) it.copy(pinned = !it.pinned) else it }
        prefs.history = history
    }

    fun deleteHistory(item: HistoryItem) {
        history = history - item
        prefs.history = history
    }

    fun clearHistory() {
        history = history.filter { it.pinned }
        prefs.history = history
    }

    // ——— Wykresy ———
    fun updateGraphs(list: List<GraphFn>) {
        graphs = list
        prefs.graphs = list
    }

    // ——— Kursy walut ———
    var rates by mutableStateOf(Rates.cached(app))
        private set
    var ratesLoading by mutableStateOf(false)
        private set
    var ratesError by mutableStateOf<String?>(null)
        private set

    /** Pobiera tabelę NBP, gdy nie ma żadnej albo ma ponad 6 godzin (albo na żądanie). */
    fun refreshRates(force: Boolean = false) {
        val r = rates
        if (ratesLoading || (!force && r != null && System.currentTimeMillis() - r.fetchedAt < 6 * 3_600_000L)) return
        ratesLoading = true
        ratesError = null
        viewModelScope.launch {
            try {
                rates = Rates.fetch(ctx)
            } catch (e: Exception) {
                ratesError = "Nie udało się pobrać kursów"
            } finally {
                ratesLoading = false
            }
        }
    }

    // ——— Aktualizacje ———
    var update by mutableStateOf<UpdateState>(UpdateState.Idle)
    var updateBannerHidden by mutableStateOf(false)

    val updatePending: Boolean
        get() = update is UpdateState.Available || update is UpdateState.Downloading || update is UpdateState.Ready || update is UpdateState.Installing

    fun resumed() {
        if (settings.autoUpdate && System.currentTimeMillis() - prefs.lastUpdateCheck > 6 * 3_600_000L) checkUpdate(silent = true)
    }

    fun checkUpdate(silent: Boolean) {
        if (update is UpdateState.Checking || update is UpdateState.Downloading || update is UpdateState.Installing) return
        update = UpdateState.Checking
        viewModelScope.launch {
            update = try {
                val info = UpdateManager.check()
                prefs.lastUpdateCheck = System.currentTimeMillis()
                if (isNewer(info.version, UpdateManager.currentVersion)) {
                    updateBannerHidden = false
                    UpdateState.Available(info)
                } else {
                    UpdateState.UpToDate
                }
            } catch (e: Exception) {
                if (silent) UpdateState.Idle else UpdateState.Failed(e.message ?: "Błąd")
            }
        }
    }

    fun updateNow() {
        when (val u = update) {
            is UpdateState.Available -> {
                if (!UpdateManager.canInstall(ctx)) {
                    message = "Zezwól aplikacji na instalowanie aktualizacji"
                    UpdateManager.openInstallPermissionSettings(ctx)
                    return
                }
                viewModelScope.launch {
                    try {
                        update = UpdateState.Downloading(u.info, 0f)
                        val file = UpdateManager.download(ctx, u.info) { p -> update = UpdateState.Downloading(u.info, p) }
                        update = UpdateState.Installing(u.info)
                        UpdateManager.install(ctx, file)
                        update = UpdateState.Ready(u.info, file)
                    } catch (e: Exception) {
                        update = UpdateState.Available(u.info)
                        message = e.message ?: "Nie udało się pobrać aktualizacji"
                    }
                }
            }
            is UpdateState.Ready -> viewModelScope.launch {
                runCatching { UpdateManager.install(ctx, u.file) }.onFailure { message = it.message }
            }
            else -> {}
        }
    }
}
