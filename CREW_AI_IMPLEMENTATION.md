# KitchenMind — CrewAI uygulaması

Bu belge, mutfak envanteri uygulamasına (Ödev 2) eklenen CrewAI tarafını özetler. Amaç: envanter verisine göre metin üretmek; tarif modunda ayrıca yapılandırılmış alanlar (eksik malzeme listesi, stok düşümü) dönmek.

## Ödev 2 ile ilişki

Önceki çalışmada Android tarafında envanter, SKT ve temel ekranlar vardı. Bu aşamada modeller telefonda çalışmıyor; **FastAPI** ile bir sunucu eklendi ve CrewAI bu sunucuda çağrılıyor. Uygulama, envanter listesini JSON ile gönderip yanıtı Asistan ekranında gösteriyor.

## Mimari (kısa)

1. Android `InventoryViewModel`, `POST /api/v1/agent/suggest` isteği atar (envanter satırları + mod: sohbet / SKT özeti / tarif).
2. `app/main.py` isteği alır, `crew_kitchen.run_kitchen_suggestion` içinde Crew çalıştırır.
3. Dönen metin ve varsa `consumption` / `missing_items` alanları uygulamada işlenir (onaylı stok düşümü, sepete eksik ekleme vb.).

## Ajanlar

| Ajan | Rol | Ne yapıyor |
|------|-----|------------|
| Envanter analisti | `Envanter Analisti` | Gelen envanter metnini ve kullanıcı notunu kullanarak SKT / öncelik özeti yazar (Türkçe). |
| Mutfak şefi | `Mutfak Şefi` | Tarif modunda analist çıktısına ve envantere göre yemek önerisi yazar; çıktının sonunda `MISSING_ITEMS_JSON` ve `CONSUMPTION_JSON` satırları üretmesi istenir (uygulama bunları ayrıştırır). |
| Sohbet asistanı | `Mutfak sohbet asistanı` | Sadece sohbet modunda; kısa yanıt, zorunlu değilse envanteri uzun uzun tekrarlamaz. |

Kodda tanımlar: `backend/app/crew_kitchen.py` (`Agent(...)`, `Task(...)`, `Crew(...)`).

## Görevler ve sıra

- **Sohbet (`chat`):** Tek ajan, tek görev, `crew.kickoff()`.
- **SKT / envanter özeti (`inventory_only`):** Tek ajan (analist), tek görev, `kickoff()`. Süresi dolmuş ürünler için sunucu tarafında kısa bir özet de eklenir; model bazen yanlış öncelik verse bile kullanıcı doğru uyarıyı görür.
- **Tarif (`recipe`):** İki görev sırayla: önce analist özeti, sonra şef tarifi + JSON. `Process.sequential`, `context=[analyze_task]` ile şef görevi analiste bağlanır.

`kickoff()` çağrısı `crew_kitchen.py` içindeki `_run_chat_mode`, `_run_inventory_only` ve `_run_recipe_crew` fonksiyonlarında yapılıyor.

## Yapılandırma

- `backend/requirements.txt` — FastAPI, uvicorn, crewai, langchain-openai, tzdata (Windows’ta saat dilimi için).
- `backend/.env.example` — API anahtarı ve model adı örnekleri. Gerçek anahtar `.env` içinde kalır; repoya konmaz.

## Android tarafı

`SuggestRequestDto` ile envanter ve `suggestMode` gönderilir; yanıt `message` metni Asistan balonunda gösterilir. Tarif modunda gelen tüketim satırları onay sonrası Room’da güncellenir.

## Önemli dosyalar

| Dosya | İçerik |
|-------|--------|
| `backend/app/crew_kitchen.py` | Ajanlar, görevler, Crew, JSON ayrıştırma, SKT metni üretimi |
| `backend/app/main.py` | FastAPI uygulaması, `/api/v1/agent/suggest` |
| `backend/app/schemas.py` | İstek/yanıt modelleri |
| `backend/app/consumption_validate.py` | Şefin `CONSUMPTION_JSON` çıktısının stokla doğrulanması |

Kurulum ve çalıştırma adımları için `backend/README.md` dosyasına bakın.
