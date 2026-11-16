package com.paeffgen.plexcloud.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.WorkInfo
import com.paeffgen.plexcloud.R
import com.paeffgen.plexcloud.domain.Format
import com.paeffgen.plexcloud.ui.theme.LocalPlex
import com.paeffgen.plexcloud.ui.theme.Mono
import com.paeffgen.plexcloud.ui.theme.MonoSmall
import com.paeffgen.plexcloud.upload.UploadRow
import java.util.UUID

@Composable
fun UploadsScreen(rows: List<UploadRow>, wifiOnly: Boolean, onCancel: (UUID) -> Unit, onClear: () -> Unit, onCancelBackup: () -> Unit = {}, onRetry: () -> Unit = {}, onCamera: () -> Unit) {
    val p = LocalPlex.current
    val active = rows.filter { !it.state.isFinished }
    val done = rows.count { it.state == WorkInfo.State.SUCCEEDED }
    // Läuft nichts mehr: Summe der fertigen Uploads zeigen statt „0 B“
    val shown = active.ifEmpty { rows.filter { it.state == WorkInfo.State.SUCCEEDED } }
    val total = shown.sumOf { it.size }
    val sent = shown.sumOf { it.done }
    val speed = active.filter { it.state == WorkInfo.State.RUNNING }.sumOf { it.speed }
    val pct = if (total > 0) sent.toFloat() / total else if (active.isEmpty() && rows.isNotEmpty()) 1f else 0f

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Uploads", style = MaterialTheme.typography.titleLarge, color = p.text)
                val errors = rows.count { it.state == WorkInfo.State.FAILED }
                Text("${active.size} aktiv · $done fertig${if (errors > 0) " · $errors Fehler" else ""} · ${if (wifiOnly) "nur WLAN" else "jedes Netz"}", style = MonoSmall, color = p.muted)
            }
            val backupPending = rows.count { it.backup && !it.state.isFinished }
            if (backupPending > 0) androidx.compose.material3.TextButton(onClick = onCancelBackup) { Text("Sicherung abbrechen ($backupPending)", color = p.danger) }
            val failed = rows.count { it.state == WorkInfo.State.FAILED }
            if (failed > 0) androidx.compose.material3.TextButton(onClick = onRetry) { Text("Erneut senden ($failed)", color = p.accent) }
            if (rows.any { it.state.isFinished }) IconButton(onClick = onClear) { PIcon(R.drawable.ic_trash, "Fertige aus der Liste nehmen", p.textSoft) }
        }

        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().plexCard().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Ring(listOf(pct to p.accent), 76.dp, 7.dp, p.track) {
                Text("${(pct * 100).toInt()}%", fontFamily = Mono, fontSize = 15.sp, color = p.text)
            }
            Column(Modifier.padding(start = 16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row { Stat("Tempo", if (speed > 0) Format.speed(speed) else "—", Modifier.weight(1f)); Stat("Restzeit", if (speed > 0) Format.eta((total - sent) / speed) else "—", Modifier.weight(1f)) }
                Row { Stat("Übertragen", Format.bytes(sent), Modifier.weight(1f)); Stat("Gesamt", Format.bytes(total), Modifier.weight(1f)) }
            }
        }

        SectionLabel("Warteschlange")
        if (rows.isEmpty()) {
            Message(R.drawable.ic_upload, "Noch nichts hochgeladen. Foto aufnehmen oder in einer anderen App „Teilen → PlexCloud“.", null, null)
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(rows, key = { it.id }) { UploadCard(it, onCancel) }
            }
        }

        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Button(
                onClick = onCamera, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
            ) {
                PIcon(R.drawable.ic_camera, null, p.onAccent, 18.dp)
                Text("  Aufnehmen", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    val p = LocalPlex.current
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = p.muted)
        Text(value, fontFamily = Mono, fontSize = 15.sp, color = p.text, maxLines = 1)
    }
}

@Composable
private fun UploadCard(r: UploadRow, onCancel: (UUID) -> Unit) {
    val p = LocalPlex.current
    val (status, color) = when (r.state) {
        WorkInfo.State.SUCCEEDED -> "fertig" to p.accent
        WorkInfo.State.FAILED -> "Fehler" to p.danger
        WorkInfo.State.CANCELLED -> "abgebrochen" to p.muted
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> "wartet" to p.muted
        WorkInfo.State.RUNNING -> "${if (r.size > 0) r.done * 100 / r.size else 0}%" to p.text
    }
    val meta = buildString {
        append(Format.bytes(r.size))
        when {
            r.state == WorkInfo.State.SUCCEEDED && r.sha256 != null -> append(" · SHA-256 ${r.sha256.take(4)}…${r.sha256.takeLast(4)}")
            r.state == WorkInfo.State.FAILED && r.error != null -> append(" · ${r.error}")
            r.state == WorkInfo.State.RUNNING -> { if (r.parts > 1) append(" · Teil ${r.part}/${r.parts}"); if (r.speed > 0) append(" · ${Format.speed(r.speed)}") }
            r.state == WorkInfo.State.ENQUEUED -> append(" · wartet auf Netz")
            else -> Unit
        }
    }
    Column(Modifier.fillMaxWidth().plexCard(low = true, shape = RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val kind = kindOfName(r.name)
            val tint = kindColor(kind)
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                PIcon(kindIcon(kind), null, tint, 18.dp)
            }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(r.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(meta, style = MonoSmall, color = if (r.state == WorkInfo.State.FAILED) p.danger else p.muted, maxLines = 2)
            }
            Text(status, fontFamily = Mono, fontSize = 12.sp, color = color, modifier = Modifier.padding(start = 8.dp))
            if (!r.state.isFinished) IconButton(onClick = { onCancel(r.id) }) { PIcon(R.drawable.ic_close, "Abbrechen", p.muted, 16.dp) }
        }
        LinearProgressIndicator(
            progress = { if (r.size > 0) (r.done.toFloat() / r.size).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = if (r.state == WorkInfo.State.SUCCEEDED) p.accentDim else p.accent,
            trackColor = p.track,
            drawStopIndicator = {},
        )
    }
}

@Composable
fun OutlineAction(text: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPlex.current
    OutlinedButton(onClick = onClick, modifier = modifier.height(48.dp), shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, p.border)) { Text(text, color = color, fontWeight = FontWeight.SemiBold) }
}

/** Art einer Datei nur aus dem Namen (die Upload-Liste kennt den Typ nicht) */
fun kindOfName(name: String): com.paeffgen.plexcloud.data.CloudItem.Kind = when (name.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg", "png", "heic", "heif", "webp", "gif" -> com.paeffgen.plexcloud.data.CloudItem.Kind.Photo
    "pdf" -> com.paeffgen.plexcloud.data.CloudItem.Kind.Pdf
    "mp4", "mov", "3gp", "mkv", "webm" -> com.paeffgen.plexcloud.data.CloudItem.Kind.Video
    else -> com.paeffgen.plexcloud.data.CloudItem.Kind.File
}
