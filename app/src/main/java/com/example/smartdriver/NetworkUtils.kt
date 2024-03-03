package com.example.smartdriver

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog

object NetworkUtils {
    fun showNetworkEnableDialog(context: Context) {
        val alertDialog = AlertDialog.Builder(context)
            .setTitle("Отключено подключение к сети")
            .setMessage("Для использования этого приложения необходимо включить подключение к сети. Хотите включить?")
            .setPositiveButton("Включить") { dialogInterface: DialogInterface, _: Int ->
                // Открываем настройки подключения к сети
                context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                dialogInterface.dismiss()
            }
            .setNegativeButton("Отмена") { dialogInterface: DialogInterface, _: Int ->
                dialogInterface.dismiss()
            }
            .create()

        alertDialog.show()
    }

    fun isNetworkConnected(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities =
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            ?: false
    }
}
