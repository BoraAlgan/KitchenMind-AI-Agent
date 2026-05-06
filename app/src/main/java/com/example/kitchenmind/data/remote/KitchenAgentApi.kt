package com.example.kitchenmind.data.remote

import com.example.kitchenmind.data.remote.dto.OrderFlowStepRequestDto
import com.example.kitchenmind.data.remote.dto.OrderFlowStepResponseDto
import com.example.kitchenmind.data.remote.dto.SuggestRequestDto
import com.example.kitchenmind.data.remote.dto.SuggestResponseDto
import retrofit2.http.Body
import retrofit2.http.POST

interface KitchenAgentApi {

    @POST("api/v1/agent/suggest")
    suspend fun suggest(@Body body: SuggestRequestDto): SuggestResponseDto

    //7. adım
    //mesajı bu interface api endpointi üzerinden atıyoruz. BACKEND TARAFI KARŞILIYOR BİZİ BU NOKTADA
    @POST("api/v1/order-flow/step")
    suspend fun orderFlowStep(@Body body: OrderFlowStepRequestDto): OrderFlowStepResponseDto
}
