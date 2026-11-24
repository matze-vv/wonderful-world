package de.wonderfulworld.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.UUID

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
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
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

    /** Temporäre Datei für Kamera-Aufnahmen (über FileProvider freigegeben). */
    fun newCameraFile(): File = File(File(context.cacheDir, "camera").apply { mkdirs() }, "shot.jpg")
}
