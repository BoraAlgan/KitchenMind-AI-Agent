package com.example.kitchenmind.data.remote

/**
 * LLM ara sıra makine etiketi / ham JSON sızıntısı gönderir; sohbet balonunda göstermeden önce sadeleştirir.
 */
object AgentMessageCleaner {
    private val lineNoise =
        listOf(
            Regex("""(?i)^\s*\*{0,2}\s*MISSING_ITEMS_JSON\s*:.*"""),
            Regex("""(?i)^\s*\*{0,2}\s*CONSUMPTION_JSON\s*:.*"""),
            Regex("""(?i)^\s*MISSING_ITEMS_JSON\s*:.*"""),
            Regex("""(?i)^\s*CONSUMPTION_JSON\s*:.*"""),
            Regex("""(?i)^\s*\*{0,2}\s*Eksik\s+Malzemeler\s*:?\s*\*{0,2}\s*$"""),
            Regex("""(?i)^\s*\*{0,2}\s*Stok\s+Düşümü\s*:?\s*\*{0,2}\s*$"""),
            // Model bazen tek satırda boş başlıklar birleştirir
            Regex("""(?i)^\s*\*{0,2}\s*Eksik\s+Malzemeler\s*:\s*\*{0,2}\s*Stok\s+Düşümü\s*:?\s*\*{0,2}\s*$"""),
            Regex("""(?i)^\s*Eksik\s+Malzemeler\s*:\s*Stok\s+Düşümü\s*:?\s*$"""),
        )

    fun forDisplay(raw: String): String {
        val lines =
            raw.lines().filter { line ->
                lineNoise.none { it.matches(line.trim()) }
            }
        return lines.joinToString("\n").trim().replace(Regex("\n{3,}"), "\n\n")
    }
}
