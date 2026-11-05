package com.blacksingularities.opencalc.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opencalc.data.GraphFn
import com.blacksingularities.opencalc.data.GraphKind
import com.blacksingularities.opencalc.engine.AngleMode
import com.blacksingularities.opencalc.engine.Analysis
import com.blacksingularities.opencalc.engine.CalcException
import com.blacksingularities.opencalc.engine.EvalContext
import com.blacksingularities.opencalc.engine.Fmt
import com.blacksingularities.opencalc.engine.Func
import com.blacksingularities.opencalc.engine.KeyPoint
import com.blacksingularities.opencalc.engine.Node
import com.blacksingularities.opencalc.engine.NumberStyle
import com.blacksingularities.opencalc.engine.Parser
import com.blacksingularities.opencalc.engine.PointKind
import com.blacksingularities.opencalc.engine.evaluate
import com.blacksingularities.opencalc.engine.niceStep
import com.blacksingularities.opencalc.i18n.tr
import com.blacksingularities.opencalc.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Wykres funkcji y = f(x) – jedyny rodzaj z odczytem wartości, analizą i tabelą. */
private class Plot(val index: Int, val color: Color, val f: (Double) -> Double, val label: String)

/** Krzywa parametryczna lub biegunowa: punkt (x, y) dla parametru t z przedziału [t0; t1]. */
private class Curve(val color: Color, val t0: Double, val t1: Double, val xy: (Double) -> Pair<Double, Double>?)

/** Nierówność dwóch zmiennych – zamalowany obszar, w którym jest spełniona. */
private class Region(val color: Color, val test: (Double, Double) -> Boolean)

/** Wynik parsowania jednego wiersza: dokładnie jedna z postaci albo błąd. */
private class Parsed(val fn: Func? = null, val curve: Curve? = null, val region: Region? = null, val error: String? = null)

private val KIND_LABEL = mapOf(
    GraphKind.FUNCTION to "f(x)",
    GraphKind.PARAMETRIC to "x(t), y(t)",
    GraphKind.POLAR to "r(θ)",
    GraphKind.INEQUALITY to "≤ ≥",
)

private val KIND_NAME = mapOf(
    GraphKind.FUNCTION to "Funkcja y = f(x)",
    GraphKind.PARAMETRIC to "Krzywa parametryczna",
    GraphKind.POLAR to "Wykres biegunowy",
    GraphKind.INEQUALITY to "Nierówność",
)

/** Funkcja jednej zmiennej o kilku nazwach (t i θ), błędy dziedziny dają NaN. */
private fun fnOf(node: Node, names: List<String>): (Double) -> Double = { v ->
    try {
        node.eval(EvalContext(AngleMode.RAD, names.associateWith { v }))
    } catch (_: CalcException) {
        Double.NaN
    } catch (_: ArithmeticException) {
        Double.NaN
    }
}

private fun parseGraph(g: GraphFn, color: Color): Parsed = try {
    when (g.kind) {
        GraphKind.FUNCTION -> Parsed(fn = Func.parse(g.expr.ifBlank { "0" }, AngleMode.RAD))
        GraphKind.PARAMETRIC, GraphKind.POLAR -> {
            val t0 = evaluate(g.t0.ifBlank { "0" })
            val t1 = evaluate(g.t1.ifBlank { "2π" })
            if (!(t1 > t0)) throw CalcException("Zakres: początek musi być mniejszy od końca")
            val names = listOf("t", "θ")
            val a = fnOf(Parser.parse(g.expr.ifBlank { "0" }, names.toSet()), names)
            val curve = if (g.kind == GraphKind.POLAR) {
                Curve(color, t0, t1) { t -> a(t).let { r -> r * cos(t) to r * sin(t) } }
            } else {
                val b = fnOf(Parser.parse(g.expr2.ifBlank { "0" }, names.toSet()), names)
                Curve(color, t0, t1) { t -> a(t) to b(t) }
            }
            Parsed(curve = curve)
        }
        GraphKind.INEQUALITY -> Parsed(region = parseInequality(g.expr, color))
    }
} catch (e: Exception) {
    Parsed(error = e.message ?: "Błąd")
}

/** „y > x²”, „x² + y² ≤ 4”: obszar, w którym nierówność jest prawdziwa. */
private fun parseInequality(text: String, color: Color): Region {
    val t = text.replace("<=", "≤").replace(">=", "≥")
    val i = t.indexOfFirst { it in "<>≤≥" }
    if (i < 0) throw CalcException("Nierówność musi zawierać <, >, ≤ lub ≥")
    val op = t[i]
    val vars = setOf("x", "y")
    val left = Parser.parse(t.substring(0, i), vars)
    val right = Parser.parse(t.substring(i + 1), vars)
    return Region(color) { x, y ->
        try {
            val c = EvalContext(AngleMode.RAD, mapOf("x" to x, "y" to y))
            val d = left.eval(c) - right.eval(c)
            when (op) {
                '<' -> d < 0
                '>' -> d > 0
                '≤' -> d <= 0
                else -> d >= 0
            }
        } catch (_: Exception) {
            false
        }
    }
}

@Composable
fun GraphScreen(vm: AppViewModel) {
    // Widok: środek i liczba jednostek na piksel (ta sama skala w obu osiach).
    var cx by rememberSaveable { mutableDoubleStateOf(0.0) }
    var cy by rememberSaveable { mutableDoubleStateOf(0.0) }
    var unit by rememberSaveable { mutableDoubleStateOf(0.02) }
    var analyze by rememberSaveable { mutableStateOf(false) }
    var table by rememberSaveable { mutableStateOf(false) }
    var trace by remember { mutableStateOf<Offset?>(null) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    val graphs = vm.graphs
    val numStyle = vm.settings.style.copy(digits = 6, grouping = false)
    val graphColors = Palette.Graph

    // Wykresy parsowane raz na zmianę tekstu; błędy pokazujemy pod polem.
    val parsed = remember(graphs, graphColors) {
        graphs.map { g -> parseGraph(g, graphColors[g.color % graphColors.size]) }
    }
    val plots = remember(parsed, graphs) {
        buildList {
            parsed.forEachIndexed { i, p ->
                val g = graphs[i]
                val fn = p.fn ?: return@forEachIndexed
                if (!g.visible || g.expr.isBlank()) return@forEachIndexed
                val color = graphColors[g.color % graphColors.size]
                add(Plot(i, color, { x -> fn(x) }, "f${i + 1}"))
                if (g.derivative) add(Plot(i, color.copy(alpha = 0.55f), { x -> Analysis.derivative({ fn(it) }, x) }, "f${i + 1}′"))
            }
        }
    }
    val curves = remember(parsed, graphs) { parsed.filterIndexed { i, _ -> graphs[i].visible && graphs[i].expr.isNotBlank() }.mapNotNull { it.curve } }
    val regions = remember(parsed, graphs) { parsed.filterIndexed { i, _ -> graphs[i].visible && graphs[i].expr.isNotBlank() }.mapNotNull { it.region } }

    val left = cx - canvasSize.width / 2 * unit
    val right = cx + canvasSize.width / 2 * unit
    val keyPoints by produceState(emptyList<KeyPoint>(), analyze, plots, left, right) {
        value = if (!analyze || canvasSize.width == 0f) emptyList() else withContext(Dispatchers.Default) {
            findKeyPoints(plots.filter { !it.label.endsWith("′") }, left, right)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Palette.Group),
        ) {
            val measurer = rememberTextMeasurer()
            val graphDesc = tr("Wykres funkcji")
            Canvas(
                Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = graphDesc }
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val w = size.width
                            val h = size.height
                            // Powiększenie wokół punktu między palcami.
                            val wx = cx + (centroid.x - w / 2) * unit
                            val wy = cy - (centroid.y - h / 2) * unit
                            val newUnit = (unit / zoom).coerceIn(1e-7, 1e5)
                            cx = wx - (centroid.x - w / 2) * newUnit - pan.x * newUnit
                            cy = wy + (centroid.y - h / 2) * newUnit + pan.y * newUnit
                            unit = newUnit
                            trace = null
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { trace = if (trace == null) it else null },
                            onDoubleTap = { cx = 0.0; cy = 0.0; unit = 0.02; trace = null },
                        )
                    },
            ) {
                canvasSize = size
                val w = size.width
                val h = size.height
                val l = cx - w / 2 * unit
                val t = cy + h / 2 * unit
                fun sx(x: Double) = ((x - l) / unit).toFloat()
                fun sy(y: Double) = ((t - y) / unit).toFloat()

                // Nierówności pod siatką: zamalowane kratki tam, gdzie warunek jest spełniony.
                if (regions.isNotEmpty()) {
                    val cell = 5.dp.toPx()
                    regions.forEach { r ->
                        val fill = r.color.copy(alpha = 0.22f)
                        var py = 0f
                        while (py < h) {
                            val y = t - (py + cell / 2) * unit
                            var px = 0f
                            while (px < w) {
                                if (r.test(l + (px + cell / 2) * unit, y)) drawRect(fill, Offset(px, py), Size(cell, cell))
                                px += cell
                            }
                            py += cell
                        }
                    }
                }

                // Siatka i podpisy osi.
                val step = niceStep(w * unit, 6)
                val labelStyle = TextStyle(color = Palette.TextFaint, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                val axisY = sy(0.0).coerceIn(0f, h - 14.dp.toPx())
                val axisX = sx(0.0).coerceIn(0f, w - 30.dp.toPx())
                var gx = ceil(l / step) * step
                while (gx <= l + w * unit) {
                    val px = sx(gx)
                    drawLine(Palette.Line.copy(alpha = 0.10f), Offset(px, 0f), Offset(px, h), 1f)
                    if (abs(gx) > step / 2) drawText(measurer, label(gx, numStyle), Offset(px + 3, axisY + 2), labelStyle)
                    gx += step
                }
                var gy = floor((t - h * unit) / step) * step
                while (gy <= t) {
                    val py = sy(gy)
                    drawLine(Palette.Line.copy(alpha = 0.10f), Offset(0f, py), Offset(w, py), 1f)
                    if (abs(gy) > step / 2) drawText(measurer, label(gy, numStyle), Offset(axisX + 4, py - 14), labelStyle)
                    gy += step
                }
                drawLine(Palette.TextFaint, Offset(0f, sy(0.0)), Offset(w, sy(0.0)), 1.5f)
                drawLine(Palette.TextFaint, Offset(sx(0.0), 0f), Offset(sx(0.0), h), 1.5f)

                // Wykresy funkcji: próbka co 2 px, przerwa przy asymptotach i poza dziedziną.
                plots.forEach { p ->
                    val path = Path()
                    var pen = false
                    var prevY = 0f
                    var px = 0f
                    while (px <= w) {
                        val y = p.f(l + px * unit)
                        if (y.isNaN() || y.isInfinite()) {
                            pen = false
                        } else {
                            val py = sy(y).coerceIn(-h * 4, h * 5)
                            if (pen && abs(py - prevY) > h * 1.5f) pen = false
                            if (pen) path.lineTo(px, py) else path.moveTo(px, py)
                            pen = true
                            prevY = py
                        }
                        px += 2f
                    }
                    val dashed = p.label.endsWith("′")
                    drawPath(
                        path,
                        p.color,
                        style = Stroke(width = 2.5.dp.toPx(), pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(14f, 10f)) else null),
                    )
                }

                // Krzywe parametryczne i biegunowe: 1500 kroków parametru.
                curves.forEach { c ->
                    val path = Path()
                    var pen = false
                    var prev = Offset.Zero
                    val n = 1500
                    for (k in 0..n) {
                        val pt = c.xy(c.t0 + (c.t1 - c.t0) * k / n)
                        if (pt == null || pt.first.isNaN() || pt.second.isNaN() || pt.first.isInfinite() || pt.second.isInfinite()) {
                            pen = false
                            continue
                        }
                        val o = Offset(sx(pt.first).coerceIn(-w * 4, w * 5), sy(pt.second).coerceIn(-h * 4, h * 5))
                        if (pen && (o - prev).getDistance() > maxOf(w, h) * 1.5f) pen = false
                        if (pen) path.lineTo(o.x, o.y) else path.moveTo(o.x, o.y)
                        pen = true
                        prev = o
                    }
                    drawPath(path, c.color, style = Stroke(width = 2.5.dp.toPx()))
                }

                keyPoints.forEach { k ->
                    val o = Offset(sx(k.x), sy(k.y))
                    drawCircle(Palette.Bg, 6.dp.toPx(), o)
                    drawCircle(Palette.Fg, 4.dp.toPx(), o)
                }

                trace?.let { tap ->
                    val x = l + tap.x * unit
                    val best = plots.mapNotNull { p -> p.f(x).takeIf { !it.isNaN() && !it.isInfinite() }?.let { p to it } }
                        .minByOrNull { (_, y) -> abs(sy(y) - tap.y) }
                    drawLine(Palette.Fg.copy(alpha = 0.4f), Offset(tap.x, 0f), Offset(tap.x, h), 1f)
                    if (best != null) {
                        val (p, y) = best
                        val o = Offset(tap.x, sy(y))
                        drawCircle(p.color, 6.dp.toPx(), o)
                        val text = "${p.label}(${label(x, numStyle)}) = ${label(y, numStyle)}"
                        val layout = measurer.measure(text, TextStyle(color = Palette.Fg, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                        val bx = (o.x + 10).coerceAtMost(w - layout.size.width - 16f)
                        val by = (o.y - layout.size.height - 18).coerceIn(4f, h - layout.size.height - 12f)
                        drawRoundRect(Palette.PanelHigh.copy(alpha = 0.94f), Offset(bx - 8, by - 4), Size(layout.size.width + 16f, layout.size.height + 8f), androidx.compose.ui.geometry.CornerRadius(12f))
                        drawText(layout, topLeft = Offset(bx, by))
                    }
                }
            }
            Row(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                GlassButton(Icons.Rounded.TableChart, "Tabela wartości", table) { table = !table }
                GlassButton(Icons.Rounded.Insights, "Analiza: miejsca zerowe, ekstrema, przecięcia", analyze) { analyze = !analyze }
                GlassButton(Icons.Rounded.ZoomIn, "Przybliż", false) { unit /= 1.5 }
                GlassButton(Icons.Rounded.ZoomOut, "Oddal", false) { unit *= 1.5 }
                GlassButton(Icons.Rounded.CenterFocusStrong, "Wyśrodkuj", false) { cx = 0.0; cy = 0.0; unit = 0.02; trace = null }
            }
            Text(
                "Dotknij – odczyt wartości · szczypanie – powiększenie · podwójne dotknięcie – reset",
                color = Palette.TextFaint,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(6.dp),
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 340.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (table) ValueTable(vm, plots.filter { !it.label.endsWith("′") })
            if (analyze && keyPoints.isNotEmpty()) {
                Section("Punkty charakterystyczne") {
                    keyPoints.take(24).forEach { k ->
                        val who = if (k.kind == PointKind.CROSS) "f${k.owner + 1} ∩ f${k.other + 1}" else "f${k.owner + 1}"
                        ResultRow("${tr(k.kind.label)} · $who", "(${label(k.x, numStyle)}; ${label(k.y, numStyle)})")
                    }
                }
            }
            graphs.forEachIndexed { i, g ->
                FunctionRow(i, g, parsed.getOrNull(i)?.error, onChange = { ng -> vm.updateGraphs(graphs.toMutableList().also { it[i] = ng }) }) {
                    vm.updateGraphs(graphs.filterIndexed { j, _ -> j != i })
                }
            }
            if (graphs.size < 8) {
                Row(
                    Modifier
                        .clip(buttonShape())
                        .background(Palette.Group)
                        .clickable {
                            val used = graphs.map { it.color }.toSet()
                            val color = (0 until graphColors.size).firstOrNull { it !in used } ?: graphs.size
                            vm.updateGraphs(graphs + GraphFn("", color))
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, null, tint = Palette.Fg, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Dodaj funkcję", color = Palette.Fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
            Hint("Dotknij rodzaju wykresu (f(x)), by przełączyć: funkcja, krzywa parametryczna x(t), y(t), wykres biegunowy r(θ) albo nierówność, np. y > x² lub x² + y² ≤ 4. Kąty w radianach. Dotknięcie kropki ukrywa wykres, f′ rysuje pochodną.")
        }
    }
}

/** Tabela wartości funkcji y = f(x): początek, krok i 21 wierszy. */
@Composable
private fun ValueTable(vm: AppViewModel, plots: List<Plot>) {
    var start by rememberSaveable { mutableStateOf("-5") }
    var step by rememberSaveable { mutableStateOf("1") }
    val style = vm.settings.style.copy(digits = 6, grouping = false)
    Section("Tabela wartości") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField(start, { start = it }, "Od x", Modifier.weight(1f), keyboard = androidx.compose.ui.text.input.KeyboardType.Text)
            NumField(step, { step = it }, "Krok", Modifier.weight(1f), keyboard = androidx.compose.ui.text.input.KeyboardType.Text)
        }
        val x0 = runCatching { vm.evalText(start.replace(',', '.')) }.getOrNull()
        val dx = runCatching { vm.evalText(step.replace(',', '.')) }.getOrNull()
        if (plots.isEmpty()) {
            Hint("Brak widocznych funkcji y = f(x)")
        } else if (x0 == null || dx == null || dx == 0.0) {
            ErrorText("Podaj początek i niezerowy krok")
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Column {
                    TableCell("x", Palette.TextDim, bold = true)
                    for (k in 0..20) TableCell(label(x0 + k * dx, style), Palette.TextDim)
                }
                plots.forEach { p ->
                    Column {
                        TableCell("${p.label}(x)", p.color, bold = true)
                        for (k in 0..20) {
                            val y = p.f(x0 + k * dx)
                            TableCell(if (y.isNaN() || y.isInfinite()) "—" else label(y, style), Palette.Fg)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableCell(text: String, color: Color, bold: Boolean = false) {
    RawText(
        text,
        color = color,
        fontSize = 13.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
        modifier = Modifier.widthIn(min = 92.dp).padding(end = 12.dp, top = 2.dp, bottom = 2.dp),
    )
}

@Composable
private fun GlassButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, active: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .padding(2.dp)
            .size(38.dp)
            .clip(CircleShape)
            .background(if (active) Palette.Fg else Palette.Bg.copy(alpha = 0.7f))
            .border(1.dp, Palette.Line, CircleShape),
    ) {
        Icon(icon, desc, tint = if (active) Palette.Bg else Palette.Fg, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun FunctionRow(i: Int, g: GraphFn, error: String?, onChange: (GraphFn) -> Unit, onDelete: () -> Unit) {
    val color = Palette.Graph[g.color % Palette.Graph.size]
    val n = i + 1
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Group)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (g.visible) color else Color.Transparent)
                    .border(2.dp, color, CircleShape)
                    .semantics { contentDescription = tr(if (g.visible) "Ukryj wykres" else "Pokaż wykres") }
                    .clickable { onChange(g.copy(visible = !g.visible)) },
            )
            Spacer(Modifier.width(6.dp))
            // Rodzaj wykresu – dotknięcie przełącza na następny.
            Text(
                KIND_LABEL.getValue(g.kind),
                color = Palette.TextDim,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .clip(buttonShape())
                    .background(Palette.KeyOp)
                    .semantics { contentDescription = tr("Rodzaj wykresu: {}", tr(KIND_NAME.getValue(g.kind))) }
                    .clickable { onChange(g.copy(kind = GraphKind.entries[(g.kind.ordinal + 1) % GraphKind.entries.size], derivative = false)) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            val prefix = when (g.kind) {
                GraphKind.FUNCTION -> "  f$n(x) = "
                GraphKind.PARAMETRIC -> "  x$n(t) = "
                GraphKind.POLAR -> "  r$n(θ) = "
                GraphKind.INEQUALITY -> "  "
            }
            RawText(prefix, color = Palette.TextDim, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
            ExprField(
                g.expr,
                { onChange(g.copy(expr = it)) },
                when (g.kind) {
                    GraphKind.FUNCTION -> "np. x² − 3"
                    GraphKind.PARAMETRIC -> "np. cos(3t)"
                    GraphKind.POLAR -> "np. 1 + cos(θ)"
                    GraphKind.INEQUALITY -> "np. y > x² − 1"
                },
                Modifier.weight(1f),
            )
            if (g.kind == GraphKind.FUNCTION) {
                RawText(
                    "f′",
                    color = if (g.derivative) Palette.Bg else Palette.TextDim,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(buttonShape())
                        .background(if (g.derivative) Palette.Fg else Color.Transparent)
                        .semantics { contentDescription = tr("Pochodna") }
                        .clickable { onChange(g.copy(derivative = !g.derivative)) }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Rounded.Close, "Usuń funkcję", tint = Palette.TextFaint, modifier = Modifier.size(18.dp))
            }
        }
        if (g.kind == GraphKind.PARAMETRIC) {
            Row(Modifier.padding(start = 30.dp), verticalAlignment = Alignment.CenterVertically) {
                RawText("y$n(t) = ", color = Palette.TextDim, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                ExprField(g.expr2, { onChange(g.copy(expr2 = it)) }, "np. sin(2t)", Modifier.weight(1f))
            }
        }
        if (g.kind == GraphKind.PARAMETRIC || g.kind == GraphKind.POLAR) {
            Row(Modifier.padding(start = 30.dp), verticalAlignment = Alignment.CenterVertically) {
                RawText(if (g.kind == GraphKind.POLAR) "θ ∈ [ " else "t ∈ [ ", color = Palette.TextDim, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                ExprField(g.t0, { onChange(g.copy(t0 = it)) }, "0", Modifier.weight(1f))
                RawText(" ; ", color = Palette.TextDim, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                ExprField(g.t1, { onChange(g.copy(t1 = it)) }, "2π", Modifier.weight(1f))
                RawText(" ]", color = Palette.TextDim, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
            }
        }
        if (error != null && g.expr.isNotBlank()) Text(error, color = Palette.Error, fontSize = 12.sp, modifier = Modifier.padding(start = 34.dp, bottom = 4.dp))
    }
}

@Composable
private fun ExprField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = Palette.Fg, fontSize = 16.sp, fontFamily = FontFamily.Monospace),
        cursorBrush = SolidColor(Palette.Fg),
        modifier = modifier.padding(vertical = 8.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) Text(placeholder, color = Palette.TextFaint, fontFamily = FontFamily.Monospace, fontSize = 16.sp)
            inner()
        },
    )
}

private fun label(v: Double, style: NumberStyle): String = Fmt.number(Analysis.snap(if (abs(v) < 1e-12) 0.0 else v), style)

private fun findKeyPoints(plots: List<Plot>, a: Double, b: Double): List<KeyPoint> {
    val out = mutableListOf<KeyPoint>()
    plots.forEach { p ->
        Analysis.roots(p.f, a, b, 1200).forEach { out += KeyPoint(it, 0.0, PointKind.ROOT, p.index) }
        Analysis.extrema(p.f, a, b, 900).forEach { out += it.copy(owner = p.index) }
        if (0.0 in a..b) p.f(0.0).takeIf { !it.isNaN() && !it.isInfinite() }?.let { out += KeyPoint(0.0, Analysis.snap(it), PointKind.Y_AXIS, p.index) }
    }
    for (i in plots.indices) for (j in i + 1 until plots.size) {
        val f = plots[i].f
        val g = plots[j].f
        Analysis.roots({ x -> f(x) - g(x) }, a, b, 1200).forEach { x ->
            out += KeyPoint(x, Analysis.snap(f(x)), PointKind.CROSS, plots[i].index, plots[j].index)
        }
    }
    return out
}
