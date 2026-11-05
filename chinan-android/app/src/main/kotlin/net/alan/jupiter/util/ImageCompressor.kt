package net.alan.jupiter.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

/**
 * 证明照片压缩工具：上传前把图片压到最长边 1024px 的 JPEG，避免超出服务端 10MB 帧限制，
 * 同时显著降低流量与内存占用。
 */
object ImageCompressor {

    private const val MAX_EDGE = 1024
    private const val JPEG_QUALITY = 80

    fun compressToJpegBytes(file: File): ByteArray {
        require(file.exists()) { "图片不存在: ${file.absolutePath}" }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val longest = max(bounds.outWidth, bounds.outHeight)
        require(longest > 0) { "无法解析图片: ${file.name}" }

        var sampleSize = 1
        while (longest / sampleSize > MAX_EDGE * 2) {
            sampleSize *= 2
        }

        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        ) ?: throw IllegalStateException("无法读取图片: ${file.name}")

        val scale = MAX_EDGE.toFloat() / max(decoded.width, decoded.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            decoded
        }

        return try {
            ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                out.toByteArray()
            }
        } finally {
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
        }
    }
}
