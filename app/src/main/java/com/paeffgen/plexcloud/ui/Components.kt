package com.paeffgen.plexcloud.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.paeffgen.plexcloud.R
import com.paeffgen.plexcloud.data.CloudItem
import com.paeffgen.plexcloud.ui.theme.Label
import com.paeffgen.plexcloud.ui.theme.LocalPlex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

val CardShape = RoundedCornerShape(16.dp)
val TileShape = RoundedCornerShape(11.dp)

@Composable
fun PIcon(id: Int, desc: String?, tint: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) =
    Icon(painterResource(id), contentDescription = desc, tint = tint, modifier = modifier.size(size))

@Composable
fun Modifier.plexCard(low: Boolean = false, shape: RoundedCornerShape = CardShape): Modifier {
    val p = LocalPlex.current
    return this.clip(shape).background(if (low) p.surfaceLow else p.surface).border(1.dp, if (low) p.borderSoft else p.border, shape)
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = Label, color = LocalPlex.current.muted, modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp))
}

fun kindIcon(k: CloudItem.Kind) = when (k) {
    CloudItem.Kind.Folder -> R.drawable.ic_folder
    CloudItem.Kind.Photo -> R.drawable.ic_photo
    CloudItem.Kind.Pdf -> R.drawable.ic_pdf
    CloudItem.Kind.Video -> R.drawable.ic_video
    CloudItem.Kind.File -> R.drawable.ic_file
}

@Composable
fun kindColor(k: CloudItem.Kind): Color {
    val p = LocalPlex.current
    return when (k) {
        CloudItem.Kind.Folder -> p.folder
        CloudItem.Kind.Photo -> p.accent
        CloudItem.Kind.Pdf -> p.blue
        CloudItem.Kind.Video -> p.violet
        CloudItem.Kind.File -> p.muted
    }
}

/** Kachel mit Symbol oder Miniatur */
@Composable
fun KindTile(item: CloudItem, size: Dp = 42.dp) {
    val color = kindColor(item.kind)
    Box(Modifier.size(size).clip(TileShape).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
        val thumb = item.thumbUrl?.let { rememberRemoteImage(it, item.itemId) }
        if (thumb != null) Image(thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else PIcon(kindIcon(item.kind), null, color, size = size * 0.5f)
    }
}

private val thumbCache = LruCache<String, ImageBitmap>(200)

/** Kleines Bild von einem signierten Link laden (Miniaturen, je Datei einmal im Speicher gehalten) */
@Composable
fun rememberRemoteImage(url: String, key: String): ImageBitmap? {
    val img by produceState(thumbCache.get(key), url) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching { URL(url).openStream().use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
        }?.also { thumbCache.put(key, it) }
    }
    return img
}

/** Ring mit mehreren Abschnitten (Anteile 0..1), beginnend oben */
@Composable
fun Ring(segments: List<Pair<Float, Color>>, size: Dp, stroke: Dp, track: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val d = this.size.minDimension - w
            val tl = Offset(w / 2, w / 2)
            drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(w))
            var start = -90f
            for ((frac, color) in segments) {
                val sweep = (frac * 360f).coerceIn(0f, 360f)
                if (sweep > 0.3f) drawArc(color, start, maxOf(sweep - 1.5f, 0.8f), false, tl, Size(d, d), style = Stroke(w, cap = if (segments.size == 1) StrokeCap.Round else StrokeCap.Butt))
                start += sweep
            }
        }
        content()
    }
}

/** Geteilte Leiste (Speicher nach Art) */
@Composable
fun SegmentBar(segments: List<Pair<Float, Color>>, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val p = LocalPlex.current
    Row(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(p.track), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for ((frac, color) in segments) if (frac > 0f) Box(Modifier.fillMaxHeight().weight(frac.coerceAtLeast(0.004f)).background(color))
        val rest = 1f - segments.sumOf { it.first.toDouble() }.toFloat()
        if (rest > 0.001f) Box(Modifier.fillMaxHeight().weight(rest))
    }
}

@Composable
fun Dot(color: Color) = Box(Modifier.size(7.dp).clip(RoundedCornerShape(2.dp)).background(color))

@Composable
fun MonoText(text: String, style: TextStyle, color: Color = LocalPlex.current.text, modifier: Modifier = Modifier) =
    Text(text, style = style, color = color, modifier = modifier, maxLines = 1)

/** Profilbild im Kreis mit Akzent-Rand; ohne Bild (oder solange es lädt) die Initialen */
@Composable
fun Avatar(profile: com.paeffgen.plexcloud.data.Profile?, size: Dp, modifier: Modifier = Modifier) {
    val p = LocalPlex.current
    val img = profile?.pictureUrl?.let { rememberRemoteImage(it, "avatar:$it") }
    Box(
        modifier.size(size).clip(androidx.compose.foundation.shape.CircleShape).background(p.surface)
            .border(1.5.dp, p.accent.copy(alpha = 0.7f), androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (img != null) androidx.compose.foundation.Image(img, profile.name.ifBlank { "Profilbild" }, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        else androidx.compose.material3.Text(profile?.initials ?: "", color = p.accent, fontFamily = com.paeffgen.plexcloud.ui.theme.Mono, fontSize = androidx.compose.ui.unit.TextUnit(size.value * 0.36f, androidx.compose.ui.unit.TextUnitType.Sp))
    }
}
