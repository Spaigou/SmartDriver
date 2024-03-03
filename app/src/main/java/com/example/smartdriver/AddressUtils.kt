package com.example.smartdriver

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import java.util.*

object AddressUtils {
    private fun addressNotFound(context: Context, address: String) {
        val alertDialog = AlertDialog.Builder(context)
            .setTitle("Внимание")
            .setMessage("Адрес \"$address\" не был найден. Пожалуйста, оповестите диспетчера об этом и запросите повторную отпрвку информации.")
            .setPositiveButton("ОК") { dialog, _ -> dialog.dismiss() }
            .create()

        alertDialog.show()
    }

    fun printAddresses(resultText: TextView, fullAddresses: List<Address>, mode: Int) {
        resultText.text = ""
        var outputText = if (mode == 0) "Обычный маршрут:\n" else "Оптимизированный маршрут:\n"
        fullAddresses.forEachIndexed { index, address ->
            if (index != 0)
                outputText += "${index}. ${address.thoroughfare} ${address.featureName}, ${address.locality}\n\n"
        }
        resultText.text = outputText
    }

    fun receiveAddresses(context: Context, allShortAddresses: List<String>): MutableList<Address> {
        val geocoder = Geocoder(context, Locale.getDefault())
        val fullAddresses: MutableList<Address> = mutableListOf()
        allShortAddresses.forEach { addressName ->
            val addressList =
                geocoder.getFromLocationName(addressName, 1)
            if (addressList.isNotEmpty() && addressList[0]?.thoroughfare != null) {
                fullAddresses.add(addressList[0])
            } else {
                addressNotFound(context, addressName)
            }
        }
        addCurrentLocation(context, fullAddresses)
        return fullAddresses
    }

    private fun addCurrentLocation(context: Context, fullAddresses: MutableList<Address>) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        fun requestLocation() {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val address = Address(Locale.getDefault())
                    address.latitude = location.latitude
                    address.longitude = location.longitude
                    address.thoroughfare = "Текущее местоположение"
                    fullAddresses.add(0, address)
                } else {
                    showToast(context, "Не удалось получить местоположение")

                    // Повторить запрос через 1 секунду
                    Handler(Looper.getMainLooper()).postDelayed({
                        requestLocation()
                    }, 1000)
                }
            }
        }

        if (!checkPermission(context as AppCompatActivity) && fullAddresses.isNotEmpty()) {
            requestLocation()
        } else if (fullAddresses.isEmpty()) {
            showToast(context, "Нет адресов")
        } else {
            Log.d("Distance exception", "no geo")
            requestPermission(context)
        }
    }

    fun showToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun checkPermission(activity: AppCompatActivity): Boolean {
        return ActivityCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED
    }

    fun requestPermission(activity: AppCompatActivity) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            PERMISSIONS_REQUEST_LOCATION
        )
    }

    private const val PERMISSIONS_REQUEST_LOCATION = 1
}