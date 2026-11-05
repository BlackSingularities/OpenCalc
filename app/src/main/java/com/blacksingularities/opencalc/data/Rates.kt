package com.blacksingularities.opencalc.data

import android.content.Context
import com.blacksingularities.opencalc.engine.Measure
import com.blacksingularities.opencalc.engine.UnitCategory
import com.blacksingularities.opencalc.i18n.I18n
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.Currency

/** Kurs średni waluty w złotych za jednostkę (tabela A NBP). */
data class Rate(val code: String, val name: String, val mid: Double)

data class RateTable(val date: String, val rates: List<Rate>, val fetchedAt: Long)

/**
 * Kursy walut z otwartego API Narodowego Banku Polskiego (tabela A, kursy średnie, bez klucza).
 * Ostatnią tabelę trzymamy w pamięci telefonu, więc przelicznik działa też bez internetu.
 */
object Rates {
    private const val URL_A = "https://api.nbp.pl/api/exchangerates/tables/A/?format=json"
    private const val KEY = "nbpTableA"
    private const val KEY_TIME = "nbpFetchedAt"

    /** Waluty na górze listy; reszta alfabetycznie po kodzie. */
    private val FAVOURITES = listOf("PLN", "EUR", "USD", "GBP", "CHF", "CZK", "NOK", "SEK", "DKK", "UAH", "JPY", "CAD", "AUD")

    fun cached(context: Context): RateTable? {
        val sp = context.getSharedPreferences("opencalc", Context.MODE_PRIVATE)
        val json = sp.getString(KEY, null) ?: return null
        return runCatching { parse(json, sp.getLong(KEY_TIME, 0L)) }.getOrNull()
    }

    suspend fun fetch(context: Context): RateTable = withContext(Dispatchers.IO) {
        val conn = (URL(URL_A).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode != 200) throw IllegalStateException("NBP: HTTP ${conn.responseCode}")
            val json = conn.inputStream.bufferedReader().use { it.readText() }
            val now = System.currentTimeMillis()
            val table = parse(json, now)
            context.getSharedPreferences("opencalc", Context.MODE_PRIVATE).edit()
                .putString(KEY, json)
                .putLong(KEY_TIME, now)
                .apply()
            table
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(json: String, fetchedAt: Long): RateTable {
        val t = JSONArray(json).getJSONObject(0)
        val a = t.getJSONArray("rates")
        val list = List(a.length()) { i ->
            val o = a.getJSONObject(i)
            Rate(o.getString("code"), o.getString("currency"), o.getDouble("mid"))
        }
        return RateTable(t.getString("effectiveDate"), list, fetchedAt)
    }

    /** Kategoria przelicznika: złoty jako jednostka bazowa, każda waluta z kursem średnim. */
    fun category(table: RateTable?): UnitCategory {
        val all = listOf(Rate("PLN", "złoty polski", 1.0)) + (table?.rates ?: emptyList())
        val sorted = all.sortedWith(compareBy<Rate> { FAVOURITES.indexOf(it.code).let { i -> if (i < 0) Int.MAX_VALUE else i } }.thenBy { it.code })
        return UnitCategory(
            CATEGORY_ID,
            "Waluty",
            sorted.map { r -> Measure(r.code, displayName(r), r.code, r.mid) },
        )
    }

    /** Nazwa waluty w języku interfejsu (z systemu), a gdy system jej nie zna – nazwa z NBP. */
    private fun displayName(r: Rate): String {
        val name = runCatching { Currency.getInstance(r.code).getDisplayName(I18n.locale) }.getOrNull()
        val n = if (name == null || name == r.code) r.name else name
        return n.replaceFirstChar { it.uppercase() }
    }

    const val CATEGORY_ID = "currency"
}
