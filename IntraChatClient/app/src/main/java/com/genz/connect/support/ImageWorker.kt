package com.genz.connect.support

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Environment
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

object ImageWorker {

    private const val REQUESTED_SIZE = 360

    suspend fun compressImage(stream: () -> InputStream?): ByteArray = withContext(Dispatchers.IO) {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true

        stream().use {
            BitmapFactory.decodeStream(it, null, options)
        }

        var tmpWidth = options.outWidth
        var tmpHeight = options.outHeight
        var scale = 1

        while (tmpHeight / 2 >= REQUESTED_SIZE && tmpHeight / 2 >= REQUESTED_SIZE) {
            tmpWidth /= 2
            tmpHeight /= 2
            scale *= 2
        }

        val scaledOptions = BitmapFactory.Options()
        scaledOptions.inSampleSize = scale

        val bitmap = stream().use { BitmapFactory.decodeStream(it, null, scaledOptions)!! }

        val rotatedBitmap = stream()?.let {
            val exifInterface = runCatching {
                ExifInterface(it)
            }.getOrNull()

            exifInterface?.let {
                val rotation = it.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                val rotationDegree = exifToDegrees(rotation)
                val matrix = Matrix()
                if (rotation != 0) {
                    matrix.preRotate(rotationDegree)
                }

                Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.width,
                    bitmap.height,
                    matrix,
                    true
                )
            }
        }

        val out = ByteArrayOutputStream()
        out.use {
            if (rotatedBitmap != null) {
                rotatedBitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            } else {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }

            it.toByteArray()
        }
    }

    suspend fun compressGIF(input: () -> InputStream?): ByteArray? = withContext(Dispatchers.IO) {
        input()?.use { it.readBytes() }
    }

    suspend fun writeToCache(context: Context, array: JSONArray, client: String, gif: Boolean = false): String {
        //val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)

        val len = array.length()
        val data = ByteArray(len)
        var i = 0
        while (i < len) {
            data[i] = (array[i] as Int).toByte()
            i += 1
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(Date())
        val fileName = "${client}_${timeStamp}_"


        val file = runInterruptible(Dispatchers.IO) {
            val file = File.createTempFile(
                fileName,
                if (gif) ".gif" else ".png",
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            )
            FileOutputStream(file).use { it.write(data) }
            file
        }


        return file.name
    }

    private fun exifToDegrees(exifRotation: Int): Float {
        return when (exifRotation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }

}