package com.paeffgen.plexcloud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paeffgen.plexcloud.R
import com.paeffgen.plexcloud.ui.theme.Grotesk
import com.paeffgen.plexcloud.ui.theme.LocalPlex
import com.paeffgen.plexcloud.ui.theme.MonoSmall

@Composable
fun LoginScreen(error: String?, onGoogle: () -> Unit, onEmail: () -> Unit) {
    val p = LocalPlex.current
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(88.dp).clip(RoundedCornerShape(26.dp)).background(p.surface).border(1.dp, p.border, RoundedCornerShape(26.dp)), contentAlignment = Alignment.Center) {
            PIcon(R.drawable.ic_cloud, null, p.accent, 52.dp)
        }
        Spacer(Modifier.height(22.dp))
        Text("PlexCloud", fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = 34.sp, color = p.text)
        Text("Deine Dateien. In Frankfurt. In deiner Hand.", style = MaterialTheme.typography.bodyMedium, color = p.textSoft, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(36.dp))

        Button(
            onClick = onGoogle, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
        ) {
            PIcon(R.drawable.ic_google, null, p.onAccent, 18.dp)
            Text("   Mit Google anmelden", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onEmail, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, p.border),
        ) {
            PIcon(R.drawable.ic_mail, null, p.text, 18.dp)
            Text("   Mit E-Mail anmelden", color = p.text, fontWeight = FontWeight.SemiBold)
        }
        if (error != null) Text(error, style = MaterialTheme.typography.bodySmall, color = p.danger, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))

        Spacer(Modifier.height(40.dp))
        Text("Plexora-Konto · Cognito eu-central-1 · OAuth 2.0 + PKCE", style = MonoSmall, color = p.muted, textAlign = TextAlign.Center)
        Text("Dein Passwort gibst du nur bei Plexora ein, nie in der App.", style = MaterialTheme.typography.labelSmall, color = p.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}
