package com.programovil.misservicios1

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface DirectionsApiService {
    @GET("directions/json")
    fun getDirections(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("key") apiKey: String
    ): Call<DirectionsResponse>
    data class DirectionsResponse(
        val routes: List<Route>,
        val status: String
    )

    data class Route(
        val legs: List<Leg>
    )

    data class Leg(
        val steps: List<Step>,
        val distance: Value,
        val duration: Value
    )

    data class Step(
        val polyline: Polyline
    )

    data class Polyline(
        val points: String
    )

    data class Value(
        val text: String,
        val value: Int
    )
}