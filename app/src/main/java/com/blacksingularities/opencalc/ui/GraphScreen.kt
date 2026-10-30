package com.blacksingularities.opencalc.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opencalc.data.GraphFn
import com.blacksingularities.opencalc.engine.AngleMode
import com.blacksingularities.opencalc.engine.Analysis
import com.blacksingularities.opencalc.engine.Func
import com.blacksingularities.opencalc.engine.KeyPoint
import com.blacksingularities.opencalc.engine.NumberStyle
import com.blacksingularities.opencalc.engine.PointKind
import com.blacksingularities.opencalc.engine.Fmt
import com.blacksingularities.opencalc.engine.niceStep
import com.blacksingularities.opencalc.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

private class Plot(val index: Int, val color: Color, val f: (Double) -> Double, val label: String)

@Composable
fun GraphScreen(vm: AppViewModel) {
    // Widok: środek i liczba jednostek na piksel (ta sama skala w obu osiach).
    var cx by rememberSaveable { mutableDoubleStateOf(0.0) }
    var cy by rememberSaveable { mutableDoubleStateOf(0.0) }
    var unit by rememberSaveable { mutableDoubleStateOf(0.02) }
    var analyze by rememberSaveable { mutableStateOf(false) }
    var trace by remember { mutableStateOf<Offset?>(null) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    val graphs = vm.graphs
    val numStyle = vm.settings.style.copy(digits = 6, grouping = false)

    // Funkcje parsowane raz na zmianę tekstu; błędy pokazujemy pod polem.
    val parsed = remember(graphs) {
        graphs.mapIndexed { i, g ->
            runCatching { Func.parse(g.expr.ifBlank { "0" }, AngleMode.RAD) }
                .fold({ it to null }, { null to (it.message ?: "Błąd") })
                .let { (fn, err) -> Triple(i, fn, err) }
        }
    }
    val plots = remember(parsed, graphs) {
        buildList {
            parsed.forEach { (i, fn, _) ->
                val g = graphs[i]
                if (fn == null || !g.visible || g.expr.isBlank()) return@forEach
                val color = Palette.Graph[g.color % Palette.Graph.size]
                add(Plot(i, color, { x -> fn(x) }, "f${i + 1}"))
                if (g.derivative) add(Plot(i, color.copy(alpha = 0.55f), { x -> Analysis.derivative({ fn(it) }, x) }, "f${i + 1}′"))
            }
        }
    }

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
            Canvas(
                Modifier
                    .fillMaxSize()
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

                // Wykresy: próbka co 2 px, przerwa przy asymptotach i poza dziedziną.
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

                keyPoints.forEach { k ->
                    val o = Offset(sx(k.x), sy(k.y))
                    drawCircle(Color.Black, 6.dp.toPx(), o)
                    drawCircle(Color.White, 4.dp.toPx(), o)
                }

                trace?.let { tap ->
                    val x = l + tap.x * unit
                    val best = plots.mapNotNull { p -> p.f(x).takeIf { !it.isNaN() && !it.isInfinite() }?.let { p to it } }
                        .minByOrNull { (_, y) -> abs(sy(y) - tap.y) }
                    drawLine(Color.White.copy(alpha = 0.4f), Offset(tap.x, 0f), Offset(tap.x, h), 1f)
                    if (best != null) {
                        val (p, y) = best
                        val o = Offset(tap.x, sy(y))
                        drawCircle(p.color, 6.dp.toPx(), o)
                        val text = "${p.label}(${label(x, numStyle)}) = ${label(y, numStyle)}"
                        val layout = measurer.measure(text, TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                        val bx = (o.x + 10).coerceAtMost(w - layout.size.width - 16f)
                        val by = (o.y - layout.size.height - 18).coerceIn(4f, h - layout.size.height - 12f)
                        drawRoundRect(Color(0xE6000000), Offset(bx - 8, by - 4), Size(layout.size.width + 16f, layout.size.height + 8f), androidx.compose.ui.geometry.CornerRadius(12f))
                        drawText(layout, topLeft = Offset(bx, by))
                    }
                }
            }
            Row(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
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
                .heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (analyze && keyPoints.isNotEmpty()) {
                Section("Punkty charakterystyczne") {
                    keyPoints.take(24).forEach { k ->
                        val who = if (k.kind == PointKind.CROSS) "f${k.owner + 1} ∩ f${k.other + 1}" else "f${k.owner + 1}"
                        ResultRow("${k.kind.label} · $who", "(${label(k.x, numStyle)}; ${label(k.y, numStyle)})")
                    }
                }
            }
            graphs.forEachIndexed { i, g ->
                FunctionRow(i, g, parsed.getOrNull(i)?.third, onChange = { ng -> vm.updateGraphs(graphs.toMutableList().also { it[i] = ng }) }) {
                    vm.updateGraphs(graphs.filterIndexed { j, _ -> j != i })
                }
            }
            if (graphs.size < 8) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Palette.Group)
                        .clickable {
                            val used = graphs.map { it.color }.toSet()
                            val color = (0 until Palette.Graph.size).firstOrNull { it !in used } ?: graphs.size
                            vm.updateGraphs(graphs + GraphFn("", color))
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Dodaj funkcję", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
            Hint("Zmienna to x, kąty w radianach. Przykłady: x^3 − 2x, sin(x)/x, √(4 − x²), |x| − 1, ln(x), floor(x), 2^x. Dotknięcie kropki ukrywa wykres, f′ rysuje pochodną.")
        }
    }
}

@Composable
private fun GlassButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, active: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .padding(2.dp)
            .size(38.dp)
            .clip(CircleShape)
            .background(if (active) Color.White else Color(0xB3000000))
            .border(1.dp, Palette.Line, CircleShape),
    ) {
        Icon(icon, desc, tint = if (active) Color.Black else Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun FunctionRow(i: Int, g: GraphFn, error: String?, onChange: (GraphFn) -> Unit, onDelete: () -> Unit) {
    val color = Palette.Graph[g.color % Palette.Graph.size]
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
                    .clickable { onChange(g.copy(visible = !g.visible)) },
            )
            Text("  f${i + 1}(x) = ", color = Palette.TextDim, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
            BasicTextField(
                value = g.expr,
                onValueChange = { onChange(g.copy(expr = it)) },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                decorationBox = { inner ->
                    if (g.expr.isEmpty()) Text("np. x² − 3", color = Palette.TextFaint, fontFamily = FontFamily.Monospace, fontSize = 16.sp)
                    inner()
                },
            )
            Text(
                "f′",
                color = if (g.derivative) Color.Black else Palette.TextDim,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (g.derivative) Color.White else Color.Transparent)
                    .clickable { onChange(g.copy(derivative = !g.derivative)) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Rounded.Close, "Usuń funkcję", tint = Palette.TextFaint, modifier = Modifier.size(18.dp))
            }
        }
        if (error != null && g.expr.isNotBlank()) Text(error, color = Palette.Error, fontSize = 12.sp, modifier = Modifier.padding(start = 34.dp, bottom = 4.dp))
    }
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
