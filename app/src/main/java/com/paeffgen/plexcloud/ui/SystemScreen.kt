package com.paeffgen.plexcloud.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paeffgen.plexcloud.R
import com.paeffgen.plexcloud.data.Usage
import com.paeffgen.plexcloud.domain.Format
import com.paeffgen.plexcloud.ui.theme.AccentPalette
import com.paeffgen.plexcloud.ui.theme.LocalPlex
import com.paeffgen.plexcloud.ui.theme.Mono
import com.paeffgen.plexcloud.ui.theme.MonoSmall

@Composable
fun SystemScreen(
    email: String,
    usage: Usage?,
    latencies: List<Long>,
    lastSync: Long,
    accent: Long,
    wifiOnly: Boolean,
    autoUpdate: Boolean,
    version: String,
    updateState: UpdateState,
    onAccent: (Long) -> Unit,
    onWifiOnly: (Boolean) -> Unit,
    onAutoUpdate: (Boolean) -> Unit,
    onCheckUpdate: () -> Unit,
    onSource: () -> Unit,
    onSignOut: () -> Unit,
    backup: @Composable () -> Unit = {},
) {
    val p = LocalPlex.current
    var custom by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp)) {
            Text("System", style = MaterialTheme.typography.headlineMedium, color = p.text)
            Text("${usage?.plan?.ifEmpty { null } ?: "Plexora"} · $email", style = MonoSmall, color = p.muted, maxLines = 1)
        }

        if (usage != null) {
            val limit = (usage.limit ?: 0L).coerceAtLeast(1)
            Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp).fillMaxWidth().plexCard(shape = RoundedCornerShape(18.dp)).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Ring(if (usage.limit == null) emptyList() else listOf((usage.used.toFloat() / limit).coerceIn(0f, 1f) to p.accent), 112.dp, 10.dp, p.track) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Format.gb(usage.used), fontFamily = Mono, fontSize = 18.sp, color = p.text)
                        Text(if (usage.limit != null) "von ${Format.gb(usage.limit)} GB" else "unbegrenzt", style = MaterialTheme.typography.labelSmall, color = p.muted)
                    }
                }
                Column(Modifier.padding(start = 18.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Downloads diesen Monat", style = MaterialTheme.typography.labelSmall, color = p.muted)
                        Text("${Format.gb(usage.downloaded)} / ${usage.downloadLimit?.let { Format.gb(it) } ?: "∞"} GB", fontFamily = Mono, fontSize = 14.sp, color = p.text)
                        SegmentBar(listOf((usage.downloaded.toFloat() / (usage.downloadLimit ?: Long.MAX_VALUE).coerceAtLeast(1)) to p.blue), height = 4.dp)
                    }
                    Column {
                        Text("Größte Datei erlaubt", style = MaterialTheme.typography.labelSmall, color = p.muted)
                        Text(Format.bytes(usage.maxFile), fontFamily = Mono, fontSize = 14.sp, color = p.text)
                    }
                }
            }
        }

        SectionLabel("Verbindung")
        val avg = if (latencies.isEmpty()) null else latencies.takeLast(5).average().toLong()
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBox("Region", "FRA", p.text, Modifier.weight(1f))
            StatBox("Latenz", avg?.let { "$it ms" } ?: "—", p.accent, Modifier.weight(1f))
            StatBox("Sync", Format.ago(lastSync), p.text, Modifier.weight(1f))
        }
        Sparkline(latencies, Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp).fillMaxWidth().height(44.dp))

        backup()

        SectionLabel("Akzentfarbe  ${Format.hex(accent)}")
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AccentPalette.forEach { (_, argb) ->
                val sel = argb == accent
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                        .border(2.dp, if (sel) p.text else Color.Transparent, RoundedCornerShape(14.dp))
                        .selectable(selected = sel, role = Role.RadioButton, onClick = { onAccent(argb) }),
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(Color(argb.toInt()))) }
            }
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, Color(0xFF3A4766), RoundedCornerShape(14.dp)).clickable { custom = true },
                contentAlignment = Alignment.Center,
            ) { Text("+", fontSize = 20.sp, color = p.textSoft) }
        }

        SectionLabel("App")
        Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().plexCard(shape = RoundedCornerShape(14.dp))) {
            SettingRow("Version", "$version · ${if (android.os.Build.SUPPORTED_ABIS.isNotEmpty()) android.os.Build.SUPPORTED_ABIS[0] else ""}", onClick = onCheckUpdate) {
                Text(
                    when (updateState) {
                        UpdateState.Checking -> "sucht …"
                        UpdateState.UpToDate -> "aktuell"
                        is UpdateState.Available -> "${updateState.info.version} bereit"
                        else -> "prüfen"
                    },
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = p.accent,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(p.accentSoft).padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            HorizontalDivider(color = p.borderSoft)
            SettingRow("Nur im WLAN hochladen", "Mobile Daten sparen") { PlexSwitch(wifiOnly, onWifiOnly) }
            HorizontalDivider(color = p.borderSoft)
            SettingRow("Automatisch nach Updates suchen", "Einmal am Tag über GitHub-Releases") { PlexSwitch(autoUpdate, onAutoUpdate) }
            HorizontalDivider(color = p.borderSoft)
            SettingRow("Quellcode", "github.com/peter1965p/PlexCloud", onClick = onSource) { PIcon(R.drawable.ic_external, null, p.muted, 16.dp) }
        }

        OutlineAction("Abmelden", p.danger, onSignOut, Modifier.padding(20.dp).fillMaxWidth())
    }

    if (custom) CustomAccentDialog(accent, onDismiss = { custom = false }, onPick = { onAccent(it); custom = false })
}

@Composable
private fun StatBox(label: String, value: String, color: Color, modifier: Modifier) {
    val p = LocalPlex.current
    Column(modifier.plexCard(low = true, shape = RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = p.muted)
        Text(value, fontFamily = Mono, fontSize = 15.sp, color = color, maxLines = 1)
    }
}

/** Antwortzeiten der letzten Aufrufe; Ausreißer (doppelter Median) in Gelb */
@Composable
private fun Sparkline(values: List<Long>, modifier: Modifier) {
    val p = LocalPlex.current
    Box(modifier.plexCard(low = true, shape = RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) {
        if (values.isEmpty()) { Text("noch keine Messwerte", style = MonoSmall, color = p.muted, modifier = Modifier.align(Alignment.Center)); return@Box }
        Canvas(Modifier.fillMaxSize()) {
            val n = 30
            val gap = 3.dp.toPx()
            val w = (size.width - gap * (n - 1)) / n
            val max = values.max().coerceAtLeast(1)
            val median = values.sorted()[values.size / 2]
            values.takeLast(n).forEachIndexed { i, v ->
                val h = (v.toFloat() / max * size.height).coerceAtLeast(2.dp.toPx())
                drawRoundRect(if (v > median * 2) p.folder else p.accentDim, Offset(i * (w + gap), size.height - h), Size(w, h), CornerRadius(2.dp.toPx()))
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, sub: String, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit) {
    val p = LocalPlex.current
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = p.text)
            Text(sub, style = MonoSmall, color = p.muted, maxLines = 1)
        }
        Spacer(Modifier.size(12.dp))
        trailing()
    }
}

@Composable
fun PlexSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPlex.current
    Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = p.accent, checkedThumbColor = p.onAccent, uncheckedTrackColor = p.track, uncheckedBorderColor = p.border))
}

/** Eigene Farbe: Farbton und Helligkeit wählen, Vorschau mit Hex-Code */
@Composable
private fun CustomAccentDialog(current: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val p = LocalPlex.current
    val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(current.toInt(), it) }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1].coerceIn(0.35f, 1f)) }
    var value by remember { mutableFloatStateOf(hsv[2].coerceIn(0.6f, 1f)) }
    val color = Color.hsv(hue, sat, value)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        title = { Text("Eigene Akzentfarbe") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(color))
                    Text(Format.hex(color.toArgb().toLong()), fontFamily = Mono, color = p.text, modifier = Modifier.padding(start = 12.dp))
                }
                Text("Farbton", style = MaterialTheme.typography.labelSmall, color = p.muted)
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(androidx.compose.ui.graphics.Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, sat, value) })))
                Slider(hue, { hue = it }, valueRange = 0f..359f, colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent))
                Text("Sättigung", style = MaterialTheme.typography.labelSmall, color = p.muted)
                Slider(sat, { sat = it }, valueRange = 0.35f..1f, colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color))
                Text("Helligkeit", style = MaterialTheme.typography.labelSmall, color = p.muted)
                Slider(value, { value = it }, valueRange = 0.6f..1f, colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color))
            }
        },
        confirmButton = { TextButton(onClick = { onPick(color.toArgb().toLong() and 0xFFFFFFFFL) }) { Text("Übernehmen", color = color) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen", color = p.textSoft) } },
    )
}
