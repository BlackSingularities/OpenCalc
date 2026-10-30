package com.blacksingularities.opencalc.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opencalc.ui.theme.Palette

fun copyText(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("OpenCalc", text))
}

fun pasteText(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
}

/** Pole liczbowe: przecinek i kropka działają jako separator dziesiętny. */
fun parseNum(text: String): Double? =
    text.trim().replace(" ", "").replace(" ", "").replace(',', '.').replace('−', '-').toDoubleOrNull()

@Composable
fun NumField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    keyboard: KeyboardType = KeyboardType.Decimal,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        suffix = suffix?.let { { Text(it, color = Palette.TextDim) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = RoundedCornerShape(14.dp),
        colors = fieldColors(),
        modifier = modifier,
    )
}

@Composable
fun TextArea(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, minLines: Int = 3, mono: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        minLines = minLines,
        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default, fontSize = 16.sp, color = Color.White),
        shape = RoundedCornerShape(14.dp),
        colors = fieldColors(),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color.White,
    unfocusedBorderColor = Palette.Line,
    focusedLabelColor = Color.White,
    unfocusedLabelColor = Palette.TextDim,
    cursorColor = Color.White,
)

@Composable
fun Section(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Nagłówek grupy wersalikami nad półprzezroczystym panelem – jak w ustawieniach OpenPDF.
        if (title != null) Text(
            title.uppercase(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.4.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 3.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Palette.Group)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/** Wiersz wyniku: etykieta i wartość; dotknięcie kopiuje wartość. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ResultRow(label: String, value: String, strong: Boolean = false, onLongClick: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val vm = LocalVm.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                onClick = {
                    copyText(ctx, value.replace(" ", ""))
                    vm.message = "Skopiowano: $value"
                },
                onLongClick = onLongClick,
            )
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Palette.TextDim, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(
            value,
            color = Color.White,
            fontSize = if (strong) 20.sp else 15.sp,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
fun <T> ChipRow(items: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { item ->
            val sel = item == selected
            Text(
                label(item),
                color = if (sel) Color.Black else Color.White,
                fontSize = 13.sp,
                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (sel) Color.White else Color.White.copy(alpha = 0.07f))
                    .clickable { onSelect(item) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
fun PillButton(text: String, modifier: Modifier = Modifier, primary: Boolean = true, onClick: () -> Unit) {
    Text(
        text,
        color = if (primary) Color.Black else Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (primary) Color.White else Palette.KeyOp)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

@Composable
fun Hint(text: String) {
    Text(text, color = Palette.TextFaint, fontSize = 12.sp, lineHeight = 16.sp)
}

@Composable
fun ErrorText(text: String?) {
    if (text != null) Text(text, color = Palette.Error, fontSize = 13.sp)
}

@Composable
fun VSpace(h: Int) = Spacer(Modifier.height(h.dp))

/** Ikona w kółku z delikatnym tłem – wiersze list jak w OpenPDF. */
@Composable
fun RowIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color = Color.White) {
    androidx.compose.foundation.layout.Box(
        Modifier
            .size(32.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
    }
}
