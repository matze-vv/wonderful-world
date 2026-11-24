package de.wonderfulworld.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.wonderfulworld.app.data.PhotoStore
import de.wonderfulworld.app.data.coverScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot

private enum class Handle { MOVE, TL, TR, BL, BR, NONE }
private const val MIN_FRACTION = 0.1f

/**
 * Vollbild-Editor: freier Zuschnitt (Ecken ziehen, Rahmen verschieben) und
 * Geraderücken per Schieberegler (±45°).
 */
@Composable
fun PhotoEditorDialog(
    photos: PhotoStore,
    name: String,
    onCancel: () -> Unit,
    onApply: (degrees: Float, crop: Rect) -> Unit,
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(name) { bitmap = withContext(Dispatchers.IO) { photos.loadPreview(name) } }
    var angle by remember { mutableFloatStateOf(0f) }
    var crop by remember { mutableStateOf(Rect(0f, 0f, 1f, 1f)) }
    val accent = Color(0xFF7FB0DD)

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF0D1620)).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel) { Text("Abbrechen", color = Color.White) }
                Text("Zuschneiden", Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.titleMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                TextButton(onClick = { onApply(angle, crop) }, enabled = bitmap != null) {
                    Text("Übernehmen", color = accent, fontWeight = FontWeight.ExtraBold)
                }
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                val bmp = bitmap
                if (bmp == null) {
                    CircularProgressIndicator(color = Color.White)
                } else {
                    val aspect = bmp.width.toFloat() / bmp.height
                    val boxAspect = maxWidth / maxHeight
                    val frameW = if (aspect > boxAspect) maxWidth else maxHeight * aspect
                    val frameH = if (aspect > boxAspect) maxWidth / aspect else maxHeight
                    val density = LocalDensity.current
                    val fw = with(density) { frameW.toPx() }
                    val fh = with(density) { frameH.toPx() }
                    val touch = with(density) { 36.dp.toPx() }
                    val scale = coverScale(bmp.width.toFloat(), bmp.height.toFloat(), angle)

                    Box(Modifier.size(frameW, frameH).clipToBounds()) {
                        Image(
                            bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = angle; scaleX = scale; scaleY = scale },
                        )
                        Canvas(
                            Modifier.fillMaxSize()
                                .semantics { contentDescription = "Zuschnitt-Rahmen. Ecken ziehen zum Anpassen, Mitte ziehen zum Verschieben." }
                                .pointerInput(fw, fh) {
                                    var handle = Handle.NONE
                                    detectDragGestures(
                                        onDragStart = { p ->
                                            val r = crop
                                            val corners = listOf(
                                                Handle.TL to Offset(r.left * fw, r.top * fh),
                                                Handle.TR to Offset(r.right * fw, r.top * fh),
                                                Handle.BL to Offset(r.left * fw, r.bottom * fh),
                                                Handle.BR to Offset(r.right * fw, r.bottom * fh),
                                            )
                                            val near = corners.minBy { (_, o) -> hypot(o.x - p.x, o.y - p.y) }
                                            handle = when {
                                                hypot(near.second.x - p.x, near.second.y - p.y) <= touch -> near.first
                                                p.x / fw in r.left..r.right && p.y / fh in r.top..r.bottom -> Handle.MOVE
                                                else -> Handle.NONE
                                            }
                                        },
                                        onDrag = { change, d ->
                                            change.consume()
                                            crop = moveCrop(crop, handle, d.x / fw, d.y / fh)
                                        },
                                    )
                                },
                        ) {
                            val r = Rect(crop.left * size.width, crop.top * size.height, crop.right * size.width, crop.bottom * size.height)
                            val dim = Color.Black.copy(alpha = .55f)
                            drawRect(dim, Offset.Zero, Size(size.width, r.top))
                            drawRect(dim, Offset(0f, r.bottom), Size(size.width, size.height - r.bottom))
                            drawRect(dim, Offset(0f, r.top), Size(r.left, r.height))
                            drawRect(dim, Offset(r.right, r.top), Size(size.width - r.right, r.height))
                            // Drittel-Raster
                            for (i in 1..2) {
                                val x = r.left + r.width * i / 3
                                val y = r.top + r.height * i / 3
                                drawLine(Color.White.copy(alpha = .45f), Offset(x, r.top), Offset(x, r.bottom), 1.dp.toPx())
                                drawLine(Color.White.copy(alpha = .45f), Offset(r.left, y), Offset(r.right, y), 1.dp.toPx())
                            }
                            drawRect(Color.White, r.topLeft, r.size, style = Stroke(2.dp.toPx()))
                            val l = 22.dp.toPx()
                            val sw = 4.dp.toPx()
                            listOf(
                                r.topLeft to Offset(1f, 1f), Offset(r.right, r.top) to Offset(-1f, 1f),
                                Offset(r.left, r.bottom) to Offset(1f, -1f), r.bottomRight to Offset(-1f, -1f),
                            ).forEach { (o, dir) ->
                                drawLine(Color.White, o, Offset(o.x + l * dir.x, o.y), sw)
                                drawLine(Color.White, o, Offset(o.x, o.y + l * dir.y), sw)
                            }
                        }
                    }
                }
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Geraderücken", Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold)
                    Text("%+.1f°".format(java.util.Locale.GERMANY, angle), color = accent, fontWeight = FontWeight.ExtraBold)
                }
                Slider(
                    value = angle, onValueChange = { angle = if (abs(it) < 0.4f) 0f else it }, valueRange = -45f..45f,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = Color.White.copy(alpha = .25f)),
                    modifier = Modifier.semantics { contentDescription = "Drehwinkel" },
                )
                TextButton(onClick = { angle = 0f; crop = Rect(0f, 0f, 1f, 1f) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Zurücksetzen", color = Color.White)
                }
            }
        }
    }
}

/** Zuschnitt-Rahmen verschieben oder an einer Ecke verändern; bleibt im Bild und mindestens 10 % groß. */
private fun moveCrop(r: Rect, h: Handle, dx: Float, dy: Float): Rect = when (h) {
    Handle.NONE -> r
    Handle.MOVE -> {
        val nx = (r.left + dx).coerceIn(0f, 1f - r.width)
        val ny = (r.top + dy).coerceIn(0f, 1f - r.height)
        Rect(nx, ny, nx + r.width, ny + r.height)
    }
    Handle.TL -> Rect((r.left + dx).coerceIn(0f, r.right - MIN_FRACTION), (r.top + dy).coerceIn(0f, r.bottom - MIN_FRACTION), r.right, r.bottom)
    Handle.TR -> Rect(r.left, (r.top + dy).coerceIn(0f, r.bottom - MIN_FRACTION), (r.right + dx).coerceIn(r.left + MIN_FRACTION, 1f), r.bottom)
    Handle.BL -> Rect((r.left + dx).coerceIn(0f, r.right - MIN_FRACTION), r.top, r.right, (r.bottom + dy).coerceIn(r.top + MIN_FRACTION, 1f))
    Handle.BR -> Rect(r.left, r.top, (r.right + dx).coerceIn(r.left + MIN_FRACTION, 1f), (r.bottom + dy).coerceIn(r.top + MIN_FRACTION, 1f))
}
