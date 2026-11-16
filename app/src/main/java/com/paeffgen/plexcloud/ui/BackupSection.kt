package com.paeffgen.plexcloud.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paeffgen.plexcloud.R
import com.paeffgen.plexcloud.data.BackupFolder
import com.paeffgen.plexcloud.domain.Format
import com.paeffgen.plexcloud.ui.theme.LocalPlex
import com.paeffgen.plexcloud.ui.theme.MonoSmall

data class BackupUi(
    val photos: Boolean,
    val videos: Boolean,
    val folders: List<BackupFolder>,
    val lastRun: Long,
    val lastCount: Long,
    /** vom Energiesparen ausgenommen (sonst stoppt z. B. Samsung die Sicherung im Hintergrund) */
    val batteryOk: Boolean = true,
    /** Namen der gewählten Alben (leer = alle) */
    val albumNames: List<String> = emptyList(),
    /** Sicherungs-Uploads, die noch warten oder laufen */
    val pending: Int = 0,
)

/** Bereich „Sicherung“ unter „System“: Fotos/Videos automatisch, gewählte Ordner, Stand */
@Composable
fun BackupSection(
    ui: BackupUi,
    onPhotos: (on: Boolean) -> Unit,
    onVideos: (Boolean) -> Unit,
    onAddFolder: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    onNow: () -> Unit,
    onBattery: () -> Unit = {},
    onAlbums: () -> Unit = {},
    onCancel: () -> Unit = {},
) {
    val p = LocalPlex.current
    SectionLabel("Sicherung")
    Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().plexCard(shape = RoundedCornerShape(14.dp))) {
        BackupRow("Fotos automatisch sichern", "Neue Fotos landen in Handy-Sicherung/<Gerät>/Fotos") { PlexSwitch(ui.photos, onPhotos) }
        if (ui.photos) {
            HorizontalDivider(color = p.borderSoft)
            BackupRow("Alben", ui.albumNames.ifEmpty { listOf("alle") }.joinToString(", "), onClick = onAlbums) {
                Text("ändern", style = MaterialTheme.typography.labelMedium, color = p.accent)
            }
            HorizontalDivider(color = p.borderSoft)
            BackupRow("Auch Videos", "Videos sind groß: am besten nur im WLAN") { PlexSwitch(ui.videos, onVideos) }
        }
        HorizontalDivider(color = p.borderSoft)
        ui.folders.forEach { f ->
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                PIcon(R.drawable.ic_folder, null, p.folder, 18.dp)
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(folderLabel(f.uri), style = MaterialTheme.typography.bodyLarge, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (f.sinceMs == 0L) "wird zum ersten Mal gesichert" else "gesichert bis ${Format.ago(f.sinceMs)}", style = MonoSmall, color = p.muted)
                }
                IconButton(onClick = { onRemoveFolder(f.uri) }) { PIcon(R.drawable.ic_close, "Ordner nicht mehr sichern", p.muted, 16.dp) }
            }
            HorizontalDivider(color = p.borderSoft)
        }
        Row(Modifier.fillMaxWidth().clickable(onClick = onAddFolder).padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon(R.drawable.ic_folder_plus, null, p.accent, 18.dp)
            Text("  Ordner sichern (z. B. Download, Dokumente)", style = MaterialTheme.typography.bodyLarge, color = p.accent)
        }
        if (ui.pending > 0) {
            HorizontalDivider(color = p.borderSoft)
            BackupRow("${ui.pending} Dateien in der Warteschlange", "Tippen: Sicherung abbrechen, ab jetzt nur Neues", onClick = onCancel) {
                PIcon(R.drawable.ic_close, null, p.danger, 18.dp)
            }
        }
        if ((ui.photos || ui.folders.isNotEmpty()) && !ui.batteryOk) {
            HorizontalDivider(color = p.borderSoft)
            BackupRow("Energiesparen bremst die Sicherung", "Tippen: PlexCloud im Hintergrund laufen lassen", onClick = onBattery) {
                Text("!", style = MaterialTheme.typography.titleMedium, color = p.folder)
            }
        }
        if (ui.photos || ui.folders.isNotEmpty()) {
            HorizontalDivider(color = p.borderSoft)
            BackupRow("Jetzt sichern", if (ui.lastRun > 0) "letzter Lauf ${Format.ago(ui.lastRun)} · ${ui.lastCount} neu" else "noch nicht gelaufen", onClick = onNow) {
                PIcon(R.drawable.ic_refresh, null, p.accent, 18.dp)
            }
        }
    }
}

@Composable
private fun BackupRow(title: String, sub: String, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit) {
    val p = LocalPlex.current
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = p.text)
            Text(sub, style = MonoSmall, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

/** „primary:Download/Rechnungen“ → „Download/Rechnungen“ */
fun folderLabel(treeUri: String): String {
    val doc = Uri.decode(treeUri.substringAfterLast("/tree/", treeUri))
    return doc.substringAfter(':', doc).ifEmpty { "Interner Speicher" }
}

/**
 * Alben wählen. Beim Einschalten (enabling) zusätzlich: nur neue oder auch vorhandene Fotos.
 * Vorauswahl beim ersten Mal: nur „Camera“ (eigene Fotos), nicht WhatsApp, Screenshots & Co.
 */
@Composable
fun AlbumDialog(
    albums: List<com.paeffgen.plexcloud.backup.Album>,
    selected: Set<String>,
    enabling: Boolean,
    onConfirm: (ids: Set<String>, includeExisting: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalPlex.current
    val start = remember(albums) {
        when {
            selected.isNotEmpty() -> selected
            enabling -> albums.filter { it.name.equals("Camera", true) || it.name.equals("Kamera", true) }.map { it.id }.toSet().ifEmpty { albums.map { it.id }.toSet() }
            else -> albums.map { it.id }.toSet()
        }
    }
    var pick by remember(start) { mutableStateOf(start) }
    var existing by remember { mutableStateOf(false) }
    val count = albums.filter { it.id in pick }.sumOf { it.photos + it.videos }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        title = { Text(if (enabling) "Was soll gesichert werden?" else "Alben für die Sicherung") },
        text = {
            Column {
                if (albums.isEmpty()) Text("Keine Alben gefunden.", color = p.muted)
                Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                    albums.forEach { a ->
                        Row(
                            Modifier.fillMaxWidth().clickable { pick = if (a.id in pick) pick - a.id else pick + a.id }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(a.id in pick, { on -> pick = if (on) pick + a.id else pick - a.id }, colors = CheckboxDefaults.colors(checkedColor = p.accent, checkmarkColor = p.onAccent))
                            Text(a.name, color = p.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${a.photos + a.videos}", style = MonoSmall, color = p.muted)
                        }
                    }
                }
                if (enabling) {
                    HorizontalDivider(color = p.borderSoft, modifier = Modifier.padding(vertical = 8.dp))
                    Row(Modifier.fillMaxWidth().clickable { existing = !existing }, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Auch vorhandene sichern", color = p.text)
                            Text(if (existing) "$count Dateien werden hochgeladen" else "nur, was ab jetzt dazukommt", style = MonoSmall, color = p.muted)
                        }
                        PlexSwitch(existing) { existing = it }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = pick.isNotEmpty(), onClick = { onConfirm(pick, existing) }) { Text(if (enabling) "Sicherung starten" else "Übernehmen", color = p.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen", color = p.textSoft) } },
    )
}
