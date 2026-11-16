package com.paeffgen.plexcloud.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paeffgen.plexcloud.R
import com.paeffgen.plexcloud.data.CloudItem
import com.paeffgen.plexcloud.data.Crumb
import com.paeffgen.plexcloud.data.Usage
import com.paeffgen.plexcloud.domain.Format
import com.paeffgen.plexcloud.ui.theme.Label
import com.paeffgen.plexcloud.ui.theme.LocalPlex
import com.paeffgen.plexcloud.ui.theme.MonoBody
import com.paeffgen.plexcloud.ui.theme.MonoSmall

@Composable
fun FilesScreen(
    state: FolderState,
    filter: Filter,
    lastSync: Long,
    onFilter: (Filter) -> Unit,
    onOpen: (CloudItem) -> Unit,
    onCrumb: (String) -> Unit,
    onRefresh: () -> Unit,
    onNewFolder: (String) -> Unit,
    onPickFiles: () -> Unit,
    profile: com.paeffgen.plexcloud.data.Profile? = null,
    onProfile: () -> Unit = {},
) {
    val p = LocalPlex.current
    var newFolder by remember { mutableStateOf(false) }
    val listing = state.listing
    val items = listing?.items.orEmpty().filter {
        when (filter) {
            Filter.All -> true
            Filter.Photos -> it.kind == CloudItem.Kind.Photo
            Filter.Pdf -> it.kind == CloudItem.Kind.Pdf
            Filter.Linked -> it.links > 0
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(p.surface).border(1.dp, p.border, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                PIcon(R.drawable.ic_cloud, null, p.accent, 20.dp)
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text("PlexCloud", style = androidx.compose.material3.MaterialTheme.typography.titleLarge, color = p.text)
                Text("eu-central-1 · ${if (state.loading) "lädt …" else "synchron " + Format.ago(lastSync)}", style = MonoSmall, color = p.muted)
            }
            IconButton(onClick = { newFolder = true }) { PIcon(R.drawable.ic_folder_plus, "Neuer Ordner", p.textSoft) }
            IconButton(onClick = onPickFiles) { PIcon(R.drawable.ic_upload, "Dateien hochladen", p.textSoft) }
            if (profile != null) Avatar(profile, 32.dp, Modifier.padding(start = 4.dp).clickable(onClick = onProfile))
        }

        listing?.usage?.let { StorageCard(it) }

        Breadcrumbs(listing?.path.orEmpty(), items.size, onCrumb)

        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Filter.All to "Alle", Filter.Photos to "Fotos", Filter.Pdf to "PDF", Filter.Linked to "Verknüpft").forEach { (f, label) ->
                FilterChipPlex(label, f == filter) { onFilter(f) }
            }
        }

        PullToRefreshBox(isRefreshing = state.loading && listing != null, onRefresh = onRefresh, modifier = Modifier.weight(1f)) {
            when {
                state.error != null && listing == null -> Message(R.drawable.ic_cloud, state.error, "Nochmal", onRefresh)
                listing == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = p.accent) }
                items.isEmpty() -> Message(R.drawable.ic_folder, if (filter == Filter.All) "Dieser Ordner ist leer." else "Nichts für diesen Filter.", null, null)
                else -> LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp)) {
                    items(items, key = { it.itemId }) { ItemRow(it, onOpen) }
                }
            }
        }
    }

    if (newFolder) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newFolder = false },
            title = { Text("Neuer Ordner") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }) },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onNewFolder(name); newFolder = false }) { Text("Anlegen") } },
            dismissButton = { TextButton(onClick = { newFolder = false }) { Text("Abbrechen") } },
            containerColor = p.surface,
        )
    }
}

@Composable
fun StorageCard(u: Usage) {
    val p = LocalPlex.current
    val limit = (u.limit ?: 0L).coerceAtLeast(1)
    Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth().plexCard().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("SPEICHER", style = Label, color = p.muted, modifier = Modifier.weight(1f))
            Text(Format.gb(u.used) + " GB", style = MonoBody, color = p.text)
            Text(if (u.limit != null) " / ${Format.gb(u.limit)} GB" else " / unbegrenzt", style = MonoBody, color = p.muted)
        }
        // Unbegrenzt (Betreiber, Demo): kein Füllstand, sonst stünde der Balken immer auf voll
        SegmentBar(if (u.limit == null) emptyList() else listOf((u.used.toFloat() / limit).coerceIn(0f, 1f) to p.accent))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) { Dot(p.accent); Text("${u.files} Dateien", style = MonoSmall, color = p.textSoft) }
            if (u.limit != null) Text("${Format.gb((u.limit - u.used).coerceAtLeast(0))} GB frei", style = MonoSmall, color = p.textSoft)
            Text("max. ${Format.bytes(u.maxFile)} je Datei", style = MonoSmall, color = p.muted)
        }
    }
}

@Composable
private fun Breadcrumbs(path: List<Crumb>, count: Int, onCrumb: (String) -> Unit) {
    val p = LocalPlex.current
    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            Text("/", style = MonoBody.copy(fontSize = 12.sp), color = if (path.isEmpty()) p.text else p.muted,
                modifier = Modifier.clickable { onCrumb(com.paeffgen.plexcloud.data.ROOT) }.padding(end = 6.dp, top = 6.dp, bottom = 6.dp))
            path.forEachIndexed { i, c ->
                val last = i == path.lastIndex
                Text(c.name, style = MonoBody.copy(fontSize = 12.sp), color = if (last) p.text else p.muted, maxLines = 1,
                    modifier = Modifier.clickable(enabled = !last) { onCrumb(c.itemId) }.padding(vertical = 6.dp))
                if (!last) Text(" / ", style = MonoBody.copy(fontSize = 12.sp), color = p.muted)
            }
        }
        Text("$count ${if (count == 1) "Eintrag" else "Einträge"}", style = MonoSmall, color = p.muted)
    }
}

@Composable
fun FilterChipPlex(label: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPlex.current
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier.height(32.dp).clip(shape)
            .background(if (selected) p.accentSoft else Color.Transparent)
            .border(1.dp, if (selected) p.accent else Color(0xFF24304F), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, color = if (selected) p.text else p.textSoft) }
}


@Composable
fun ItemRow(item: CloudItem, onOpen: (CloudItem) -> Unit, meta: String? = null) {
    val p = LocalPlex.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(item) }.padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KindTile(item)
        Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.name, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(meta ?: itemMeta(item), style = MonoSmall, color = p.muted, maxLines = 1)
                if (item.links > 0) PIcon(R.drawable.ic_link, "verknüpft", p.blue, 12.dp)
                if (item.portal) PIcon(R.drawable.ic_globe, "im Kundenportal", p.accent, 12.dp)
            }
        }
        PIcon(R.drawable.ic_more, null, p.muted, 18.dp, Modifier.padding(start = 8.dp))
    }
}

fun itemMeta(i: CloudItem): String =
    if (i.folder) "${i.count ?: 0} ${if (i.count == 1) "Datei" else "Dateien"} · ${Format.bytes(i.size)}"
    else "${Format.bytes(i.size)} · ${Format.shortDate(i.updatedAt)}"

@Composable
fun Message(icon: Int, text: String, action: String?, onAction: (() -> Unit)?) {
    val p = LocalPlex.current
    Column(Modifier.fillMaxSize().padding(32.dp).semantics { contentDescription = text }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        PIcon(icon, null, p.muted, 32.dp)
        Spacer(Modifier.height(10.dp))
        Text(text, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = p.muted)
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action, color = p.accent) }
        Spacer(Modifier.width(1.dp))
    }
}
