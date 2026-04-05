package com.example.kitchenmind.util

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/**
 * Siparişten envantere eklerken kullanılan **tahmini** SKT (raf ömrü).
 * Gerçek ambalaj/tarih bilgisi değildir; SKT uyarıları ve asistan bağlamı için yaklaşık değer.
 */
object EstimatedShelfLife {

    private val trLocale = Locale.forLanguageTag("tr")

    /** Eşleşme yoksa kullanılan gün sayısı. */
    const val DEFAULT_DAYS = 7

    /**
     * Ürün adına göre bugünden itibaren kaç gün sonra SKT sonu kabul edileceği.
     * Özgün / uzun ömürlü kalemler genel sebzelerden önce kontrol edilir.
     */
    fun shelfLifeDaysForProductName(name: String): Int {
        val n = name.trim().lowercase(trLocale)
        if (n.isEmpty()) return DEFAULT_DAYS

        // Deniz / balık
        if (n.contains("balık") || n.contains("balik") || n.contains("somon") ||
            n.contains("ton") || n.contains("karides") || n.contains("hamsi") ||
            n.contains("midye") || n.contains("levrek") || n.contains("çupra") ||
            n.contains("cupra")
        ) {
            return 3
        }
        // Et / kümes
        if (n.contains("tavuk") || n.contains("hindi") || n.contains("köfte") ||
            n.contains("kofte") || n.contains("kıyma") || n.contains("kiyma") ||
            n.contains("dana eti") || n.contains("kuzu eti") || n.contains("sucuk") ||
            n.contains("sosis") || n.contains("pastırma") || n.contains("pastirma")
        ) {
            return 5
        }
        // Süt ürünleri
        if (n.contains("süt") || n.contains("sut") || n.contains("yoğurt") ||
            n.contains("yogurt") || n.contains("ayran") || n.contains("kefir") ||
            n.contains("krema")
        ) {
            return 5
        }
        // Salça / konserve (taze domatesten önce)
        if (n.contains("salçası") || n.contains("salçasi") || n.contains("salça") ||
            n.contains("salca") || n.contains("konserve")
        ) {
            return 120
        }
        if (n.contains("limon suyu")) return 90
        if (n.contains("lor peynir") || (n.contains("beyaz") && n.contains("peynir"))) return 7
        if (n.contains("peynir") || n.contains("kaşar") || n.contains("kasar")) return 10
        if (n.contains("tereyağı") || n.contains("tereyagi")) return 30
        if (n.contains("margarin")) return 45
        // Yeşillik
        if (n.contains("marul") || n.contains("roka") || n.contains("ıspanak") ||
            n.contains("ispanak") || n.contains("maydanoz") || n.contains("dereotu") ||
            n.contains("nane") || n.contains("tere")
        ) {
            return 4
        }
        // Baharat (içinde "biber" geçer)
        if (n.contains("karabiber") || n.contains("pul biber") || n.contains("kimyon") ||
            n.contains("kekik")
        ) {
            return 90
        }
        // Taze sebze / meyve
        if (n.contains("çilek") || n.contains("cilek") || n.contains("kiraz") ||
            n.contains("üzüm") || n.contains("uzum") || n.contains("muz") ||
            n.contains("avokado") || n.contains("domates") || n.contains("salatalık") ||
            n.contains("salatalik") || n.contains("patlıcan") || n.contains("patlican") ||
            n.contains("kabak") || n.contains("sivri biber") || n.contains("dolmalık") ||
            n.contains("dolmalik") || n.contains("biber (")
        ) {
            return 6
        }
        // Kabuklu meyve
        if (n.contains("elma") || n.contains("armut") || n.contains("portakal") ||
            n.contains("mandalina") || n.contains("limon") || n.contains("greyfurt") ||
            n.contains("nar")
        ) {
            return 14
        }
        // Kök
        if (n.contains("patates") || n.contains("soğan") || n.contains("sogan") ||
            n.contains("havuç") || n.contains("havuc") || n.contains("sarımsak") ||
            n.contains("sarimsak")
        ) {
            return 21
        }
        // Fırın
        if (n.contains("ekmek") || n.contains("simit") || n.contains("poğaça") ||
            n.contains("pogaca") || n.contains("börek") || n.contains("borek") ||
            n.contains("yufka")
        ) {
            return 4
        }
        if (n.contains("yumurta")) return 18
        // Kuru / ambalajlı
        if (n.contains("pirinç") || n.contains("pirinc") || n.contains("bulgur") ||
            n.contains("makarna") || n.contains("erişte") || n.contains("eriste") ||
            n.contains(" un") || n.startsWith("un ") || n == "un" || n.endsWith(" un") ||
            n.contains("şeker") || n.contains("seker") || n.contains("tuz") ||
            n.contains("nohut") || n.contains("mercimek") || n.contains("fasulye") ||
            n.contains("barbunya") || n.contains("sirke") || n.contains("zeytinyağı") ||
            n.contains("zeytinyagi") || n.contains("sıvı yağ") || n.contains("sivi yag") ||
            n.contains("ayçiçek") || n.contains("aycicek") ||
            n.contains("çay") || n.contains("cay") || n.contains("kahve") ||
            n.contains("reçel") || n.contains("recel")
        ) {
            return 90
        }
        if (n == "bal" || n.startsWith("bal ") || n.endsWith(" bal")) return 365

        return DEFAULT_DAYS
    }

    /**
     * Sipariş anına göre, tahmini son kullanma gününün **yerel saat diliminde gün sonu** (23:59:59.999…).
     */
    fun estimatedExpiryMillisAtEndOfDay(
        productName: String,
        orderedAtMillis: Long = System.currentTimeMillis(),
    ): Long {
        val days = shelfLifeDaysForProductName(productName)
        val zone = ZoneId.systemDefault()
        val baseDate = Instant.ofEpochMilli(orderedAtMillis).atZone(zone).toLocalDate()
        val expiryDate = baseDate.plusDays(days.toLong())
        return expiryDate
            .atTime(LocalTime.MAX)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }
}
