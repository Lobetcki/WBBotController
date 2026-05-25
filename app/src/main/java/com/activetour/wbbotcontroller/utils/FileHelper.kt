package com.activetour.wbbotcontroller.utils

import android.os.Environment
import java.io.File

//object FileHelper {

//    fun getDownloadsDir(): File {
//        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
//    }

//    fun createAppDir(dirName: String): File {
//        val appDir = File(getDownloadsDir(), dirName)
//        if (!appDir.exists()) {
//            appDir.mkdirs()
//        }
//        return appDir
//    }

//    fun getFileSize(file: File): String {
//        val size = file.length()
//        return when {
//            size < 1024 -> "$size B"
//            size < 1024 * 1024 -> String.format("%.2f KB", size / 1024.0)
//            else -> String.format("%.2f MB", size / (1024.0 * 1024.0))
//        }
//    }
//}