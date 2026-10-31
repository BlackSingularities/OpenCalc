package com.blacksingularities.opencalc.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Functions
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opencalc.data.HistoryItem
import com.blacksingularities.opencalc.data.KeyLayout
import com.blacksingularities.opencalc.engine.AngleMode
import com.blacksingularities.opencalc.engine.Constants
import com.blacksingularities.opencalc.engine.Fmt
import com.blacksingularities.opencalc.engine.Fraction
import com.blacksingularities.opencalc.engine.Functions
import com.blacksingularities.opencalc.engine.NumMode
import com.blacksingularities.opencalc.ui.theme.Palette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** Dodatkowa funkcja klawisza wywoływana przesunięciem palca. */
private data class Alt(val label: String, val action: () -> Unit)

/** Klawisz: etykieta, tekst wstawiany (albo akcja), wygląd; [up]/[down] – funkcje pod gestem przesunięcia. */
private data class Key(
    val label: String,
    val kind: KeyKind = KeyKind.NUM,
    val long: (() -> Unit)? = null,
    val up: Alt? = null,
    val down: Alt? = null,
    val action: () -> Unit,
)

private enum class KeyKind { NUM, FN, OP, EQ, CLEAR }

@Composable
fun CalcScreen(vm: AppViewModel) {
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showFunctions by rememberSaveable { mutableStateOf(false) }
    var showStore by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val sciOpen = vm.settings.sciOpen
        val swipe = vm.settings.keyLayout == KeyLayout.SWIPE
        val dense = maxWidth < 380.dp
        // Paski narzędzi i odstępy są stałe, więc klawiatury dobieramy z tego, co naprawdę
        // zostaje na ekranie — inaczej przy otwartych funkcjach naukowych wyświetlacz
        // schodzi do zera i nie widać ani wyrażenia, ani górnej krawędzi cyfr.
        val toolbarH = if (dense) 74.dp else 82.dp
        if (landscape) {
            val avail = maxHeight - 16.dp - toolbarH
            val keyH = ((avail - 72.dp - 20.dp) / 8.8f).coerceIn(32.dp, 64.dp)
            Row(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1.1f).fillMaxHeight()) {
                    Display(vm, Modifier.weight(1f).heightIn(min = 64.dp), scale = 0.8f)
                    Toolbar(vm, { showHistory = true }, { showFunctions = true }, { showStore = true }, ctx, compact = true, dense = dense)
                    if (swipe) SwipeSciPad(vm, (keyH * 0.9f).coerceAtLeast(34.dp)) else SciPad(vm, (keyH * 0.62f).coerceAtLeast(26.dp))
                }
                Column(Modifier.weight(0.9f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    BasicPad(vm, keyH, swipe)
                }
            }
        } else {
            val avail = maxHeight - toolbarH - 14.dp
            val displayMin = if (sciOpen) 104.dp else 148.dp
            // Wiersze: 5 podstawowych + 0,7 na backspace, a klawisze naukowe mają 0,62 wysokości
            // podstawowego klawisza w 5 wierszach; stałe to odstępy między wierszami.
            // W trybie przesuwanym zamiast pięciu niskich wierszy są dwa wyższe (0,72), bo każdy
            // klawisz mieści trzy podpisy.
            val sciRows = if (swipe) 2 else 5
            val sciRatio = if (swipe) 0.72f else 0.62f
            val rowsFactor = if (sciOpen) 5.7f + sciRows * sciRatio else 5.7f
            val gaps = if (sciOpen) 40.dp + 6.dp * sciRows else 40.dp
            val basicH = ((avail - displayMin - gaps) / rowsFactor).coerceIn(40.dp, 88.dp)
            val sciH = (basicH * sciRatio).coerceAtLeast(if (swipe) 40.dp else 28.dp)
            val padsH = basicH * 5.7f + gaps + (if (sciOpen) sciH * sciRows else 0.dp)
            val displayH = (avail - padsH).coerceAtLeast(56.dp)
            // Na bardzo niskich ekranach nawet najmniejsze klawisze mogą nie zmieścić się
            // razem z wyświetlaczem – wtedy całość daje się przewinąć, nic nie jest ucięte.
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
            ) {
                Display(vm, Modifier.height(displayH), scale = (displayH / 160.dp).coerceIn(0.6f, 1f))
                Toolbar(vm, { showHistory = true }, { showFunctions = true }, { showStore = true }, ctx, compact = false, dense = dense)
                AnimatedVisibility(sciOpen) { if (swipe) SwipeSciPad(vm, sciH) else SciPad(vm, sciH) }
                Spacer(Modifier.height(6.dp))
                BasicPad(vm, basicH, swipe)
                Spacer(Modifier.height(4.dp))
            }
        }
    }

    if (showHistory) HistorySheet(vm) { showHistory = false }
    if (showFunctions) FunctionsDialog(vm) { showFunctions = false }
    if (showStore) StoreDialog(vm) { showStore = false }
}

@Composable
private fun Display(vm: AppViewModel, modifier: Modifier, scale: Float = 1f) {
    val comma = vm.settings.style.decimalComma
    val shown = Fmt.displayExpr(vm.expr, comma)
    Column(
        modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
        horizontalAlignment = Alignment.End,
    ) {
        if (vm.evaluated && vm.lastExpr != null) {
            Text(
                Fmt.displayExpr(vm.lastExpr!!, comma) + " =",
                color = Palette.TextDim,
                fontSize = 19.sp * scale,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { vm.setExpression(vm.lastExpr!!) },
            )
        }
        val shownLength = if (vm.evaluated && vm.lastValue != null) vm.format(vm.lastValue!!).length else shown.length
        val size: TextUnit = when {
            shownLength < 10 -> 46.sp
            shownLength < 14 -> 38.sp
            shownLength < 19 -> 31.sp
            shownLength < 27 -> 26.sp
            else -> 22.sp
        } * scale
        // Liczba trzyma się dolnej krawędzi, a wolne miejsce zostaje nad nią; wiersz jest
        // wyraźnie wyższy od znaków, inaczej cyfry obcinają się u góry.
        Box(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState(), reverseScrolling = true),
            contentAlignment = Alignment.BottomEnd,
        ) {
            if (vm.evaluated && vm.lastValue != null) {
                Text(
                    vm.format(vm.lastValue!!),
                    color = Color.White,
                    fontSize = size,
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.End,
                    lineHeight = size * 1.32f,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                ExpressionText(vm, shown.ifEmpty { "" }, size)
            }
        }
        val err = vm.error
        Box(Modifier.fillMaxWidth().heightIn(min = 32.dp * scale), contentAlignment = Alignment.CenterEnd) {
            when {
                err != null -> Text(err, color = Palette.Error, fontSize = 17.sp * scale, maxLines = 1, overflow = TextOverflow.Ellipsis)
                vm.preview != null -> Text("= " + vm.preview, color = Palette.TextDim, fontSize = 23.sp * scale, maxLines = 1, overflow = TextOverflow.Ellipsis)
                vm.evaluated && vm.lastValue != null -> AltFormats(vm, vm.lastValue!!)
            }
        }
    }
}

/** Wyrażenie z migającym kursorem; dotknięcie ustawia kursor w miejscu palca. */
@Composable
private fun ExpressionText(vm: AppViewModel, shown: String, size: TextUnit) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val blink by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(530), RepeatMode.Reverse),
        label = "blink",
    )
    Text(
        shown.ifEmpty { "0" },
        color = if (shown.isEmpty()) Palette.TextFaint else Color.White,
        fontSize = size,
        fontWeight = FontWeight.Light,
        textAlign = TextAlign.End,
        lineHeight = size * 1.32f,
        onTextLayout = { layout = it },
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(shown) {
                detectTapGestures { pos ->
                    val l = layout ?: return@detectTapGestures
                    if (shown.isNotEmpty()) vm.setCursorAt(l.getOffsetForPosition(pos))
                }
            }
            .drawWithContent {
                drawContent()
                val l = layout ?: return@drawWithContent
                val i = if (shown.isEmpty()) 0 else vm.cursor.coerceIn(0, shown.length)
                val rect = l.getCursorRect(i)
                val x = if (shown.isEmpty()) size.toPx() * 0.6f + rect.left else rect.left
                drawLine(
                    Color.White.copy(alpha = blink),
                    Offset(if (shown.isEmpty()) this.size.width - 2.dp.toPx() - (this.size.width - x).coerceAtLeast(0f) * 0f else x, rect.top + 4.dp.toPx()),
                    Offset(if (shown.isEmpty()) this.size.width - 2.dp.toPx() else x, rect.bottom - 4.dp.toPx()),
                    strokeWidth = 2.dp.toPx(),
                )
            },
    )
}

/** Inne zapisy wyniku: ułamek, notacja naukowa, systemy liczbowe, stopnie-minuty-sekundy. */
@Composable
private fun AltFormats(vm: AppViewModel, v: Double) {
    val style = vm.settings.style
    val items = remember(v, style) {
        buildList {
            val isInt = v == Math.rint(v) && abs(v) < 9.0e15
            if (!isInt) Fraction.of(v)?.let { f ->
                add("Ułamek" to f.toString())
                if (abs(f.num) > f.den) add("Mieszany" to f.mixed())
            }
            if (style.mode != NumMode.SCI) add("Naukowy" to Fmt.number(v, style.copy(mode = NumMode.SCI)))
            if (abs(v) >= 1000 || abs(v) < 0.01) add("Inżynierski" to Fmt.number(v, style.copy(mode = NumMode.ENG)))
            if (isInt) {
                val l = v.toLong()
                add("HEX" to (if (l < 0) "−" else "") + java.lang.Long.toHexString(abs(l)).uppercase())
                if (abs(l) < 1L shl 32) add("BIN" to (if (l < 0) "−" else "") + java.lang.Long.toBinaryString(abs(l)))
                add("OCT" to (if (l < 0) "−" else "") + java.lang.Long.toOctalString(abs(l)))
            } else {
                add("° ′ ″" to dms(v))
            }
        }
    }
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) {
        items.forEach { (label, text) ->
            Row(
                Modifier
                    .clip(buttonShape())
                    .background(Palette.PanelHigh)
                    .clickable {
                        copyText(ctx, text)
                        vm.message = "Skopiowano: $text"
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("$label ", color = Palette.TextFaint, fontSize = 12.sp)
                Text(text, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
            }
        }
    }
}

fun dms(v: Double): String {
    val sign = if (v < 0) "−" else ""
    var a = abs(v)
    var d = a.toLong()
    a = (a - d) * 60
    var m = a.toLong()
    var s = (a - m) * 60
    if (s >= 59.995) {
        s = 0.0
        m++
    }
    if (m >= 60) {
        m = 0
        d++
    }
    return "$sign$d° $m′ ${"%.2f".format(Locale.US, s).replace('.', ',')}″"
}

@Composable
private fun Toolbar(vm: AppViewModel, onHistory: () -> Unit, onFunctions: () -> Unit, onStore: () -> Unit, ctx: android.content.Context, compact: Boolean, dense: Boolean = false) {
    val s = vm.settings
    val iconSize = if (dense) 34.dp else 40.dp
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            when (s.angle) {
                AngleMode.DEG -> "DEG"
                AngleMode.RAD -> "RAD"
                AngleMode.GRAD -> "GRAD"
            },
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier
                .clip(buttonShape())
                .background(Color.White)
                .clickable {
                    val next = AngleMode.entries[(s.angle.ordinal + 1) % AngleMode.entries.size]
                    vm.updateSettings(s.copy(angle = next))
                }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        if (vm.memory != 0.0) {
            Text("M", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(start = 10.dp))
        }
        Spacer(Modifier.weight(1f))
        ToolIcon(Icons.Rounded.History, "Historia", iconSize, onHistory)
        ToolIcon(Icons.Rounded.Functions, "Funkcje i stałe", iconSize, onFunctions)
        ToolIcon(Icons.Rounded.ContentCopy, "Kopiuj wynik", iconSize) {
            val text = vm.lastValue?.takeIf { vm.evaluated }?.let { vm.format(it) } ?: vm.preview ?: Fmt.displayExpr(vm.expr, s.style.decimalComma)
            copyText(ctx, text.replace(" ", ""))
            vm.message = "Skopiowano: $text"
        }
        ToolIcon(Icons.Rounded.ContentPaste, "Wklej", iconSize) {
            val t = pasteText(ctx)?.trim()
            if (t.isNullOrEmpty()) vm.message = "Schowek jest pusty" else {
                // Z przecinkiem dziesiętnym zamieniamy „1,5” na „1.5”, a średnik na separator argumentów.
                val clean = if (s.style.decimalComma) t.replace(" ", "").replace(",", ".").replace(";", ",") else t
                vm.insert(clean)
            }
        }
        ToolIcon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Kursor w lewo", iconSize) { vm.moveCursor(-1) }
        ToolIcon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Kursor w prawo", iconSize) { vm.moveCursor(1) }
        if (!compact) {
            ToolIcon(if (s.sciOpen) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess, if (s.sciOpen) "Ukryj funkcje naukowe" else "Pokaż funkcje naukowe", iconSize) {
                vm.updateSettings(s.copy(sciOpen = !s.sciOpen))
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            "MC" to { vm.memoryClear() },
            "MR" to { vm.memoryRecall() },
            "M+" to { vm.memoryAdd(1) },
            "M−" to { vm.memoryAdd(-1) },
            "→ zm." to onStore,
        ).forEach { (label, action) ->
            Text(
                label,
                color = Palette.TextDim,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(buttonShape())
                    .clickable(onClick = action)
                    .padding(vertical = if (dense) 4.dp else 6.dp),
            )
        }
    }
}

@Composable
private fun ToolIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, size: Dp = 40.dp, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(size)) {
        Icon(icon, desc, tint = Palette.TextDim, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
private fun SciPad(vm: AppViewModel, keyH: Dp) {
    val s = vm.second
    val h = vm.hyp
    fun trig(base: String) = when {
        s && h -> "a${base}h"
        s -> "a$base"
        h -> "${base}h"
        else -> base
    }
    fun trigLabel(base: String) = when {
        s && h -> "a${base}h"
        s -> "$base⁻¹"
        h -> "${base}h"
        else -> base
    }
    fun after(action: () -> Unit): () -> Unit = {
        action()
        vm.second = false
    }
    val rows = listOf(
        listOf(
            Key(if (s) "2nd ●" else "2nd", KeyKind.FN) { vm.second = !vm.second },
            Key(if (h) "hyp ●" else "hyp", KeyKind.FN) { vm.hyp = !vm.hyp },
            Key("(", KeyKind.FN) { vm.insert("(") },
            Key(")", KeyKind.FN) { vm.insert(")") },
            Key(if (s) "rand" else "Ans", KeyKind.FN, action = after { if (s) vm.insert("rand()") else vm.insert("Ans") }),
        ),
        listOf("sin", "cos", "tan").map { b -> Key(trigLabel(b), KeyKind.FN, action = after { vm.insertFunction(trig(b)) }) } + listOf(
            Key(if (s) "eˣ" else "ln", KeyKind.FN, action = after { if (s) vm.insert("e^(") else vm.insertFunction("ln") }),
            Key(if (s) "10ˣ" else "log", KeyKind.FN, action = after { if (s) vm.insert("10^(") else vm.insertFunction("log") }),
        ),
        listOf(
            Key(if (s) "x³" else "x²", KeyKind.FN, action = after { vm.insert(if (s) "³" else "²") }),
            Key(if (s) "ʸ√x" else "xʸ", KeyKind.FN, action = after { if (s) vm.insertFunction("root") else vm.insert("^") }),
            Key(if (s) "∛" else "√", KeyKind.FN, action = after { vm.insert(if (s) "∛(" else "√(") }),
            Key("1/x", KeyKind.FN) { vm.insert("⁻¹") },
            Key("x!", KeyKind.FN) { vm.insert("!") },
        ),
        listOf(
            Key(if (s) "τ" else "π", KeyKind.FN, action = after { vm.insert(if (s) "τ" else "π") }),
            Key(if (s) "φ" else "e", KeyKind.FN, action = after { vm.insert(if (s) "φ" else "e") }),
            Key("|x|", KeyKind.FN) { vm.insertFunction("abs") },
            Key("mod", KeyKind.FN) { vm.insert("mod") },
            Key("EE", KeyKind.FN) { vm.insert("E") },
        ),
        listOf(
            Key("nCr", KeyKind.FN) { vm.insertFunction("nCr") },
            Key("nPr", KeyKind.FN) { vm.insertFunction("nPr") },
            Key("°", KeyKind.FN) { vm.insert("°") },
            Key(if (s) "gcd" else ",", KeyKind.FN, action = after { if (s) vm.insertFunction("gcd") else vm.insert(",") }),
            Key(if (s) "lcm" else "%", KeyKind.FN, action = after { if (s) vm.insertFunction("lcm") else vm.insert("%") }),
        ),
    )
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { k -> KeyButton(vm, k, Modifier.weight(1f).height(keyH), fontSize = (keyH.value * 0.38f).coerceIn(11f, 15f).sp) }
            }
        }
    }
}

/**
 * Klawisze naukowe „wielofunkcyjne”: dotknięcie wpisuje funkcję główną, przesunięcie
 * w górę – podpis nad nią, w dół – podpis pod nią. Dwa wiersze zastępują pięć wierszy
 * panelu, a przełączniki 2nd i hyp nie są potrzebne.
 */
@Composable
private fun SwipeSciPad(vm: AppViewModel, keyH: Dp) {
    fun fn(name: String): () -> Unit = { vm.insertFunction(name) }
    fun ins(text: String): () -> Unit = { vm.insert(text) }
    fun trig(b: String) = Key(b, KeyKind.FN, up = Alt("$b⁻¹", fn("a$b")), down = Alt("${b}h", fn("${b}h")), action = fn(b))
    val rows = listOf(
        listOf(
            trig("sin"), trig("cos"), trig("tan"),
            Key("ln", KeyKind.FN, up = Alt("eˣ", ins("e^(")), down = Alt("log₂", fn("log2")), action = fn("ln")),
            Key("log", KeyKind.FN, up = Alt("10ˣ", ins("10^(")), down = Alt("Ans", ins("Ans")), action = fn("log")),
        ),
        listOf(
            Key("√", KeyKind.FN, up = Alt("∛", ins("∛(")), down = Alt("ʸ√x", fn("root")), action = ins("√(")),
            Key("xʸ", KeyKind.FN, up = Alt("x²", ins("²")), down = Alt("x³", ins("³")), action = ins("^")),
            Key("π", KeyKind.FN, up = Alt("e", ins("e")), down = Alt("φ", ins("φ")), action = ins("π")),
            Key("x!", KeyKind.FN, up = Alt("nCr", fn("nCr")), down = Alt("nPr", fn("nPr")), action = ins("!")),
            Key("1/x", KeyKind.FN, up = Alt("|x|", fn("abs")), down = Alt("mod", ins("mod")), action = ins("⁻¹")),
        ),
    )
    // Przełączniki z panelu nie mogą zostać włączone po zmianie trybu.
    LaunchedEffect(Unit) {
        vm.second = false
        vm.hyp = false
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { k -> KeyButton(vm, k, Modifier.weight(1f).height(keyH), fontSize = (keyH.value * 0.34f).coerceIn(13f, 18f).sp) }
            }
        }
    }
}

@Composable
private fun BasicPad(vm: AppViewModel, keyH: Dp, swipe: Boolean = false) {
    val comma = vm.settings.style.decimalComma
    val dot = if (comma) "," else "."
    fun digit(d: String) = Key(d) { vm.insert(d) }
    // W trybie przesuwanym operatory i kilka klawiszy dostają rzadziej używane funkcje.
    fun Key.alt(up: Alt?, down: Alt? = null) = if (swipe) copy(up = up, down = down) else this
    val rows = listOf(
        listOf(
            Key("AC", KeyKind.CLEAR) { vm.clear() },
            Key("( )", KeyKind.OP) { vm.parenSmart() }.alt(Alt("(") { vm.insert("(") }, Alt(")") { vm.insert(")") }),
            Key("%", KeyKind.OP) { vm.insert("%") }.alt(Alt("°") { vm.insert("°") }, Alt("rand") { vm.insert("rand()") }),
            Key("÷", KeyKind.OP) { vm.insert("÷") }.alt(Alt("gcd") { vm.insertFunction("gcd") }, Alt("lcm") { vm.insertFunction("lcm") }),
        ),
        listOf(digit("7"), digit("8"), digit("9"), Key("×", KeyKind.OP) { vm.insert("×") }.alt(Alt("x²") { vm.insert("²") }, Alt("xʸ") { vm.insert("^") })),
        listOf(digit("4"), digit("5"), digit("6"), Key("−", KeyKind.OP) { vm.insert("−") }.alt(Alt("√") { vm.insert("√(") }, Alt("|x|") { vm.insertFunction("abs") })),
        listOf(digit("1"), digit("2"), digit("3"), Key("+", KeyKind.OP) { vm.insert("+") }.alt(Alt("π") { vm.insert("π") }, Alt("e") { vm.insert("e") })),
        listOf(
            Key("±", KeyKind.NUM) { vm.negate() }.alt(Alt("Ans") { vm.insert("Ans") }, Alt("M") { vm.memoryRecall() }),
            digit("0").alt(Alt("EE") { vm.insert("E") }, Alt("000") { vm.insert("000") }),
            Key(dot) { vm.insert(".") }.alt(Alt(if (comma) ";" else ",") { vm.insert(",") }),
            Key("=", KeyKind.EQ) { vm.equals() },
        ),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEachIndexed { i, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k -> KeyButton(vm, k, Modifier.weight(1f).height(keyH), fontSize = (keyH.value * 0.42f).coerceIn(17f, 26f).sp) }
            }
            if (i == 0) Unit
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyButton(vm, Key("⌫", KeyKind.FN, long = { vm.clear() }) { vm.backspace() }, Modifier.weight(1f).height(keyH * 0.7f), fontSize = 20.sp, icon = true)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeyButton(vm: AppViewModel, key: Key, modifier: Modifier, fontSize: TextUnit, icon: Boolean = false) {
    val haptic = LocalHapticFeedback.current
    val (bg, fg) = when (key.kind) {
        KeyKind.NUM -> Palette.Key to Color.White
        KeyKind.FN -> Palette.KeyFn to Color.White
        KeyKind.OP -> Palette.KeyOp to Color.White
        KeyKind.EQ -> Color.White to Color.Black
        KeyKind.CLEAR -> Palette.KeyOp to Palette.Error
    }
    if (key.up != null || key.down != null) {
        SwipeKey(vm, key, modifier, bg, fg, fontSize)
        return
    }
    Box(
        modifier
            .clip(buttonShape())
            .background(bg)
            .combinedClickable(
                onClick = {
                    if (vm.settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    key.action()
                },
                onLongClick = key.long?.let { l ->
                    {
                        if (vm.settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        l()
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (icon) {
            Icon(Icons.AutoMirrored.Rounded.Backspace, "Usuń (przytrzymaj, by wyczyścić)", tint = fg, modifier = Modifier.size(24.dp))
        } else {
            Text(key.label, color = fg, fontSize = fontSize, fontWeight = if (key.kind == KeyKind.NUM) FontWeight.Normal else FontWeight.Medium, maxLines = 1)
        }
    }
}

/**
 * Klawisz wielofunkcyjny: dotknięcie – funkcja główna, przesunięcie w górę/dół – funkcja
 * z podpisu nad/pod nią. W trakcie gestu wybrana funkcja wskakuje na środek klawisza,
 * a wibracja potwierdza przekroczenie progu; odjechanie palcem daleko w bok anuluje.
 */
@Composable
private fun SwipeKey(vm: AppViewModel, key: Key, modifier: Modifier, bg: Color, fg: Color, fontSize: TextUnit) {
    val haptic = LocalHapticFeedback.current
    val current by rememberUpdatedState(key)
    val haptics by rememberUpdatedState(vm.settings.haptics)
    var pressed by remember { mutableStateOf(false) }
    var dir by remember { mutableIntStateOf(0) }
    val shownBg = when {
        !pressed -> bg
        key.kind == KeyKind.EQ -> Color(0xFFD0D0D0)
        else -> Color.White.copy(alpha = 0.22f)
    }
    BoxWithConstraints(
        modifier
            .clip(buttonShape())
            .background(shownBg)
            .semantics {
                contentDescription = listOfNotNull(key.label, key.up?.let { "w górę ${it.label}" }, key.down?.let { "w dół ${it.label}" }).joinToString(", ")
            }
            .pointerInput(Unit) {
                val threshold = 14.dp.toPx()
                awaitEachGesture {
                    val first = awaitFirstDown()
                    first.consume()
                    pressed = true
                    dir = 0
                    var total = Offset.Zero
                    var cancelled = false
                    while (true) {
                        val ch = awaitPointerEvent().changes.firstOrNull { it.id == first.id }
                        if (ch == null) {
                            cancelled = true
                            break
                        }
                        if (!ch.pressed) {
                            ch.consume()
                            break
                        }
                        total += ch.positionChange()
                        ch.consume()
                        val k = current
                        val vertical = abs(total.y) > abs(total.x) * 0.8f
                        val next = when {
                            vertical && total.y < -threshold && k.up != null -> -1
                            vertical && total.y > threshold && k.down != null -> 1
                            else -> 0
                        }
                        cancelled = next == 0 && abs(total.x) > size.width * 0.9f
                        if (next != dir) {
                            dir = next
                            if (next != 0 && haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                    if (!cancelled) {
                        val k = current
                        if (haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        when (dir) {
                            -1 -> k.up?.action?.invoke()
                            1 -> k.down?.action?.invoke()
                            else -> k.action()
                        }
                    }
                    pressed = false
                    dir = 0
                }
            },
    ) {
        val small = (maxHeight.value * 0.19f).coerceIn(9f, 12f).sp
        val faint = if (key.kind == KeyKind.EQ) Color.Black.copy(alpha = 0.45f) else Palette.TextFaint
        val main = when (dir) {
            -1 -> key.up?.label
            1 -> key.down?.label
            else -> null
        } ?: key.label
        key.up?.let {
            Text(
                it.label,
                color = faint,
                fontSize = small,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 3.dp).alpha(if (dir == -1) 0f else 1f),
            )
        }
        Text(
            main,
            color = fg,
            fontSize = if (dir != 0) fontSize * 1.08f else fontSize,
            fontWeight = if (dir != 0) FontWeight.Bold else if (key.kind == KeyKind.NUM) FontWeight.Normal else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.align(Alignment.Center),
        )
        key.down?.let {
            Text(
                it.label,
                color = faint,
                fontSize = small,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp).alpha(if (dir == 1) 0f else 1f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun HistorySheet(vm: AppViewModel, onClose: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val fmt = remember { SimpleDateFormat("d MMM, HH:mm", Locale.forLanguageTag("pl")) }
    var confirmClear by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = state, containerColor = Palette.PanelHigh) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Historia", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (vm.history.any { !it.pinned }) TextButton(onClick = { confirmClear = true }) { Text("Wyczyść", color = Palette.Error) }
        }
        Hint("    Dotknij działania, by je przywrócić, a wyniku – by wstawić liczbę. Przypięte wpisy nie znikają przy czyszczeniu.")
        if (vm.history.isEmpty()) {
            Text("Brak obliczeń", color = Palette.TextFaint, modifier = Modifier.fillMaxWidth().padding(40.dp), textAlign = TextAlign.Center)
        }
        val sorted = vm.history.sortedByDescending { it.pinned }
        LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            items(sorted, key = { it.time.toString() + it.expr }) { item ->
                HistoryRow(vm, item, fmt.format(Date(item.time)), onClose)
                HorizontalDivider(color = Palette.Line, modifier = Modifier.padding(horizontal = 20.dp))
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Wyczyścić historię?") },
            text = { Text("Przypięte obliczenia zostaną.") },
            confirmButton = { TextButton(onClick = { vm.clearHistory(); confirmClear = false }) { Text("Wyczyść", color = Palette.Error) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Anuluj") } },
            containerColor = Palette.PanelHigh,
        )
    }
}

@Composable
private fun HistoryRow(vm: AppViewModel, item: HistoryItem, time: String, onClose: () -> Unit) {
    val comma = vm.settings.style.decimalComma
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                Fmt.displayExpr(item.expr, comma),
                color = Palette.TextDim,
                fontSize = 15.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.useHistory(item, false); onClose() }.padding(8.dp, 2.dp),
            )
            Text(
                "= " + item.result,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.useHistory(item, true); onClose() }.padding(8.dp, 2.dp),
            )
            Text(time, color = Palette.TextFaint, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp))
        }
        IconButton(onClick = { vm.togglePin(item) }) {
            Icon(if (item.pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin, "Przypnij", tint = if (item.pinned) Color.White else Palette.TextFaint)
        }
        IconButton(onClick = { vm.deleteHistory(item) }) {
            Icon(Icons.Rounded.Delete, "Usuń", tint = Palette.TextFaint)
        }
    }
}

@Composable
private fun FunctionsDialog(vm: AppViewModel, onClose: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Palette.PanelHigh,
        title = { Text("Funkcje, stałe i zmienne") },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Szukaj…") },
                    singleLine = true,
                    colors = fieldColors(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                val q = query.trim().lowercase()
                val vars = buildList {
                    add(Triple("Ans", "Ostatni wynik", vm.ans))
                    add(Triple("M", "Pamięć", vm.memory))
                    vm.variables.toSortedMap().forEach { (k, v) -> add(Triple(k, "Zmienna", v)) }
                }.filter { q.isEmpty() || it.first.lowercase().contains(q) || it.second.lowercase().contains(q) }
                val consts = listOf("π" to "Pi", "e" to "Liczba Eulera", "φ" to "Złota proporcja", "τ" to "Tau = 2π")
                    .filter { q.isEmpty() || it.first.contains(q) || it.second.lowercase().contains(q) }
                val fns = Functions.all.filter { q.isEmpty() || it.name.lowercase().contains(q) || it.description.lowercase().contains(q) }
                LazyColumn(Modifier.height(420.dp)) {
                    if (vars.isNotEmpty()) item { GroupLabel("Zmienne") }
                    items(vars) { (name, desc, value) ->
                        FnRow(name, "$desc · ${vm.format(value)}") { vm.insert(name); onClose() }
                    }
                    if (consts.isNotEmpty()) item { GroupLabel("Stałe") }
                    items(consts) { (sym, desc) ->
                        FnRow(sym, "$desc · ${vm.format(Constants.values[sym] ?: 0.0)}") { vm.insert(sym); onClose() }
                    }
                    if (fns.isNotEmpty()) item { GroupLabel("Funkcje") }
                    items(fns) { f ->
                        FnRow(f.usage, f.description) {
                            if (f.maxArgs == 0) vm.insert(f.name + "()") else vm.insertFunction(f.name)
                            onClose()
                        }
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                        Hint("Składnia: mnożenie domyślne (2π, 3(x+1)), potęgi x², x^y, silnia n!, procenty 200+10%, notacja 1,5E−3, stopnie 30°, |x| i mod. Brakujące nawiasy zamykające są dopisywane automatycznie.")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Zamknij") } },
    )
}

@Composable
private fun GroupLabel(text: String) {
    Text(text.uppercase(), color = Palette.TextFaint, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
}

@Composable
private fun FnRow(name: String, desc: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(name, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 15.sp)
        Text(desc, color = Palette.TextDim, fontSize = 12.sp)
    }
}

@Composable
private fun StoreDialog(vm: AppViewModel, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Palette.PanelHigh,
        title = { Text("Zapisz wynik w zmiennej") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Hint("Zmienne A–F możesz potem wstawiać do działań przyciskiem funkcji (ƒ).")
                listOf("A", "B", "C", "D", "F").forEach { name ->
                    val current = vm.variables[name]
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Palette.KeyFn)
                            .clickable { vm.store(name); onClose() }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.width(32.dp))
                        Text(current?.let { vm.format(it) } ?: "pusta", color = if (current == null) Palette.TextFaint else Palette.TextDim, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Anuluj") } },
        dismissButton = if (vm.variables.isNotEmpty()) {
            { TextButton(onClick = { vm.clearVariables(); onClose() }) { Text("Wyczyść zmienne", color = Palette.Error) } }
        } else null,
    )
}
