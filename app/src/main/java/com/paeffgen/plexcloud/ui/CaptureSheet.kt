package com.paeffgen.plexcloud.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.paeffgen.plexcloud.R
import com.paeffgen.plexcloud.ui.theme.LocalPlex
import com.paeffgen.plexcloud.ui.theme.MonoSmall

/** Nach dem Tipp auf die Kamera: Dokument (Scanner → PDF) oder Foto (Kamera → JPG)? */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureSheet(folderLabel: String, onDocument: () -> Unit, onPhoto: () -> Unit, onDismiss: () -> Unit) {
    val p = LocalPlex.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.surface) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Was nimmst du auf?", style = MaterialTheme.typography.titleLarge, color = p.text)
            Text("Ziel: $folderLabel", style = MonoSmall, color = p.muted)
            Choice(
                icon = R.drawable.ic_pdf, tint = p.blue, title = "Dokument", badge = "PDF",
                text = "Rechnung, Brief, Formular: Kanten werden erkannt und gerade gezogen, mehrere Seiten in einer Datei",
                onClick = onDocument,
            )
            Choice(
                icon = R.drawable.ic_photo, tint = p.accent, title = "Foto", badge = "JPG",
                text = "Baustelle, Schaden, Produkt: volle Auflösung, wie mit der Kamera-App",
                onClick = onPhoto,
            )
        }
    }
}

@Composable
private fun Choice(icon: Int, tint: Color, title: String, badge: String, text: String, onClick: () -> Unit) {
    val p = LocalPlex.current
    Row(
        Modifier.fillMaxWidth().plexCard(low = true, shape = RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            PIcon(icon, null, tint, 26.dp)
        }
        Column(Modifier.padding(start = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = p.text)
                Text(badge, style = MonoSmall, fontWeight = FontWeight.SemiBold, color = tint,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(tint.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Text(text, style = MaterialTheme.typography.bodySmall, color = p.muted)
        }
    }
}
