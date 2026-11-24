package de.wonderfulworld.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Vergrößerung, damit ein um [degrees] gedrehtes w×h-Bild den w×h-Rahmen ganz füllt. */
fun coverScale(w: Float, h: Float, degrees: Float): Float {
    val a = Math.toRadians(abs(degrees).toDouble())
    val c = cos(a).toFloat()
    val s = sin(a).toFloat()
    return max((w * c + h * s) / w, (w * s + h * c) / h)
}

/** Fotos liegen als JPEG im privaten App-Ordner "photos". */
class PhotoStore(private val context: Context) {
    val dir: File = File(context.filesDir, "photos").apply { mkdirs() }

    fun file(name: String) = File(dir, name)

    fun delete(name: String) {
        file(name).delete()
    }

    /** Liest ein Bild (Kamera oder Galerie), verkleinert es, dreht es richtig und speichert es. */
    fun importImage(uri: Uri, maxSize: Int = 2048): String? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // Hinweis: decodeStream liefert mit inJustDecodeBounds immer null – nur die Maße zählen.
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSize || bounds.outHeight / (sample * 2) >= maxSize) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

        val rotation = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f

        val scale = minOf(1f, maxSize.toFloat() / maxOf(bmp.width, bmp.height))
        if (rotation != 0f || scale < 1f) {
            val m = Matrix().apply { postScale(scale, scale); postRotate(rotation) }
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (rotated != bmp) bmp.recycle()
            bmp = rotated
        }

        val name = "${UUID.randomUUID()}.jpg"
        file(name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bmp.recycle()
        return name
    }

    /** Bild zur Anzeige im Editor laden (verkleinert auf höchstens [maxSize] px). */
    fun loadPreview(name: String, maxSize: Int = 1600): Bitmap? {
        val f = file(name)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSize || bounds.outHeight / (sample * 2) >= maxSize) sample *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /**
     * Dreht das Bild um [degrees] (vergrößert so, dass keine leeren Ecken entstehen)
     * und schneidet den Bereich [left]..[right] × [top]..[bottom] (Anteile 0..1) aus.
     * Ergebnis ist eine neue Datei; das Original bleibt unverändert.
     */
    fun cropRotate(name: String, degrees: Float, left: Float, top: Float, right: Float, bottom: Float): String? {
        val src = BitmapFactory.decodeFile(file(name).path) ?: return null
        val w = src.width.toFloat()
        val h = src.height.toFloat()
        val s = coverScale(w, h, degrees)
        val m = Matrix().apply {
            postTranslate(-w / 2, -h / 2); postRotate(degrees); postScale(s, s); postTranslate(w / 2, h / 2)
        }
        val cw = ((right - left) * w).toInt().coerceAtLeast(1)
        val ch = ((bottom - top) * h).toInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            translate(-left * w, -top * h)
            drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        }
        src.recycle()
        val newName = "${UUID.randomUUID()}.jpg"
        file(newName).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        out.recycle()
        return newName
    }

    /** Temporäre Datei für Kamera-Aufnahmen (über FileProvider freigegeben). */
    fun newCameraFile(): File = File(File(context.cacheDir, "camera").apply { mkdirs() }, "shot.jpg")
}
