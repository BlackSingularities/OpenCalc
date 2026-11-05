package com.blacksingularities.opencalc.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.blacksingularities.opencalc.MainActivity
import com.blacksingularities.opencalc.R
import com.blacksingularities.opencalc.data.Prefs
import com.blacksingularities.opencalc.engine.Calc
import com.blacksingularities.opencalc.engine.CalcException
import com.blacksingularities.opencalc.engine.EvalContext
import com.blacksingularities.opencalc.engine.Fmt
import com.blacksingularities.opencalc.i18n.I18n
import com.blacksingularities.opencalc.i18n.tr

/**
 * Widżet z prostym kalkulatorem na ekranie głównym. Każdy klawisz wysyła rozgłoszenie do tej
 * klasy, stan (wyrażenie i wynik) leży w osobnych SharedPreferences, a liczy ten sam silnik co
 * aplikacja – z dokładną arytmetyką dziesiętną. Dotknięcie wyświetlacza otwiera aplikację.
 */
class CalcWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_KEY) return
        press(context, intent.getStringExtra(EXTRA_KEY) ?: return)
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, CalcWidget::class.java))
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    private class State(val expr: String, val result: String, val evaluated: Boolean)

    private fun load(context: Context): State {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return State(sp.getString("expr", "") ?: "", sp.getString("result", "") ?: "", sp.getBoolean("evaluated", false))
    }

    private fun save(context: Context, s: State) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("expr", s.expr)
            .putString("result", s.result)
            .putBoolean("evaluated", s.evaluated)
            .apply()
    }

    private fun press(context: Context, key: String) {
        val settings = Prefs(context).settings
        I18n.setting = settings.language
        val s = load(context)
        val ctx = EvalContext(settings.angle, mapOf("Ans" to Prefs(context).ans))
        fun preview(e: String) = runCatching { Calc.compute(e, ctx).shown(settings.style) }.getOrDefault("")
        val operator = key in OPERATORS
        // Po „=” cyfra zaczyna nowe działanie, a operator liczy dalej od wyniku – jak w aplikacji.
        val base = if (s.evaluated && !operator && key != "=" && key != "⌫") "" else s.expr
        val next: State = when (key) {
            "C" -> State("", "", false)
            "⌫" -> State(base.dropLast(1), preview(base.dropLast(1)), false)
            "()" -> {
                val open = base.count { it == '(' } - base.count { it == ')' }
                val last = base.lastOrNull()
                val add = if (open > 0 && last != null && (last.isDigit() || last in ")%")) ")" else "("
                State(base + add, preview(base + add), false)
            }
            "=" -> try {
                if (base.isBlank()) s else {
                    val r = Calc.compute(base, ctx)
                    State(r.plain().replace('-', '−'), r.shown(settings.style), true)
                }
            } catch (e: CalcException) {
                State(base, tr(e.message ?: "Błąd"), false)
            } catch (_: Exception) {
                State(base, tr("Błąd wyrażenia"), false)
            }
            else -> {
                // Dwa operatory obok siebie: nowy zastępuje poprzedni.
                val trimmed = if (key in "+×÷" && base.lastOrNull()?.let { it in "+−×÷" } == true) base.dropLast(1) else base
                val e = trimmed + if (key == "." && trimmed.lastOrNull()?.isDigit() != true) "0." else key
                State(e, preview(e), false)
            }
        }
        save(context, next)
    }

    private fun views(context: Context): RemoteViews {
        val settings = Prefs(context).settings
        val s = load(context)
        val comma = settings.style.decimalComma
        val v = RemoteViews(context.packageName, R.layout.widget_calc)
        val shownExpr = Fmt.displayExpr(s.expr, comma)
        if (s.evaluated) {
            v.setTextViewText(R.id.w_expr, "")
            v.setTextViewText(R.id.w_result, s.result)
        } else {
            v.setTextViewText(R.id.w_expr, if (s.result.isEmpty()) "" else "= " + s.result)
            v.setTextViewText(R.id.w_result, shownExpr.ifEmpty { "0" })
        }
        v.setTextViewText(R.id.k_dot, if (comma) "," else ".")
        KEYS.forEachIndexed { i, (id, key) ->
            val intent = Intent(context, CalcWidget::class.java).setAction(ACTION_KEY).putExtra(EXTRA_KEY, key)
            v.setOnClickPendingIntent(id, PendingIntent.getBroadcast(context, i + 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        v.setOnClickPendingIntent(R.id.w_display, PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE))
        return v
    }

    companion object {
        private const val ACTION_KEY = "com.blacksingularities.opencalc.WIDGET_KEY"
        private const val EXTRA_KEY = "key"
        private const val PREFS = "opencalc_widget"
        private val OPERATORS = setOf("+", "−", "×", "÷", "%")

        private val KEYS = listOf(
            R.id.k_clear to "C", R.id.k_paren to "()", R.id.k_back to "⌫", R.id.k_div to "÷",
            R.id.k_7 to "7", R.id.k_8 to "8", R.id.k_9 to "9", R.id.k_mul to "×",
            R.id.k_4 to "4", R.id.k_5 to "5", R.id.k_6 to "6", R.id.k_sub to "−",
            R.id.k_1 to "1", R.id.k_2 to "2", R.id.k_3 to "3", R.id.k_add to "+",
            R.id.k_pct to "%", R.id.k_0 to "0", R.id.k_dot to ".", R.id.k_eq to "=",
        )
    }
}
