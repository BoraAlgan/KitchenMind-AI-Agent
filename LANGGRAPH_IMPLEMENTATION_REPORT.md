# LangGraph — KitchenMind Rapor

---

## Bu çalışmada ne var?

KitchenMind’a **LangGraph** ile bir **sipariş asistanı** ekledim. Kullanıcı doğal dilde ne almak istediğini yazıyor; sunucu bir **graf** (düğümler ve kenarlar) ile cevap üretiyor, taslak listeyi gösteriyor, onay sonrası ürünler **telefondaki envantere** kaydediliyor.  
**CrewAI** (asistan/tarif tarafı) **aynı projede** duruyor; LangGraph **ayrı bir klasörde** (`order_flow`), karışmadan çalışıyor.

---

## Akış

**Kullanıcı** → Android’de Sipariş ekranı → **sunucuya mesaj** → **LangGraph** işler → **cevap + durum** döner → onaylanınca **envanter güncellenir**.

---

## Node’lar (düğümler) — ne iş yapıyor?

Her düğüm grafın bir **adımı**. İsimleri kodda da böyle:

| Düğüm | Kısaca |
|--------|--------|
| **parse_order_lines** | Mesajı okuyup ürün satırlarını çıkarmaya çalışır; gerekirse onay/iptal anlar. |
| **emit_clarification** | “Ne almak istediğinizi net yazın” gibi **netleştirme** sorusu döner. |
| **compose_confirmation_prompt** | **Taslak listeyi** gösterir: “Bunu onaylıyor musunuz?” |
| **emit_cancellation** | Kullanıcı vazgeçtiyse **iptal** mesajı. |
| **finalize_confirmed_order** | Kullanıcı onayladıysa **sipariş alındı** mesajı. |

İlk adım her zaman **parse**; gerisi duruma göre seçiliyor.

---

## Edge’ler (kenarlar) — nasıl bağladım?

- **Başlangıç:** `START` → her seferinde önce **parse_order_lines** çalışır.

- **Parse’tan sonra** tek bir yol seçilir (koşullu kenar):
  - İptal niyeti varsa → **emit_cancellation** → bitiş
  - Onay niyeti varsa → **finalize_confirmed_order** → bitiş
  - Liste belirsizse → **emit_clarification** → bitiş
  - Liste hazırsa → **compose_confirmation_prompt** (onay sorusu) → bitiş

- Bu dört dalın sonunda graf **END** ile o tur kapanır; kullanıcı yeni mesaj yazınca yine **parse**’tan başlar (aynı sohbet için `threadId` ile).

Özet şema:

```text
START → parse_order_lines → (koşula göre) → iptal | onay | netleştir | taslak+onay sorusu → END
```

---

## LangSmith

İzleri görmek için `.env` içinde LangSmith anahtarı ve tracing açık; sunucuya bir istek atınca panelde run görünür. Detay: `backend/README.md`.

---

## Son söz

LangGraph’u **sipariş senaryosu** için kullandım: **node**’lar adımları, **edge**’ler (özellikle parse sonrası **koşullu kenar**) hangi cevabın üretileceğini belirliyor. CrewAI ayrı endpoint’te kaldı; ikisi aynı projede birlikte çalışıyor.

Kod klasörü: `backend/app/order_flow/`.
