package com.example.kitchenmind.data.catalog

/** Uygulama kataloğu: ürün adı serbest yazılmaz; yalnızca listeden seçim. */
data class CatalogProduct(val displayName: String, val defaultUnit: String)

object KitchenCatalog {

    val products: List<CatalogProduct> = listOf(
        CatalogProduct("Elma", "adet"),
        CatalogProduct("Armut", "adet"),
        CatalogProduct("Muz", "adet"),
        CatalogProduct("Portakal", "adet"),
        CatalogProduct("Limon", "adet"),
        CatalogProduct("Domates", "kg"),
        CatalogProduct("Salatalık", "adet"),
        CatalogProduct("Patlıcan", "adet"),
        CatalogProduct("Kabak", "adet"),
        CatalogProduct("Biber (dolmalık)", "adet"),
        CatalogProduct("Sivri biber", "adet"),
        CatalogProduct("Soğan", "kg"),
        CatalogProduct("Sarımsak", "diş"),
        CatalogProduct("Patates", "kg"),
        CatalogProduct("Havuç", "kg"),
        CatalogProduct("Ispanak", "demet"),
        CatalogProduct("Maydanoz", "demet"),
        CatalogProduct("Dereotu", "demet"),
        CatalogProduct("Nane", "demet"),
        CatalogProduct("Marul", "adet"),
        CatalogProduct("Roka", "demet"),
        CatalogProduct("Tavuk göğsü", "kg"),
        CatalogProduct("Tavuk but", "kg"),
        CatalogProduct("Kıyma (dana)", "kg"),
        CatalogProduct("Kıyma (kuzu)", "kg"),
        CatalogProduct("Dana eti", "kg"),
        CatalogProduct("Kuzu eti", "kg"),
        CatalogProduct("Balık", "kg"),
        CatalogProduct("Yumurta", "adet"),
        CatalogProduct("Süt", "L"),
        CatalogProduct("Yoğurt", "kg"),
        CatalogProduct("Peynir (beyaz)", "kg"),
        CatalogProduct("Kaşar peyniri", "kg"),
        CatalogProduct("Lor peyniri", "kg"),
        CatalogProduct("Tereyağı", "g"),
        CatalogProduct("Margarin", "g"),
        CatalogProduct("Krema", "ml"),
        CatalogProduct("Zeytinyağı", "L"),
        CatalogProduct("Sıvı yağ", "L"),
        CatalogProduct("Ayçiçek yağı", "L"),
        CatalogProduct("Un", "kg"),
        CatalogProduct("Pirinç", "kg"),
        CatalogProduct("Bulgur", "kg"),
        CatalogProduct("Makarna", "paket"),
        CatalogProduct("Erişte", "paket"),
        CatalogProduct("Şeker", "kg"),
        CatalogProduct("Tuz", "g"),
        CatalogProduct("Karabiber", "g"),
        CatalogProduct("Pul biber", "g"),
        CatalogProduct("Kimyon", "g"),
        CatalogProduct("Kekik", "g"),
        CatalogProduct("Nohut (kuru)", "kg"),
        CatalogProduct("Mercimek (kırmızı)", "kg"),
        CatalogProduct("Mercimek (yeşil)", "kg"),
        CatalogProduct("Fasulye (kuru)", "kg"),
        CatalogProduct("Barbunya", "kg"),
        CatalogProduct("Domates salçası", "g"),
        CatalogProduct("Biber salçası", "g"),
        CatalogProduct("Sirke", "L"),
        CatalogProduct("Limon suyu (şişe)", "ml"),
        CatalogProduct("Bal", "g"),
        CatalogProduct("Reçel", "g"),
        CatalogProduct("Çay", "g"),
        CatalogProduct("Kahve", "g"),
        CatalogProduct("Ekmek", "adet"),
        CatalogProduct("Yufka", "paket"),
        CatalogProduct("Börek peyniri", "kg"),
    )

    fun filter(query: String): List<CatalogProduct> {
        val q = query.trim()
        if (q.isEmpty()) return products
        return products.filter { it.displayName.contains(q, ignoreCase = true) }
    }
}
