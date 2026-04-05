package com.example.kitchenmind.data.remote

import com.example.kitchenmind.data.remote.dto.SuggestRequestDto
import com.example.kitchenmind.data.remote.dto.SuggestResponseDto
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.HttpException

class AgentRepository(
    private val api: KitchenAgentApi,
) {

    suspend fun suggest(request: SuggestRequestDto): Result<SuggestResponseDto> =
        runCatching { api.suggest(request) }

    fun humanReadableError(throwable: Throwable): String = when (throwable) {
        is HttpException -> {
            val detail = throwable.response()?.errorBody()?.string()?.let(::parseFastApiDetail)
            when (throwable.code()) {
                503 -> detail ?: "Sunucu hazır değil (LLM/API anahtarı veya yapılandırma)."
                504 -> detail ?: "İstek zaman aşımına uğradı."
                502 -> detail ?: "Sunucu hatası (CrewAI/LLM)."
                in 400..499 -> detail ?: "İstek reddedildi (${throwable.code()})."
                else -> detail ?: "HTTP ${throwable.code()}"
            }
        }
        is IOException -> "Ağ hatası: sunucuya ulaşılamıyor."
        else -> throwable.message ?: "Bilinmeyen hata"
    }

    private fun parseFastApiDetail(json: String): String? = runCatching {
        val root = JSONObject(json)
        when (val d = root.opt("detail")) {
            is String -> d
            is JSONArray -> buildString {
                for (i in 0 until d.length()) {
                    val el = d.opt(i)
                    val piece = when (el) {
                        is JSONObject -> el.optString("msg").takeIf { it.isNotBlank() } ?: el.toString()
                        else -> el?.toString().orEmpty()
                    }
                    if (piece.isNotBlank()) {
                        if (isNotEmpty()) append(' ')
                        append(piece)
                    }
                }
            }.takeIf { it.isNotBlank() }
            else -> null
        }
    }.getOrNull()
}
