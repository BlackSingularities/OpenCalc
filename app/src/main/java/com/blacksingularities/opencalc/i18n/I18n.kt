package com.blacksingularities.opencalc.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Tłumaczenia interfejsu.
 *
 * Tekstem źródłowym jest polski – stoi wprost w kodzie, a słownik języka mapuje polskie zdanie
 * na przekład. Napisy z wartościami zapisujemy w słowniku z `{}` w miejscu wartości, np.
 * „Pamięć: {}” → „Memory: {}”, i taki wzorzec pasuje też do gotowego tekstu „Pamięć: 12”.
 *
 * Nowy język: plik w tym pakiecie z mapą `pl → przekład` (wzór: [En]) i jeden wiersz w [languages].
 * Brakujące przekłady zostają po polsku, więc niekompletny słownik niczego nie psuje.
 */
object I18n {
    class Language(val code: String, val name: String, val dict: Map<String, String>) {
        internal val patterns: List<Pair<Regex, String>> by lazy {
            dict.filterKeys { "{}" in it }.map { (k, v) ->
                Regex("^" + k.split("{}").joinToString("(.+?)") { Regex.escape(it) } + "$", RegexOption.DOT_MATCHES_ALL) to v
            }
        }
    }

    val languages = listOf(
        Language("pl", "Polski", emptyMap()),
        Language("en", "English", En.dict),
    )

    /** Kod z ustawień: „auto” – język systemu (gdy go nie mamy, angielski) albo kod języka. */
    var setting by mutableStateOf(AUTO)

    val current: Language
        get() {
            val code = if (setting == AUTO) systemCode() else setting
            return languages.firstOrNull { it.code == code } ?: languages.first { it.code == FALLBACK }
        }

    val isSource get() = current.code == SOURCE

    /** Locale do dat i nazw walut. */
    val locale: Locale get() = Locale.forLanguageTag(current.code)

    private fun systemCode(): String {
        val sys = Locale.getDefault().language
        return if (languages.any { it.code == sys }) sys else FALLBACK
    }

    fun tr(text: String): String {
        val lang = current
        if (lang.code == SOURCE || text.isEmpty()) return text
        lang.dict[text]?.let { return it }
        if (text.none { it.isLetter() }) return text
        for ((re, template) in lang.patterns) {
            val m = re.matchEntire(text) ?: continue
            var i = 0
            // Wartości wstawiane w szablon też mogą być tekstem do przetłumaczenia („Błąd: Dzielenie przez zero”).
            return Regex("\\{\\}").replace(template) { tr(m.groupValues.getOrElse(++i) { "" }) }
        }
        return text
    }

    const val AUTO = "auto"
    private const val SOURCE = "pl"
    private const val FALLBACK = "en"
}

/** Przekład tekstu źródłowego (polskiego) na bieżący język interfejsu. */
fun tr(text: String): String = I18n.tr(text)

/** Przekład z wartościami: tr("Pamięć: {}", "12") – kolejne `{}` dostają kolejne argumenty. */
fun tr(template: String, vararg args: Any?): String {
    var i = 0
    return Regex("\\{\\}").replace(I18n.tr(template)) { args.getOrNull(i++)?.toString() ?: "" }
}
