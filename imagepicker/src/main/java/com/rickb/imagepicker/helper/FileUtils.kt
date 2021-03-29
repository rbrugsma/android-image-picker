package com.rickb.imagepicker.helper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.rickb.imagepicker.model.Image
import com.rickb.imagepicker.model.ImageQuality
import java.io.File
import java.io.FileOutputStream

fun addCompressedFile(image: Image, publicAppDirectory: String?, imageQuality: ImageQuality?) {
    val directory = publicAppDirectory ?: return

    val file = File(directory, "compressed${System.currentTimeMillis()}")
    file.createNewFile()

    val outputStream = FileOutputStream(file)
    BitmapFactory.decodeFile(image.path)?.let { bitmap ->

        val resizedBitmap = imageQuality?.let {
            applyQualityToBitmap(imageQuality, bitmap)
        } ?: bitmap

        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)

        outputStream.close()
        image.compressedFilePath = file.path
    } ?: outputStream.close()
}

private fun applyQualityToBitmap(photoMemoQuality: ImageQuality, originalBitmap: Bitmap): Bitmap {
    val currentWidth = originalBitmap.width
    val currentHeight = originalBitmap.height

    return if (currentWidth > currentHeight) {
        val desiredWidth = photoMemoQuality.desiredWidth
        Bitmap.createScaledBitmap(originalBitmap, desiredWidth, calculateHeight(desiredWidth, currentWidth, currentHeight), true)
    } else {
        val desiredHeight = photoMemoQuality.desiredHeight
        Bitmap.createScaledBitmap(originalBitmap, calculateWidth(desiredHeight, currentWidth, currentHeight), desiredHeight, true)
    }
}

private fun calculateHeight(desiredWidth: Int, currentWidth: Int, currentHeight: Int): Int {
    return desiredWidth / (currentWidth / currentHeight)
}

private fun calculateWidth(desiredHeight: Int, currentWidth: Int, currentHeight: Int): Int {
    return (desiredHeight.toFloat() * (currentWidth.toFloat() / currentHeight.toFloat())).toInt()
}