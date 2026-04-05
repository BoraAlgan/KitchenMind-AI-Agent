package com.example.kitchenmind.data.remote

import com.example.kitchenmind.data.remote.dto.SuggestRequestDto
import com.example.kitchenmind.data.remote.dto.SuggestResponseDto
import retrofit2.http.Body
import retrofit2.http.POST

interface KitchenAgentApi {

    @POST("api/v1/agent/suggest")
    suspend fun suggest(@Body body: SuggestRequestDto): SuggestResponseDto
}
