package com.paeffgen.plexcloud.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paeffgen.plexcloud.R
import com.paeffgen.plexcloud.data.CloudItem
import com.paeffgen.plexcloud.data.LinkInfo
import com.paeffgen.plexcloud.domain.Format
import com.paeffgen.plexcloud.ui.theme.LocalPlex
import com.paeffgen.plexcloud.ui.theme.Mono
import com.paeffgen.plexcloud.ui.theme.MonoSmall

@Composable
fun DetailScreen(
    state: DetailState,
    onBack: () -> Unit,
    onView: (CloudItem) -> Unit,
    onDownload: (CloudItem) -> Unit,
    onShare: (CloudItem) -> Unit,
    onPortal: (LinkInfo, Boolean) -> Unit,
) {
    val p = LocalPlex.current
    val item = state.item
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { PIcon(R.drawable.ic_back, "Zurück", p.textSoft, 22.dp) }
            Text(item.name, style = MaterialTheme.typography.titleMedium, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (state.busy) CircularProgressIndicator(Modifier.size(18.dp), color = p.accent, strokeWidth = 2.dp)
        }

        // Vorschau: Miniatur, sonst großes Symbol
        Box(Modifier.padding(horizontal = 20.dp, vertical = 4.dp).fillMaxWidth().height(236.dp).plexCard(shape = RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
            val thumb = item.thumbUrl?.let { rememberRemoteImage(it, item.itemId) }
            if (thumb != null) androidx.compose.foundation.Image(thumb, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            else PIcon(kindIcon(item.kind), null, kindColor(item.kind), 64.dp)
            Text(item.contentType.ifEmpty { "unbekannt" }, style = MonoSmall, color = p.textSoft,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp).clip(RoundedCornerShape(8.dp)).background(p.ground.copy(alpha = 0.85f)).padding(horizontal = 9.dp, vertical = 4.dp))
        }

        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Action(R.drawable.ic_eye, "Ansehen", Modifier.weight(1f)) { onView(item) }
            Action(R.drawable.ic_download, "Laden", Modifier.weight(1f)) { onDownload(item) }
            Action(R.drawable.ic_share, "Teilen", Modifier.weight(1f)) { onShare(item) }
        }

        SectionLabel("Verknüpft mit")
        val links = state.links
        when {
            links == null -> Text("lädt …", style = MonoSmall, color = p.muted, modifier = Modifier.padding(horizontal = 20.dp))
            links.isEmpty() -> Text("Noch nicht verknüpft. Verknüpfen geht in der Web-Version (Rechtsklick → „Verknüpfen …“).", style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.padding(horizontal = 20.dp))
            else -> Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                links.forEach { l -> LinkRow(l, state.busy, onPortal) }
            }
        }
        state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = p.danger, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) }

        SectionLabel("Details")
        Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().plexCard(low = true, shape = RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 4.dp)) {
            val facts = listOf(
                "Größe" to Format.bytes(item.size),
                "Typ" to item.contentType.ifEmpty { "—" },
                "Geändert" to Format.dateTime(item.updatedAt),
                "Ordner" to ("/" + state.path.joinToString("/") { it.name }),
                "Speicherort" to "S3 · Frankfurt",
                "Verschlüsselt" to "AES-256 (SSE)",
            )
            facts.forEachIndexed { i, (k, v) ->
                Row(Modifier.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(k, style = MaterialTheme.typography.bodySmall, color = p.muted, modifier = Modifier.weight(0.4f))
                    Text(v, style = MonoSmall.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize), color = p.text, textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.6f))
                }
                if (i < facts.lastIndex) HorizontalDivider(color = p.borderSoft)
            }
        }
        Box(Modifier.height(24.dp))
    }
}

@Composable
private fun Action(icon: Int, label: String, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPlex.current
    Column(
        modifier.height(64.dp).plexCard(shape = RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        PIcon(icon, null, p.accent, 20.dp)
        Text(label, style = MaterialTheme.typography.labelSmall, color = p.text, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun LinkRow(l: LinkInfo, busy: Boolean, onPortal: (LinkInfo, Boolean) -> Unit) {
    val p = LocalPlex.current
    val tint = if (l.typ == "contact") p.accent else p.blue
    Row(Modifier.fillMaxWidth().plexCard(shape = RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Text(l.badge, fontFamily = Mono, fontWeight = FontWeight.SemiBold, style = MonoSmall, color = tint)
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(l.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    l.typ != "contact" -> l.typLabel
                    l.portal -> "im Kundenportal sichtbar"
                    !l.email -> "Kontakt · ohne E-Mail, kein Portal möglich"
                    else -> "Kontakt"
                },
                style = MaterialTheme.typography.labelSmall, color = if (l.portal) p.accent else p.muted,
            )
        }
        if (l.typ == "contact" && l.email) Switch(
            checked = l.portal, enabled = !busy, onCheckedChange = { onPortal(l, it) },
            colors = SwitchDefaults.colors(checkedTrackColor = p.accent, checkedThumbColor = p.onAccent),
        )
    }
}
