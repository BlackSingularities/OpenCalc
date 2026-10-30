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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
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

/** Klawisz: etykieta, tekst wstawiany (albo akcja), wygląd. */
private data class Key(
    val label: String,
    val kind: KeyKind = KeyKind.NUM,
    val long: (() -> Unit)? = null,
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
        if (landscape) {
            val keyH = ((maxHeight - 24.dp) / 5).coerceAtMost(64.dp)
            Row(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1.1f).fillMaxHeight()) {
                    Display(vm, Modifier.weight(1f))
                    Toolbar(vm, { showHistory = true }, { showFunctions = true }, { showStore = true }, ctx, compact = true)
                    SciPad(vm, keyH * 0.62f)
                }
                Column(Modifier.weight(0.9f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    BasicPad(vm, keyH)
                }
            }
        } else {
            val basicH = ((maxHeight * if (sciOpen) 0.44f else 0.56f) / 5).coerceIn(46.dp, 88.dp)
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                Display(vm, Modifier.weight(1f))
                Toolbar(vm, { showHistory = true }, { showFunctions = true }, { showStore = true }, ctx, compact = false)
                AnimatedVisibility(sciOpen) { SciPad(vm, (basicH * 0.66f).coerceAtLeast(36.dp)) }
                Spacer(Modifier.height(6.dp))
                BasicPad(vm, basicH)
                Spacer(Modifier.height(4.dp))
            }
        }
    }

    if (showHistory) HistorySheet(vm) { showHistory = false }
    if (showFunctions) FunctionsDialog(vm) { showFunctions = false }
    if (showStore) StoreDialog(vm) { showStore = false }
}

@Composable
private fun Display(vm: AppViewModel, modifier: Modifier) {
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
                fontSize = 19.sp,
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
        }
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
        Box(Modifier.fillMaxWidth().heightIn(min = 32.dp), contentAlignment = Alignment.CenterEnd) {
            when {
                err != null -> Text(err, color = Palette.Error, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                vm.preview != null -> Text("= " + vm.preview, color = Palette.TextDim, fontSize = 23.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    .clip(RoundedCornerShape(50))
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
private fun Toolbar(vm: AppViewModel, onHistory: () -> Unit, onFunctions: () -> Unit, onStore: () -> Unit, ctx: android.content.Context, compact: Boolean) {
    val s = vm.settings
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
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
                .clip(RoundedCornerShape(50))
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
        ToolIcon(Icons.Rounded.History, "Historia", onHistory)
        ToolIcon(Icons.Rounded.Functions, "Funkcje i stałe", onFunctions)
        ToolIcon(Icons.Rounded.ContentCopy, "Kopiuj wynik") {
            val text = vm.lastValue?.takeIf { vm.evaluated }?.let { vm.format(it) } ?: vm.preview ?: Fmt.displayExpr(vm.expr, s.style.decimalComma)
            copyText(ctx, text.replace(" ", ""))
            vm.message = "Skopiowano: $text"
        }
        ToolIcon(Icons.Rounded.ContentPaste, "Wklej") {
            val t = pasteText(ctx)?.trim()
            if (t.isNullOrEmpty()) vm.message = "Schowek jest pusty" else {
                // Z przecinkiem dziesiętnym zamieniamy „1,5” na „1.5”, a średnik na separator argumentów.
                val clean = if (s.style.decimalComma) t.replace(" ", "").replace(",", ".").replace(";", ",") else t
                vm.insert(clean)
            }
        }
        ToolIcon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Kursor w lewo") { vm.moveCursor(-1) }
        ToolIcon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Kursor w prawo") { vm.moveCursor(1) }
        if (!compact) {
            ToolIcon(if (s.sciOpen) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess, if (s.sciOpen) "Ukryj funkcje naukowe" else "Pokaż funkcje naukowe") {
                vm.updateSettings(s.copy(sciOpen = !s.sciOpen))
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = action)
                    .padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ToolIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(icon, desc, tint = Palette.TextDim, modifier = Modifier.size(22.dp))
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
                row.forEach { k -> KeyButton(vm, k, Modifier.weight(1f).height(keyH), fontSize = 15.sp) }
            }
        }
    }
}

@Composable
private fun BasicPad(vm: AppViewModel, keyH: Dp) {
    val dot = if (vm.settings.style.decimalComma) "," else "."
    fun digit(d: String) = Key(d) { vm.insert(d) }
    val rows = listOf(
        listOf(Key("AC", KeyKind.CLEAR) { vm.clear() }, Key("( )", KeyKind.OP) { vm.parenSmart() }, Key("%", KeyKind.OP) { vm.insert("%") }, Key("÷", KeyKind.OP) { vm.insert("÷") }),
        listOf(digit("7"), digit("8"), digit("9"), Key("×", KeyKind.OP) { vm.insert("×") }),
        listOf(digit("4"), digit("5"), digit("6"), Key("−", KeyKind.OP) { vm.insert("−") }),
        listOf(digit("1"), digit("2"), digit("3"), Key("+", KeyKind.OP) { vm.insert("+") }),
        listOf(
            Key("±", KeyKind.NUM) { vm.negate() },
            digit("0"),
            Key(dot) { vm.insert(".") },
            Key("=", KeyKind.EQ) { vm.equals() },
        ),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEachIndexed { i, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k -> KeyButton(vm, k, Modifier.weight(1f).height(keyH), fontSize = 26.sp) }
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
    Box(
        modifier
            .clip(RoundedCornerShape(50))
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
