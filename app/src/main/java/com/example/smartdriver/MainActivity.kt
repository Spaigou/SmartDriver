package com.example.smartdriver

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import java.lang.Exception
import java.net.URISyntaxException
import java.util.*


class MainActivity : AppCompatActivity() {
    private lateinit var resultText: TextView
    private var mSocket: Socket? = null
    private var allShortAddresses: MutableList<String> = mutableListOf()
    private var fullAddresses: MutableList<Address> = mutableListOf()
    private var optimizedAddresses: MutableList<Address> = mutableListOf()

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        resultText = findViewById(R.id.resultText)
        resultText.movementMethod = ScrollingMovementMethod()

        val btnOptimizeRoute: Button = findViewById(R.id.btnOptimizeRoute)
        val btnOpenInMap: Button = findViewById(R.id.btnOpenInMap)
        val routeType: Spinner = findViewById(R.id.spinnerRouteType)

        btnOptimizeRoute.setOnClickListener {
            DistanceUtils.getDistances(fullAddresses, mSocket)

        }

        btnOpenInMap.setOnClickListener {
            try {
                val type = routeType.selectedItem.toString()
                if (type == "Обычный маршрут")
                    openInMap(fullAddresses)
                else
                    openInMap(optimizedAddresses)
            } catch (e: UninitializedPropertyAccessException) {
                AddressUtils.showToast(this, "Оптимизируйте маршрут.")
            }
        }

        routeType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedItem = parent.getItemAtPosition(position).toString()
                if (selectedItem == "Обычный маршрут")
                    AddressUtils.printAddresses(resultText, fullAddresses, 0)
                else
                    AddressUtils.printAddresses(resultText, optimizedAddresses, 1)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Обработка случая, когда ничего не выбрано
            }
        }

        if (AddressUtils.checkPermission(this)) {
            AddressUtils.requestPermission(this)
        }

        initSocket()
        setupSocketListeners()
        connectToSocket()
    }

    override fun onStart() {
        super.onStart()
        if (!NetworkUtils.isNetworkConnected(this)) {
            NetworkUtils.showNetworkEnableDialog(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mSocket?.disconnect()
        mSocket?.close()
    }

    private fun openInMap(addresses: MutableList<Address>) {
        var uri = "https://yandex.ru/maps/?ll=mode=routes&rtext="
        if (addresses.isEmpty()) {
            AddressUtils.showToast(this, "Нет адресов.")
        } else {
            addresses.forEach { address ->
                val latitude = address.latitude
                val longitude = address.longitude

                uri += "$latitude%2C$longitude~"

            }
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
        }
    }


    private fun initSocket() {
        try {
//            val uri = "http://192.168.214.132:8080"  // mobile internet
            val uri = "http://192.168.0.103:8080"  // home wifi
            mSocket = IO.socket(uri)
        } catch (e: URISyntaxException) {
            AddressUtils.showToast(this, "Unable to connect to server")
        }
    }

    private fun setupSocketListeners() {
        mSocket?.on(Socket.EVENT_CONNECT, onConnect)
        mSocket?.on(Socket.EVENT_DISCONNECT, onDisconnect)
        mSocket?.on("addresses", onDataReceived)
        mSocket?.on("send_answer", onAnswerReceived)
        mSocket?.on("send_file", onSendFileReceived)
        mSocket?.on("delete_addresses", onDeleteAddressesReceived)
    }

    private fun connectToSocket() {
        mSocket?.connect()
    }

    private val onConnect: Emitter.Listener = Emitter.Listener {
        val name = "Chingiz"  // при авторизации можно логин использовать
        mSocket?.emit("send_name", name) // Отправить имя на сервер
        mSocket?.emit("check_file")
    }

    private val onDisconnect: Emitter.Listener = Emitter.Listener {
        runOnUiThread {
            Toast.makeText(applicationContext, "Disconnected from server", Toast.LENGTH_SHORT).show()
            // Дополнительные действия при разрыве соединения
        }
    }

    private val onAnswerReceived: Emitter.Listener = Emitter.Listener { args ->
        runOnUiThread {
            if (args.isNotEmpty()) {
                val results = args[0].toString()
                    .split(",")
                    .filter { it.isNotBlank() }
                    .map { it.toInt() }
                    .toList()

                optimizedAddresses.clear()
                for (i in 0 until (results.size)) {
                    val address = fullAddresses[results[i]]
                    optimizedAddresses.add(address)
                }
                val spinner: Spinner = findViewById(R.id.spinnerRouteType)
                spinner.setSelection(1)
            }
        }
    }

    private val onDataReceived: Emitter.Listener = Emitter.Listener { args ->
        runOnUiThread {
            val gson = Gson()
            allShortAddresses = gson.fromJson(args[0].toString(), Array<String>::class.java).toMutableList()
            val newAddresses = AddressUtils.receiveAddresses(this, allShortAddresses)
            fullAddresses.removeAt(0)
            fullAddresses += newAddresses
            AddressUtils.printAddresses(resultText, fullAddresses, 0)
        }
    }

    private val onSendFileReceived: Emitter.Listener = Emitter.Listener { args ->
        runOnUiThread {
            val gson = Gson()
            allShortAddresses = gson.fromJson(args[0].toString(), Array<String>::class.java).toMutableList()
            fullAddresses = AddressUtils.receiveAddresses(this, allShortAddresses)
            AddressUtils.printAddresses(resultText, fullAddresses, 0)
        }
    }

    private val onDeleteAddressesReceived: Emitter.Listener = Emitter.Listener { args ->
        runOnUiThread {
            try {
                val gson = Gson()
                val addressesToDelete =
                    gson.fromJson(args[0].toString(), Array<Int>::class.java).toList()
                addressesToDelete.forEach { row ->
                    fullAddresses.removeAt(row)
                }
                AddressUtils.printAddresses(resultText, fullAddresses, 0)
            } catch (e: Exception) {
                AddressUtils.showToast(this, e.toString())
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERMISSIONS_REQUEST_LOCATION || grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Разрешение на использование геолокации")
                .setMessage("Это приложение требует разрешение на использование геолокации, чтобы определить ваше текущее местоположение. Чтобы продолжить, пожалуйста, разрешите использование геолокации.")
                .setPositiveButton("ОК") { _, _ ->
                    AddressUtils.requestPermission(this)
                }
                .setNegativeButton("Отмена") { _, _ -> }
                .show()
        }
        //            AddressUtils.showToast(this, "Разрешение успешно выдано")
    }

    companion object {
        private const val PERMISSIONS_REQUEST_LOCATION = 1
    }
}