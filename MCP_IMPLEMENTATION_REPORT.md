# MCP (Model Context Protocol) — KitchenMind Rapor

---

## Bu çalışmada ne var?

KitchenMind backend’inde **LangGraph sipariş akışı** (`backend/app/order_flow/`) için **MCP** entegrasyonu eklendi. Amaç: kullanıcı mesajından **sipariş satırlarını** (ürün adı, miktar, birim) çıkarırken, bu işi **standart bir “tool” arayüzü** üzerinden yapmak; böylece parse mantığı **MCP sunucusunda** toplanabilsin ve gerektiğinde aynı tool başka host’lardan da çağrılabilsin.

**CrewAI** (asistan / tarif, `POST /api/v1/agent/suggest`) bu rapordaki MCP hattını kullanmaz; MCP entegrasyonu **sipariş LangGraph** tarafındadır.

---

## Sipariş ekranı → LangGraph bağlantısı

Uygulamada **Sipariş** sekmesindeki **“AI ile sipariş”** akışı, Android tarafından backend’e gider: **`POST /api/v1/order-flow/step`**. Bu uç nokta LangGraph grafiğini çalıştırır; kullanıcının yazdığı metin graf durumuna (`messages`) eklenir ve ilk düğüm olan **`parse_order_lines`** üzerinden işlenir.

MCP burada **ayrı bir ekran veya ayrı bir servis değildir**; aynı sipariş API’sinin içinde, **parse düğümünün** (`run_structured_parse`) ürettiği **taslak satırlar** (`draft_lines`) ve **netleştirme / onay** bayraklarını hazırlamaya yardımcı olur. Grafın geri kalanı (**taslak gösterme, onay sorusu, iptal, finalize**) yine mevcut **node’lar ve koşullu kenarlar** ile çalışmaya devam eder.

---

## Parse içinde MCP tool’un rolü

Kullanıcıdan gelen serbest metin (ör. `2 elma, 1 armut` veya `3 litre süt, 5 kilo dana eti`) önce **ürün satırlarına** dönüştürülür: isim, miktar, birim. Bu dönüşümün bir kısmı MCP tarafında **`draft_order_from_text`** tool’u ile yapılır; tool çıktısı `OrderParseResult` ile uyumlu alanlara map edilir ve LangGraph state’ine **`_result_to_state_patch`** ile yazılır.

Böylece **sipariş diyaloğu** (hangi node’a gidileceği: netleştirme mi, onay mı, iptal mi) **LangGraph routing** ile aynı kalır; değişen şey çoğunlukla **satırların nasıl üretildiği**dir.

---

## Switch (yapılandırma) ve fallback (yedek parse) yapısı

Burada “switch” pratikte **ortam değişkeni** ile açılıp kapanan bir yoldur: **`ORDER_FLOW_USE_MCP`**. LangGraph’ın kendisi kapanmaz; istek geldiyse graf yine çalışır.

**Doğru anlayış:**

- **LangGraph çalışmazsa MCP devreye girer** ifadesi bu projede **geçerli değildir**. LangGraph, API çağrısı başarılı olduğu sürece akışı yürütür. MCP, **parse kaynağı** için ek bir seçenektir.
- **MCP çalışmazsa veya kullanılmıyorsa** aynı `parse_order_lines` düğümü içinde **mevcut yollar** devreye girer; **diğer node’lar** (`emit_clarification`, `compose_confirmation_prompt`, vb.) yine seçilir.

**`run_structured_parse` içindeki sıra (özet):**

1. **Onay / iptal kısayolları** (regex ile `evet`, `hayır` vb.) — bunlar MCP’den bağımsız; uygunsa doğrudan patch döner.
2. **`ORDER_FLOW_USE_MCP` açıksa** → `_mcp_parse_lines` → MCP tool çağrısı. **Başarılıysa** patch üretilir ve fonksiyon **burada biter** (aynı turda LLM parse’a düşülmez).
3. **MCP kapalıysa** veya **`_mcp_parse_lines` `None` döndürdüyse** (ör. env kapalı, istisna, boş sonuç) → sıradaki yollar:
   - LLM yapılandırılmamışsa → **`_fallback_parse_lines`** (kural tabanlı bölme),
   - LLM varsa → **structured LLM parse**,
   - LLM hata verirse → yine **`_fallback_parse_lines`**.

Sonuç her durumda **aynı state alanlarına** (`draft_lines`, `needs_clarification`, …) yazılabildiği için **LangGraph’un koşullu kenarı** (`route_after_parse`) aynı mantıkla çalışır: taslak hazırsa onay sorusu, belirsizse netleştirme, onay/iptal ise ilgili finalize/cancel node’ları.

---

## MCP mimarisi

MCP’yi derste anlatılan **Host — Client — Server** üçlüsüyle düşünmek en net yöntemdir. KitchenMind’da amaç, “model her şeyi prompt içinde çözsün” yerine, **dış dünyaya dokunan işleri** (burada: metinden sipariş satırı çıkarma) **sözleşmeli bir araç katmanına** taşımaktır. Sunumda da vurgulanan fikir budur: LLM veya diyalog motoru **ne yapılabileceğini** MCP sunucusunun sunduğu **tool listesi** üzerinden görür; gerçek iş kuralları ve normalleştirme mantığı **sunucu tarafında** toplanabilir.

**Host (sunucu uygulaması + diyalog grafiği)**  
Sipariş tarafında “host”, kullanıcı mesajını alıp LangGraph ile adımları yürüten backend akışıdır. Yani kullanıcı uygulamada yazdığında, önce **aynı API ve aynı graf** devrededir; MCP burada grafı iptal etmez, **ilk parse anında** devreye girip çıkabilen bir **yardımcı katmandır**.

**MCP Client (host içindeki istemci)**  
Host, ihtiyaç duyduğunda MCP protokolüyle konuşan ince bir istemci açar ve “şu tool’u şu parametrelerle çalıştır” der. Bu projede iletişim **stdio** üzerinden kurulur: pratikte ayrı bir süreç olarak MCP sunucusu başlatılır, sonuç JSON benzeri yapı olarak geri gelir ve **sipariş state’ine** (taslak satırlar, belirsizlik bayrakları) dönüştürülür. Sunumda gösterilecek “süreç” kısmı tam burasıdır: **çağrı öncesi → tool adı → dönen yapılandırılmış çıktı**.

**MCP Server (araçları sunan taraf)**  
Sunucu tarafında “sipariş metnini satırlara çevir” ve “envanter için hızlı özet” gibi **isimlendirilmiş yetenekler** bulunur. Bu sayede hocaya şunu rahatça söyleyebilirsiniz: MCP, uygulamanın geri kalanını bilmez; sadece **tanımlı arayüzlerle** konuşur. İleride başka bir kaynak (fiyat API’si, tedarikçi servisi) eklenecekse bile aynı desen korunur: **yeni tool**, aynı protokol.

**Neden bu mimari sunum için uygundur?**  
Bir yanda **LangGraph**: “şimdi netleştir, şimdi onay iste, şimdi finalize et” gibi **diyalog akışını** yönetir. Diğer yanda **MCP**: “metni satıra dök” gibi **tek bir işi** güvenilir ve tekrarlanabilir şekilde yapar. İkisini karıştırmamak önemlidir: biri **orkestrasyon**, diğeri **araç erişimi**. Rapor ve canlı gösterimde bu cümle, teknik detaydan çok **mantıksal ayrımı** netleştirir.

**Özet tablo (roller)**

| Rol | KitchenMind sipariş bağlamında |
|-----|----------------------------------|
| **Host** | Sipariş isteğini alan ve LangGraph ile diyaloğu sürdüren backend akışı. |
| **MCP Client** | Parse anında MCP sunucusuna bağlanıp `draft_order_from_text` gibi tool’ları çağıran katman. |
| **MCP Server** | Sipariş/ envanter ile ilgili tool’ları sunan ayrı süreç; birim normalleştirme gibi kurallar burada toplanabilir. |

İletişim biçimi: **stdio** (sunucu süreci `python -m app.mcp_server` ile ayağa kalkar).

---

## Akış (süreç)

1. Android veya istemci → `POST /api/v1/order-flow/step` (`backend/app/main.py`).
2. `backend/app/order_flow/runner.py` → LangGraph `graph.invoke(...)`.
3. `backend/app/order_flow/graph.py` → `START` → `parse_order_lines`.
4. `backend/app/order_flow/nodes.py` → `node_parse_order_lines` → `run_structured_parse(state)`.
5. `backend/app/order_flow/parsing.py` → `ORDER_FLOW_USE_MCP=1` ise `_mcp_parse_lines` → MCP tool `draft_order_from_text`.
6. Tool çıktısı → `OrderParseResult` → `_result_to_state_patch` → state patch döner.
7. `route_after_parse` → `emit_clarification` | `compose_confirmation_prompt` | `finalize_confirmed_order` | `emit_cancellation` (mevcut LangGraph davranışı aynı).
8. `runner.py` → `OrderFlowStepResponse` ile istemciye yanıt.

Özet şema:

```text
API → runner (invoke) → graph (parse node) → parsing (MCP tool?) → state patch → route → sonraki node → API yanıtı
```

---

## Tool’lar (`mcp_server.py`)

| Tool | Görevi |
|------|--------|
| **draft_order_from_text** | Serbest metinden satır listesi: `name`, `quantity`, `unit`; birim eş anlamlıları (`litre`→`L`, `kilo`→`kg`, `tane`→`adet` vb.). |
| **get_inventory_summary** | Demo amaçlı envanter özeti alanları (`total_items`, `critical_items`, `expiring_soon`). |

Sipariş parse entegrasyonunda doğrudan kullanılan tool: **`draft_order_from_text`**.

---

## Yapılandırma

`backend/.env.example` ve kendi `.env` dosyanızda:

| Değişken | Anlamı |
|----------|--------|
| `ORDER_FLOW_USE_MCP` | `1` / `true` / `yes` / `on` → MCP parse yolu denenir. |
| `ORDER_FLOW_MCP_PYTHON` | MCP sunucusunu başlatacak `python` yolu (Windows’ta gerekirse tam yol). |

Bağımlılık: `backend/requirements.txt` içinde `mcp>=1.0.0`.

Detaylı API ve ortam notları: `backend/README.md`.

---

## Sonuçlar (kanıt için ne eklenebilir?)

Ödev metninde istenen **“süreç ve sonuçların görünmesi”** için rapora veya ekte şunları koyabilirsiniz:

1. **Git deposu:** [KitchenMind-AI-Agent](https://github.com/BoraAlgan/KitchenMind-AI-Agent)  
2. **MCP entegrasyon commit’i:** `d583535` — *Add MCP-backed order parsing for LangGraph flow.*  
3. **Ekran görüntüleri:** Sipariş asistanında örnek mesaj (`2 elma, 1 armut`, `3 litre süt, 5 kilo dana eti` vb.) ve dönen taslak satırları.  
4. **İsteğe bağlı karşılaştırma:** `ORDER_FLOW_USE_MCP=0` ile aynı girişte farklı parse çıktısı (fallback davranışı).

---

## Son söz

MCP, LangGraph’ın **node/edge iskeletini değiştirmeden**, sadece **parse adımında** harici bir **tool sunucusu** ile yapılandırılmış çıktı üretmeyi sağlar. **`ORDER_FLOW_USE_MCP`** ile açılıp kapanan bir **switch** vardır; MCP üretemezse veya kapalıysa **LLM veya kural tabanlı fallback** aynı fonksiyon içinde devreye girer, grafın geri kalanı çalışmaya devam eder. Böylece ödev kapsamında hem **protokolün kullanımı** hem de **KitchenMind sipariş senaryosuyla uyum** gösterilmiş olur.

Kod: `backend/app/mcp_server.py`, `backend/app/order_flow/parsing.py`.
