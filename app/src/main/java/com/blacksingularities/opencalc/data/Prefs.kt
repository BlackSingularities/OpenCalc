package com.blacksingularities.opencalc.data

import android.content.Context
import android.content.SharedPreferences
import com.blacksingularities.opencalc.engine.AngleMode
import com.blacksingularities.opencalc.engine.NumMode
import com.blacksingularities.opencalc.engine.NumberStyle
import org.json.JSONArray
import org.json.JSONObject

data class HistoryItem(val expr: String, val result: String, val value: Double, val time: Long, val pinned: Boolean = false)

data class GraphFn(val expr: String, val color: Int, val visible: Boolean = true, val derivative: Boolean = false)

data class Settings(
    val angle: AngleMode = AngleMode.DEG,
    val style: NumberStyle = NumberStyle(),
    val haptics: Boolean = true,
    val sciOpen: Boolean = true,
    val keepScreenOn: Boolean = false,
    val autoUpdate: Boolean = true,
)

/** Trwały stan aplikacji w SharedPreferences (JSON dla list). */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("opencalc", Context.MODE_PRIVATE)

    var settings: Settings
        get() = Settings(
            angle = runCatching { AngleMode.valueOf(sp.getString("angle", "DEG")!!) }.getOrDefault(AngleMode.DEG),
            style = NumberStyle(
                digits = sp.getInt("digits", 12),
                decimalComma = sp.getBoolean("comma", true),
                grouping = sp.getBoolean("grouping", true),
                mode = runCatching { NumMode.valueOf(sp.getString("numMode", "AUTO")!!) }.getOrDefault(NumMode.AUTO),
            ),
            haptics = sp.getBoolean("haptics", true),
            sciOpen = sp.getBoolean("sciOpen", true),
            keepScreenOn = sp.getBoolean("keepScreenOn", false),
            autoUpdate = sp.getBoolean("autoUpdate", true),
        )
        set(s) {
            sp.edit()
                .putString("angle", s.angle.name)
                .putInt("digits", s.style.digits)
                .putBoolean("comma", s.style.decimalComma)
                .putBoolean("grouping", s.style.grouping)
                .putString("numMode", s.style.mode.name)
                .putBoolean("haptics", s.haptics)
                .putBoolean("sciOpen", s.sciOpen)
                .putBoolean("keepScreenOn", s.keepScreenOn)
                .putBoolean("autoUpdate", s.autoUpdate)
                .apply()
        }

    var history: List<HistoryItem>
        get() = runCatching {
            val a = JSONArray(sp.getString("history", "[]"))
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                HistoryItem(o.getString("e"), o.getString("r"), o.optDouble("v", Double.NaN), o.optLong("t"), o.optBoolean("p"))
            }
        }.getOrDefault(emptyList())
        set(list) {
            val a = JSONArray()
            list.forEach {
                a.put(
                    JSONObject().put("e", it.expr).put("r", it.result).put("t", it.time).put("p", it.pinned)
                        .apply { if (!it.value.isNaN() && !it.value.isInfinite()) put("v", it.value) },
                )
            }
            sp.edit().putString("history", a.toString()).apply()
        }

    var graphs: List<GraphFn>
        get() = runCatching {
            val a = JSONArray(sp.getString("graphs", null) ?: return@runCatching DEFAULT_GRAPHS)
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                GraphFn(o.getString("e"), o.getInt("c"), o.optBoolean("v", true), o.optBoolean("d"))
            }
        }.getOrDefault(DEFAULT_GRAPHS)
        set(list) {
            val a = JSONArray()
            list.forEach { a.put(JSONObject().put("e", it.expr).put("c", it.color).put("v", it.visible).put("d", it.derivative)) }
            sp.edit().putString("graphs", a.toString()).apply()
        }

    var memory: Double
        get() = Double.fromBits(sp.getLong("memory", 0.0.toBits()))
        set(v) = sp.edit().putLong("memory", v.toBits()).apply()

    var ans: Double
        get() = Double.fromBits(sp.getLong("ans", 0.0.toBits()))
        set(v) = sp.edit().putLong("ans", v.toBits()).apply()

    /** Zmienne użytkownika A–F zapisywane przyciskiem „→ zmienna”. */
    var variables: Map<String, Double>
        get() = runCatching {
            val o = JSONObject(sp.getString("vars", "{}")!!)
            o.keys().asSequence().associateWith { o.getDouble(it) }
        }.getOrDefault(emptyMap())
        set(map) {
            val o = JSONObject()
            map.forEach { (k, v) -> if (!v.isNaN() && !v.isInfinite()) o.put(k, v) }
            sp.edit().putString("vars", o.toString()).apply()
        }

    var expression: String
        get() = sp.getString("expr", "") ?: ""
        set(v) = sp.edit().putString("expr", v).apply()

    var lastUpdateCheck: Long
        get() = sp.getLong("lastUpdateCheck", 0L)
        set(v) = sp.edit().putLong("lastUpdateCheck", v).apply()

    var convertCategory: String
        get() = sp.getString("convCat", "length") ?: "length"
        set(v) = sp.edit().putString("convCat", v).apply()

    var lastTab: Int
        get() = sp.getInt("tab", 0)
        set(v) = sp.edit().putInt("tab", v).apply()

    companion object {
        val DEFAULT_GRAPHS = listOf(GraphFn("sin(x)", 0), GraphFn("x^2/4 − 1", 1))
    }
}
