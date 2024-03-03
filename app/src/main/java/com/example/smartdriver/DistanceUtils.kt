package com.example.smartdriver

import android.location.Address
import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.MapboxDirections
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.geojson.Point
import io.socket.client.Socket
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.concurrent.CompletableFuture

object DistanceUtils {
    private lateinit var distanceMatrix: MutableList<MutableList<Long>>

    @RequiresApi(Build.VERSION_CODES.N)
    internal fun getDistances(
        fullAddresses: List<Address>,
        mSocket: Socket?
    ) {
        getWithCallback(object : DistanceMatrixCallback {
            override fun onDistanceMatrixReady(matrix: MutableList<MutableList<Long>>) {
            }
        }, fullAddresses, mSocket)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun getWithCallback(
        callback: DistanceMatrixCallback,
        fullAddresses: List<Address>,
        mSocket: Socket?
    ) {
        val size = fullAddresses.size
        distanceMatrix = MutableList(size) { MutableList(size) { 0 } }

        val completableFutures = mutableListOf<CompletableFuture<Unit>>()

        for (i in 0 until size) {
            for (j in i + 1 until size) {
                val origin = Point.fromLngLat(
                    fullAddresses[i].longitude,
                    fullAddresses[i].latitude
                )
                val destination = Point.fromLngLat(
                    fullAddresses[j].longitude,
                    fullAddresses[j].latitude
                )
                val client = createDirectionsClient(origin, destination)
                val request = client.build()

                val future = CompletableFuture<Unit>()
                completableFutures.add(future)

                sendDirectionsRequest(request, i, j, object : DistanceMatrixCallback {
                    override fun onDistanceMatrixReady(matrix: MutableList<MutableList<Long>>) {
                        distanceMatrix = matrix
                        callback.onDistanceMatrixReady(matrix)
                        future.complete(Unit)
                    }
                })
            }
        }

        CompletableFuture.allOf(*completableFutures.toTypedArray()).thenRun {
            val gson = Gson()
            val jsonString = gson.toJson(distanceMatrix)
            mSocket?.emit("send_distance_matrix", jsonString)
        }
    }

    private fun createDirectionsClient(
        origin: Point,
        destination: Point
    ): MapboxDirections.Builder {
        return MapboxDirections.builder()
            .accessToken("pk.eyJ1Ijoic3BhaWdvdSIsImEiOiJjbGhuaXE2dXExN2oyM2xvZGZhb2N1aGN6In0.bCCWCWjuguSzt77GFemVzQ")
            .origin(origin)
            .destination(destination)
            .overview(DirectionsCriteria.OVERVIEW_FALSE)
            .profile(DirectionsCriteria.PROFILE_DRIVING)
    }

    private fun sendDirectionsRequest(
        request: MapboxDirections,
        i: Int,
        j: Int,
        callback: DistanceMatrixCallback
    ) {
        request.enqueueCall(object : Callback<DirectionsResponse> {
            override fun onResponse(
                call: Call<DirectionsResponse>,
                response: Response<DirectionsResponse>
            ) {
                if (response.isSuccessful) { // выбрать routes с наименьшим временем поездки
                    val distance = response.body()?.routes()?.get(0)?.distance() ?: 0.0
                    distanceMatrix[i][j] = distance.toLong()
                    distanceMatrix[j][i] = distance.toLong()
                    callback.onDistanceMatrixReady(distanceMatrix)
                }
            }

            override fun onFailure(call: Call<DirectionsResponse>, t: Throwable) {
//                AddressUtils.showToast(this@MainActivity,"Не удалось получить маршрут")
            }
        })
    }

    interface DistanceMatrixCallback {
        fun onDistanceMatrixReady(matrix: MutableList<MutableList<Long>>)
    }
}
