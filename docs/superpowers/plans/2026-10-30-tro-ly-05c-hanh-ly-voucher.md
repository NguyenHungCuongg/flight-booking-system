# Trợ lý Bước 5c: Hành lý và voucher Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Khách mua thêm hành lý ký gửi và dùng một mã giảm giá trong cùng lượt đặt vé. Thẻ duyệt hiện tiền hành lý của từng chiều và khoản giảm giá. `is_done` kiểm hành lý của từng hành khách trên từng chiều, và mã voucher của booking đọc lại.

**Architecture:** `propose_hold` nhận thêm `baggage_kg` (một mức cho mọi người lớn và trẻ em, trên mọi chiều) và `voucher_code`. Mức hành lý đổi thành `baggageOptionId` theo bảng giá của hãng từng chiều; harness đọc bảng giá bằng `GET /api/airlines/{code}/baggage-options` và cache theo hãng. Khi chưa mua hành lý, kết quả `pending_approval` kèm `baggage_options` để model hỏi khách ngay trong câu trả lời có thẻ duyệt. Voucher chỉ chuyển cho backend kiểm (BR-30); lỗi trả về model dạng `backend_error`.

**Tech Stack:** Python, LangChain, pytest với `httpx.MockTransport` (agent); Next.js 16, TypeScript (frontend).

**Spec:** [docs/superpowers/specs/2026-10-26-tro-ly-dat-ve-agent-design.md](../specs/2026-10-26-tro-ly-dat-ve-agent-design.md) · Lộ trình: [Bước 5](2026-10-27-tro-ly-dat-ve-agent-roadmap-mo-rong.md#bước-5--mở-rộng-phạm-vi-đặt-vé) · Plan trước: [5b](2026-10-30-tro-ly-05b-khu-hoi.md)

## Global Constraints

- Nhánh `feature/flight-agent`. Không sửa `backend/`.
- Khuyến nghị trước Task 1 (chủ dự án, cần backend profile `dev`): thử tay 5b, đặt khứ hồi SGN–HAN, F5 rồi bấm "Giữ chỗ". 5c mở rộng `is_done` thêm, nên `hold_mismatch` nhầm của 5b (nếu có) cần lộ ra trước.
- Đã quyết (chủ dự án, 2026-10-29): agent tự hỏi khách có thêm hành lý ký gửi không, kèm giá.
- Quyết trong plan này: hỏi hành lý **trong cùng câu trả lời có thẻ duyệt**. Model đề xuất không kèm hành lý (trừ khi khách đã nói muốn mua), rồi đọc các mức và giá trong `baggage_options`. Khách muốn thêm thì model gọi lại `propose_hold` với `baggage_kg`. Như vậy các case eval cũ vẫn kết thúc ở `proposal`.
- Quyết trong plan này: `baggage_kg` là **một mức cho mọi người lớn và trẻ em, trên mọi chiều**. Em bé không mua (BR-22). Mức khác nhau theo người hoặc theo chiều thì thêm sau. `baggage_kg` bằng `None` hoặc `0` nghĩa là không mua.
- Mức hành lý của một chiều lấy từ bảng giá của hãng chặng đầu chiều đó. Backend so `option.airlineCode()` với `itinerary.airlineCode()`, mà hãng của itinerary là hãng chặng đầu (`FlightApi`). Bảng giá đọc qua `GET /api/airlines/{code}/baggage-options` (công khai, chỉ trả mức đang bán) và cache theo hãng trong `Harness`, giữ qua các lượt như `airports`.
- Quyết trong plan này: `max_price` là `totalAmount`, số tiền khách trả: mọi hành khách, mọi chiều, gồm hành lý, sau giảm giá. Lúc tìm, `search_flights` vẫn lọc theo giá vé trước hành lý và voucher. Cách lọc này thận trọng: không bao giờ vượt ngân sách, nhưng có thể bỏ sót chuyến chỉ vừa giá sau khi trừ voucher.
- Voucher: khách tự đưa mã, vì không có API cho khách xem danh sách voucher. Agent gửi `strip().upper()` như `VoucherApi.check`, để `voucherCode` đọc lại so khớp chính xác. Mã rỗng gửi là `None`. Backend kiểm BR-30 và trả lỗi `VOUCHER_INVALID` (409, `detail` tiếng Việt), chuyển cho model dạng `backend_error` như MVP.
- `passengerIndex` của hành lý trong booking đọc lại là vị trí trong danh sách hành khách sắp theo `id` (`BookingQueries`). `is_done` so hành lý theo **cặp (hành khách, kg)**, với hành khách là bộ `PAX_KEYS` như `_people`, để không phụ thuộc thứ tự.
- Thẻ hiện mọi khoản bị tính: hành lý từng chiều (số kiện × kg = tiền hành lý của chiều đó trong báo giá) và giảm giá (mã, số tiền `discountTotal`). Tổng tiền vẫn là `totalAmount` của báo giá.
- Số liệu seed để thử tay: hành lý 15/20/25/30 kg giá 250.000/350.000/450.000/550.000 đ cho VN, VJ, QH (`V103`); voucher `WELCOME10` giảm 10%, tối đa 200.000 đ (`V104`).
- Eval (chủ dự án chọn, 2026-10-30): chạy case mới bằng `--case hanh-ly` và `--case voucher`, k=3, rồi chạy **đủ bộ** k=3. Tỉ lệ đạt không được thấp hơn mốc sau 5a (99%, 83/84 lượt). Đủ bộ khoảng 37 case × 3 lượt, tốn quota nhất từ trước tới nay.

## Review Focus

1. **`baggage_kg` hãng không bán hoặc sai dạng** (23, "hai mươi", -5): `invalid_param` nêu các mức có bán, không gọi báo giá. `0` là không mua, không bị từ chối.
2. **Hành lý đẩy tổng vượt `max_price`**: `denied`, hint nêu số tiền báo giá và gợi ý hỏi khách bỏ hành lý hay nới giá; không có thẻ duyệt.
3. **Mã voucher viết thường hoặc có khoảng trắng** (" welcome10 "): gửi `WELCOME10`, thẻ hiện đúng mã. Nếu gửi nguyên văn, `is_done` sẽ báo `hold_mismatch` nhầm vì backend trả mã chuẩn.
4. **Booking đọc lại mất hành lý, sai mức, hành lý gắn cho hành khách không tồn tại, hoặc mất voucher**: `is_done` sai, bàn giao `hold_mismatch`. Không được ném `IndexError` làm hỏng lượt duyệt.
5. **Có em bé khi mua hành lý**: em bé không có dòng hành lý, số kiện trên thẻ chỉ tính người lớn và trẻ em.

## File structure

```
agent/flight_agent/core.py     _legs có airline, propose_hold(baggage_kg), thẻ baggage, is_done so hành lý (Task 1);
                               propose_hold(voucher_code), thẻ discount, is_done so voucherCode (Task 2)
agent/tests/fakes.py           BAGGAGE, bảng giá theo hãng, báo giá và chi tiết có hành lý (Task 1); VOUCHERS (Task 2)
agent/tests/test_harness.py    Task 1, 2
agent/tests/test_chat.py       Task 1, 2
frontend/src/app/assistant/agent-api.ts       kiểu thẻ (Task 3)
frontend/src/app/assistant/assistant-chat.tsx dòng hành lý, dòng giảm giá, câu giới thiệu (Task 3)
agent/flight_agent/react.py    quy tắc prompt (Task 4)
agent/evals/cases.py, run.py   Case.baggage_kg, Case.voucher, 5 case nhóm "hành lý và voucher" (Task 4)
agent/tests/test_evals.py      Task 4
agent/README.md, roadmap       Task 4
```

---

### Task 1: Hành lý ký gửi

**Files:** `agent/flight_agent/core.py`, `agent/tests/fakes.py`, `agent/tests/test_harness.py`, `agent/tests/test_chat.py`

**Produces:**

- Mỗi chiều trong kết quả `search_flights` (một chiều và từng chiều của mục khứ hồi) có thêm khóa `"airline"`: mã hãng chặng đầu (`segments[0]["airline"]["code"]` của API).
- `Harness.baggage_prices: dict[str, list[dict]]`: mã hãng → các mức `{"id", "weightKg", "price"}`, giữ qua các lượt.
- `propose_hold(flight_ids, fare_family_id, passengers, return_flight_ids=None, return_fare_family_id=None, baggage_kg: int | None = None)`.
- Kết quả `pending_approval`: `{"status", "total"}`, thêm `"baggage_options": {direction: ["15 kg: 250.000 đ", ...]}` khi chưa mua hành lý. Mỗi chiều có bảng giá là một khóa; không chiều nào có thì bỏ khóa.
- Mỗi chiều trên thẻ `proposal` có thêm `"baggage": {"weightKg", "count", "total"} | None`.
- `fakes.BAGGAGE`: mã hãng → các mức đúng dạng `BaggageOptionResponse`. Id VJ là 11–14, VN là 21–24, QH là 31–34, tương ứng 15/20/25/30 kg, giá 250.000/350.000/450.000/550.000 (như seed `V103`).

- [ ] **Step 1: Write the failing tests**

`test_harness.py`. Sửa hai test đang so cả dict kết quả, vì kết quả nay có thêm `baggage_options`. Trong `test_family_hold_types_each_passenger_and_shows_fares` và `test_round_trip_hold_quotes_both_journeys_and_shows_each`, đổi `r == {"status": "pending_approval", "total": X}` thành `(r["status"], r["total"]) == ("pending_approval", X)`. Rồi thêm:

```python
PRICES = ["15 kg: 250.000 đ", "20 kg: 350.000 đ", "25 kg: 450.000 đ", "30 kg: 550.000 đ"]


def test_hold_offers_baggage_prices_per_direction():
    h, fake = round_trip()                                   # VN122 đi, VJ611 về: hai hãng
    r = h.execute("propose_hold", {**ROUND, "passengers": [PAX]})
    assert r["baggage_options"] == {"OUTBOUND": PRICES, "RETURN": PRICES}
    assert [j["baggage"] for j in h.cards[-1]["journeys"]] == [None, None]
    paths = {q.url.path for q in fake.requests}
    assert {"/api/airlines/VN/baggage-options", "/api/airlines/VJ/baggage-options"} <= paths


def test_baggage_goes_to_every_adult_and_child_on_every_direction():
    h, _ = ready()
    h.execute("set_constraints", {**TRIP, "max_price": 8_000_000, "children": 1, "infants": 1})
    h.execute("search_flights", {})
    r = h.execute("propose_hold", {**ROUND, "passengers": FAMILY, "baggage_kg": 20})
    assert (r["status"], r["total"]) == ("pending_approval", 5_800_000 + 4 * 350_000) and "baggage_options" not in r
    assert h.proposal.body["baggage"] == [                   # em bé (BR-22) không mua
        {"passengerIndex": 0, "direction": "OUTBOUND", "baggageOptionId": 22},
        {"passengerIndex": 1, "direction": "OUTBOUND", "baggageOptionId": 22},
        {"passengerIndex": 0, "direction": "RETURN", "baggageOptionId": 12},
        {"passengerIndex": 1, "direction": "RETURN", "baggageOptionId": 12}]
    assert [j["baggage"] for j in h.cards[-1]["journeys"]] == [{"weightKg": 20, "count": 2, "total": 700_000}] * 2


def test_baggage_weight_must_be_on_sale_or_empty():
    h, fake = searched()
    for bad in (23, "hai mươi", -5):
        r = h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, passengers=[PAX], baggage_kg=bad))
        assert r["status"] == "invalid_param" and "15, 20, 25, 30" in r["hint"]
    assert not [q for q in fake.requests if q.url.path == "/api/bookings/quote"]
    r = h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, passengers=[PAX], baggage_kg=0))
    assert r["status"] == "pending_approval" and h.proposal.body["baggage"] == []


def test_baggage_over_the_price_limit_is_denied_with_a_hint():
    h, _ = searched()                                        # tối đa 2 triệu; VN122 1.850.000 + 20 kg 350.000
    r = h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, passengers=[PAX], baggage_kg=20))
    assert r["status"] == "denied" and "2.200.000" in r["hint"] and "bỏ hành lý" in r["hint"]
    assert h.proposal is None
```

`test_chat.py`:

```python
LOOSE = {**ROUTE, "max_price": 3_000_000}
BAG = dict(flight_ids=[122], fare_family_id=1, baggage_kg=20)


def test_approval_with_baggage_holds_and_reads_back():
    fake = FakeBackend()
    chat, model, out = proposed(fake, route=LOOSE, choice=BAG)
    result = approve(chat, model, out["cards"][-1]["proposalId"], fake.backend(), TODAY)
    assert fake.holds[0]["baggage"] == [{"passengerIndex": 0, "direction": "OUTBOUND", "baggageOptionId": 22}]
    assert [(c["type"], c["totalAmount"]) for c in result["cards"]] == [("held", 2_200_000)]


def test_read_back_with_other_baggage_hands_off():
    bag = lambda js, **kw: [{**js[0], "baggage": [{**js[0]["baggage"][0], **kw}]}]
    for patch in (lambda js: [{**js[0], "baggage": []}],     # mất hành lý
                  lambda js: bag(js, weightKg=30),            # sai mức
                  lambda js: bag(js, passengerIndex=1)):      # gắn cho hành khách không có
        fake = FakeBackend(journeys_patch=patch)
        chat, model, out = proposed(fake, route=LOOSE, choice=BAG)
        result = approve(chat, model, out["cards"][-1]["proposalId"], fake.backend(), TODAY)
        assert [c["type"] for c in result["cards"]] == ["handoff"]
```

- [ ] **Step 2: Run to verify they fail**

Run: `cd agent && uv run pytest -q`
Expected: FAIL (`propose_hold` không nhận `baggage_kg`; kết quả không có `baggage_options`).

- [ ] **Step 3: Implement `fakes.py`**

- `BAGGAGE` như ở Produces, mỗi mức `{"id", "airlineCode", "weightKg", "price", "active": True}`. `GET /api/airlines/{code}/baggage-options` trả `BAGGAGE.get(code, [])`.
- `_quote(body)`: `baggageTotal` của mỗi chiều là tổng `price` của các dòng `body["baggage"]` cùng `direction`. `baggageTotal` ở mức trên là tổng các chiều, và `totalAmount = fareTotal + baggageTotal`.
- `_price(body)` trả `self._quote(body)["totalAmount"]`.
- `_detail()`: mỗi chiều thêm `"baggage": [{"passengerIndex", "weightKg", "price"}]` lấy từ các dòng hành lý cùng chiều của body (tra mức theo `baggageOptionId`), và mức trên thêm `"baggageTotal"`. `journeys_patch` áp sau cùng, như hiện tại.

- [ ] **Step 4: Implement `core.py`**

- `_legs`: thêm `"airline": segs[0]["airline"]["code"]`.
- Trường `baggage_prices` đặt cạnh `airports`, kèm chú thích "cache GET /api/airlines/{code}/baggage-options theo hãng".
- `propose_hold`:
  - Docstring thêm: "baggage_kg: số kg hành lý ký gửi mua thêm cho mỗi người lớn và trẻ em trên mỗi chiều, chọn từ baggage_options; bỏ trống nếu khách không mua."
  - Sau khi có `it`: đọc bảng giá của `j["airline"]` cho từng chiều trong `_journeys(it)` (chưa có trong cache thì GET).
  - `baggage_kg` đổi về `int`. Nếu không đổi được, hoặc âm, hoặc khác 0 mà có chiều không bán mức đó, trả `invalid_param` với hint `f"baggage_kg phải là một trong các mức {', '.join(...)} kg, hoặc bỏ trống."`. Danh sách là các mức có ở **mọi** chiều, tăng dần. Trả trước khi gọi báo giá. `0` coi như `None`.
  - `body["baggage"]`: duyệt theo chiều (thứ tự `DIRECTIONS`), rồi theo hành khách, bỏ `INFANT`. Mỗi dòng `{"passengerIndex": i, "direction": d, "baggageOptionId": id}`, với `i` là vị trí trong `built`.
  - Hint khi báo giá vượt `max_price`: có hành lý thì chèn " Hỏi khách bỏ hành lý hay nới giá tối đa." sau câu "Báo giá … vượt giá tối đa."
  - Thẻ: mỗi chiều thêm `"baggage"`. Có hành lý thì là `{"weightKg": baggage_kg, "count": số người lớn và trẻ em, "total": q["baggageTotal"]}`, không thì `None`.
  - Kết quả: có hành lý thì không có `baggage_options`. Không có thì mỗi chiều có bảng giá một khóa, giá trị `f"{kg} kg: {_money(price)}"` theo kg tăng dần.
- `is_done`: mỗi phần tử của `got` và `want` thêm thành phần thứ tư là danh sách cặp `(hành khách, kg)` đã sắp xếp, với hành khách là `tuple(p.get(k) for k in PAX_KEYS)`.
  - `got`: lấy từ `j.get("baggage") or []`, hành khách tra trong `detail["passengers"]` theo `passengerIndex`. Chỉ số không hợp lệ thì hành khách là `()`; không được ném lỗi, và `()` vẫn sắp xếp được cùng các tuple khác.
  - `want`: lấy từ các dòng `proposal.body["baggage"]` cùng chiều, hành khách tra trong `proposal.body["passengers"]`, kg là `weightKg` trên thẻ của chiều đó.
- `Constraints.to_prompt`: "giá tối đa (tổng cả booking, mọi chiều)" đổi thành "giá tối đa (tổng khách trả: mọi chiều, gồm hành lý, sau giảm giá)".
- Docstring `set_constraints`: câu về `max_price` đổi thành "max_price: số tiền tối đa khách trả cho cả booking (VND, số nguyên), gồm hành lý và sau giảm giá, bỏ trống nếu khách không nói".

- [ ] **Step 5: Run to verify they pass**

Run: `cd agent && uv run pytest -q`
Expected: tất cả PASS.

- [ ] **Step 6: Commit**

```bash
git add agent/
git commit -m "feat(agent): mua thêm hành lý ký gửi, thẻ và is_done kiểm hành lý từng chiều"
```

---

### Task 2: Mã giảm giá

**Files:** `agent/flight_agent/core.py`, `agent/tests/fakes.py`, `agent/tests/test_harness.py`, `agent/tests/test_chat.py`

**Consumes:** Task 1 `propose_hold(baggage_kg=)`, `_quote` có `baggageTotal`, `LOOSE` và `BAG` trong `test_chat.py`.

**Produces:**

- `propose_hold(..., baggage_kg=None, voucher_code: str | None = None)`, là tham số cuối.
- Body luôn có `"voucherCode"`: `str(voucher_code).strip().upper()`, hoặc `None` khi không có hay rỗng.
- Thẻ `proposal` có `"discount": {"code": str, "amount": int} | None`, với `amount` là `discountTotal` của báo giá.
- `fakes.VOUCHERS`: mã → hàm tính mức giảm từ tạm tính. Chỉ có `"WELCOME10": lambda subtotal: min(subtotal // 10, 200_000)`. `fakes.problem(status, code, errors=(), detail=None)`: `detail` mặc định vẫn là `f"Lỗi {code}"`.

- [ ] **Step 1: Write the failing tests**

`test_harness.py`:

```python
def test_voucher_code_is_sent_upper_case_and_shown_on_the_card():
    h, _ = searched()
    r = h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, passengers=[PAX], voucher_code=" welcome10 "))
    assert (r["status"], r["total"]) == ("pending_approval", 1_665_000)          # 1.850.000 - 10%
    assert h.proposal.body["voucherCode"] == "WELCOME10"
    assert h.cards[-1]["discount"] == {"code": "WELCOME10", "amount": 185_000} and h.cards[-1]["totalAmount"] == 1_665_000
    h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, passengers=[PAX], voucher_code=""))
    assert h.proposal.body["voucherCode"] is None and h.cards[-1]["discount"] is None


def test_rejected_voucher_goes_back_to_the_model():
    h, _ = searched()
    r = h.execute("propose_hold", dict(flight_ids=[122], fare_family_id=1, passengers=[PAX], voucher_code="GIAM50"))
    assert (r["status"], r["code"], r["detail"]) == ("backend_error", "VOUCHER_INVALID", "Mã giảm giá không tồn tại")
    assert h.proposal is None and h.stop_reason is None
```

`test_chat.py`:

```python
def test_voucher_applies_to_fare_and_baggage_up_to_its_cap():
    fake = FakeBackend()
    chat, model, out = proposed(fake, route=LOOSE, choice={**BAG, "voucher_code": "WELCOME10"})
    result = approve(chat, model, out["cards"][-1]["proposalId"], fake.backend(), TODAY)
    # 1.850.000 + 350.000 = 2.200.000; giảm 10% = 220.000, trần 200.000
    assert [(c["type"], c["totalAmount"]) for c in result["cards"]] == [("held", 2_000_000)]


def test_read_back_without_the_voucher_hands_off():
    fake = FakeBackend(detail_patch={"voucherCode": None})
    chat, model, out = proposed(fake, choice=dict(flight_ids=[122], fare_family_id=1, voucher_code="WELCOME10"))
    result = approve(chat, model, out["cards"][-1]["proposalId"], fake.backend(), TODAY)
    assert [c["type"] for c in result["cards"]] == ["handoff"]
```

- [ ] **Step 2: Run to verify they fail**

Run: `cd agent && uv run pytest -q`
Expected: FAIL (`propose_hold` không nhận `voucher_code`).

- [ ] **Step 3: Implement `fakes.py`**

- `problem(...)` nhận `detail` như ở Produces.
- `POST /api/bookings/quote` và `POST /api/bookings`: nếu `body.get("voucherCode")` có giá trị mà không có trong `VOUCHERS`, trả `problem(409, "VOUCHER_INVALID", detail="Mã giảm giá không tồn tại")`. Với `/api/bookings`, kiểm trước khi ghi `self.holds`.
- `_quote(body)`: `discountTotal = VOUCHERS[code](fareTotal + baggageTotal)` nếu có mã, không thì 0. `totalAmount` trừ `discountTotal`. Mỗi chiều giữ `"discount": 0`, vì không ai đọc phần chia theo chiều.
- `_detail()`: thêm `"voucherCode": body.get("voucherCode")` và `"discountTotal"` (trước `**self.detail_patch`, để patch đè được).

- [ ] **Step 4: Implement `core.py`**

- `propose_hold`:
  - Docstring thêm: "voucher_code: mã giảm giá khách đưa; bỏ trống nếu khách không có mã, không tự bịa mã."
  - `body["voucherCode"]` như ở Produces.
  - Thẻ thêm `"discount"`: có mã thì `{"code": mã, "amount": quote["discountTotal"]}`, không thì `None`.
- `is_done`: thêm điều kiện `detail.get("voucherCode") == proposal.body["voucherCode"]`.

- [ ] **Step 5: Run to verify they pass**

Run: `cd agent && uv run pytest -q`
Expected: tất cả PASS.

- [ ] **Step 6: Commit**

```bash
git add agent/
git commit -m "feat(agent): mã giảm giá khi giữ chỗ, thẻ hiện giảm giá, is_done so mã voucher"
```

---

### Task 3: Thẻ trên trang `/assistant`

**Files:** `frontend/src/app/assistant/agent-api.ts`, `frontend/src/app/assistant/assistant-chat.tsx`

**Consumes:** Task 1 `journeys[].baggage`; Task 2 `discount`.

- [ ] **Step 1: Kiểu thẻ.** Mỗi phần tử `journeys` của `proposal` thêm `baggage: { weightKg: number; count: number; total: number } | null`. Thẻ `proposal` thêm `discount: { code: string; amount: number } | null`.

- [ ] **Step 2: Vẽ thẻ.**
  - Trong `dl` của mỗi chiều, sau "Giá vé": khi `baggage` khác `null`, thêm dòng "Hành lý ký gửi" với nội dung `` `${count} kiện ${weightKg} kg = ${vnd(total)}` ``.
  - Trong `dl` chung (Hành khách, Liên hệ): khi `discount` khác `null`, thêm dòng "Giảm giá" với nội dung `` `${code}: −${vnd(amount)}` ``, đặt trước "Liên hệ".
  - Câu giới thiệu khi chat trống: "Trợ lý đặt được vé một chiều hoặc khứ hồi, hạng phổ thông, cho nhiều hành khách, kèm hành lý ký gửi và mã giảm giá, và chỉ giữ chỗ khi bạn bấm “Giữ chỗ”."

- [ ] **Step 3: Verify**

Run: `cd frontend && npm run lint && npx tsc --noEmit`
Expected: không lỗi.

Bằng tay (khi có backend profile `dev`, agent và `npm run dev`): gõ "Đặt vé SGN đi HAN ngày kia cho <họ tên, giới tính, ngày sinh>". Thẻ duyệt hiện ra, và câu trả lời hỏi có thêm hành lý không, kèm giá 15 kg 250.000 đ… Trả lời "Thêm 20kg, dùng mã WELCOME10". Thẻ mới phải có dòng "Hành lý ký gửi 1 kiện 20 kg = 350.000 đ", dòng "Giảm giá WELCOME10: −…", và tổng bằng vé cộng hành lý trừ giảm giá. Bấm "Giữ chỗ" ra thẻ "Đã giữ chỗ" cùng tổng tiền đó.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/assistant/
git commit -m "feat(assistant): thẻ duyệt hiện hành lý từng chiều và giảm giá"
```

---

### Task 4: Prompt, case eval và chạy eval

**Files:** `agent/flight_agent/react.py`, `agent/evals/cases.py`, `agent/evals/run.py`, `agent/tests/test_evals.py`, `agent/README.md`, `docs/superpowers/plans/2026-10-27-tro-ly-dat-ve-agent-roadmap-mo-rong.md`

**Consumes:** Task 1, 2 (tham số `baggage_kg`, `voucher_code`; thẻ `journeys[].baggage`; body `voucherCode`).

**Produces:** `Case.baggage_kg: int = 0` và `Case.voucher: str | None = None`, thêm sau `return_flight_ids` ở cuối dataclass. Cả hai luôn được chấm khi có đề xuất, nên case cũ (mặc định `0` và `None`) bắt được việc model tự thêm hành lý hay bịa mã.

- [ ] **Step 1: Write the failing tests** (`test_evals.py`)

```python
def test_cases_cover_baggage_and_voucher():
    group = [c for c in CASES if c.group == "hành lý và voucher"]
    assert len(group) == 5 and any(c.baggage_kg for c in group) and any(c.voucher for c in group)
    # --case hanh-ly và --case voucher chạy đúng nhóm này, không lẫn case cũ
    assert {c.name for c in CASES if "hanh-ly" in c.name or "voucher" in c.name} == {c.name for c in group}


def test_baggage_and_voucher_are_graded():
    loose = {**ROUTE, "max_price": 3_000_000}

    def hold(**extra):
        return scripted(call("set_constraints", **loose), call("search_flights"),
                        call("propose_hold", flight_ids=[122], fare_family_id=1, passengers=[PAX], **extra),
                        AIMessage("Bạn bấm Giữ chỗ trên thẻ nhé."))
    assert run_case(happy(constraints=loose, baggage_kg=20, voucher="WELCOME10"),
                    hold(baggage_kg=20, voucher_code="welcome10"))["passed"]
    r = run_case(happy(constraints=loose), hold(baggage_kg=20, voucher_code="WELCOME10"))
    assert not r["passed"]
    assert any("baggage_kg" in f for f in r["failures"]) and any("voucher" in f for f in r["failures"])
```

Trong `test_cases_are_well_formed` thêm `assert not (c.baggage_kg or c.voucher) or "proposal" in ends, c.name`.

- [ ] **Step 2: Run to verify they fail**

Run: `cd agent && uv run pytest tests/test_evals.py -q`
Expected: FAIL (`Case` không có `baggage_kg`; chưa có nhóm "hành lý và voucher").

- [ ] **Step 3: Case và cách chấm.**

`run.py::grade`, khi có đề xuất `p`:

- `got = sorted({(j["baggage"] or {}).get("weightKg", 0) for j in p.card["journeys"]})`. Khác `[case.baggage_kg]` thì thêm lỗi `f"baggage_kg: được {got}"`.
- `p.body.get("voucherCode")` khác `case.voucher` thì thêm lỗi `f"voucher: được {p.body.get('voucherCode')}"`.

Thêm 5 case nhóm `"hành lý và voucher"`, mỗi case cho 1 người lớn `AN`, với `NOON = R(depart_before="12:00")`:

| Tên                 | Lượt của khách                                                                                         | Kết quả mong đợi                                                                                           |
| ------------------- | ------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------- |
| `hanh-ly-hoi-gia`   | `f"Đặt vé SGN đi DAD ngày 30/10, bay trước 12h, cho {AN}"`                                             | `proposal`, `NOON`, `[122]`, `[AN_PAX]`, `reply_has=["250.000"]` (model hỏi hành lý kèm giá)               |
| `hanh-ly-them-sau`  | `[f"Đặt vé SGN đi DAD ngày 30/10, bay trước 12h, cho {AN}", "Thêm 20kg hành lý ký gửi nhé"]`           | `proposal`, `NOON`, `[122]`, `[AN_PAX]`, `baggage_kg=20`                                                   |
| `hanh-ly-hai-chieu` | `f"Đặt vé khứ hồi SGN - DAD, đi 30/10 bay trước 12h, về 2/11, cho {AN}, mua thêm 20kg hành lý ký gửi"` | `proposal`, `R(depart_before="12:00", return_date=RT)`, `[122]`, `[AN_PAX]`, về `[611]`, `baggage_kg=20`   |
| `voucher-giam-gia`  | `f"Đặt vé SGN đi DAD ngày 30/10, bay trước 12h, cho {AN}, dùng mã WELCOME10"`                          | `proposal`, `NOON`, `[122]`, `[AN_PAX]`, `voucher="WELCOME10"`                                             |
| `voucher-sai-ma`    | `f"Đặt vé SGN đi DAD ngày 30/10, bay trước 12h, cho {AN}, dùng mã GIAM50"`                             | `("reply", "proposal")`, `NOON`, `[122]`, `[AN_PAX]`, `reply_has=["GIAM50"]` (nếu đề xuất thì không có mã) |

Đáp án tính từ backend giả. VN122 là chuyến rẻ nhất trước 12h. Cặp khứ hồi rẻ nhất là VN122 + VJ611, cộng 20 kg mỗi chiều thành 3.600.000. Với `WELCOME10`, VN122 còn 1.665.000. `GIAM50` không có trong `VOUCHERS`.

- [ ] **Step 4: Prompt.** Trong `SYSTEM_PROMPT`:
  - Sau câu phạm vi thêm: "Có thể mua thêm hành lý ký gửi (một mức cho mọi người lớn và trẻ em, mọi chiều) và dùng một mã giảm giá khách đưa."
  - Câu `max_price` đổi "(mọi hành khách, cả hai chiều nếu khứ hồi)" thành "(số tiền khách trả: mọi hành khách, cả hai chiều nếu khứ hồi, gồm hành lý, sau giảm giá)".
  - Sau "Khi nhận pending_approval thì nhắc khách bấm "Giữ chỗ" trên thẻ." thêm: "Kết quả có baggage_options thì hỏi luôn khách có muốn thêm hành lý ký gửi không, đọc các mức và giá (mỗi khách mỗi chiều); khách muốn thêm thì gọi lại propose_hold như cũ với baggage_kg. Khách đưa mã giảm giá thì truyền voucher_code, không tự bịa mã; backend báo mã không dùng được thì nói rõ với khách và hỏi có đặt không dùng mã không."

- [ ] **Step 5: Run the whole suite**

Run: `cd agent && uv run pytest -q`
Expected: tất cả PASS.

- [ ] **Step 6: Chạy eval case mới với model thật**

Run: `cd agent && uv run python -m evals.run --case hanh-ly > <workspace>/eval-hanh-ly.txt`, rồi `--case voucher > <workspace>/eval-voucher.txt`
Expected: 5 case nhóm "hành lý và voucher", mỗi case 3/3. Case nào trượt thì đọc trace trong `evals/results/` rồi sửa prompt hoặc cách chấm, sau đó chạy lại đúng case đó. Ví dụ: `voucher-sai-ma` trượt chỉ vì model không nhắc lại mã, trong khi câu trả lời vẫn nói rõ mã không dùng được; khi đó đổi `reply_has` sang cụm mà model dùng ổn định, và ghi lý do vào ghi chú §3.

- [ ] **Step 7: Chạy đủ bộ**

Run: `cd agent && uv run python -m evals.run > <workspace>/eval-full.txt`
Expected: tỉ lệ lượt đạt ≥ 99% (mốc sau 5a: 83/84). Thấp hơn thì đọc trace của lượt trượt. Case cũ trượt vì `baggage_kg` (model tự thêm hành lý) hoặc vì thiếu thẻ (model hỏi hành lý trước khi đề xuất) thì sửa câu prompt ở Step 4, rồi chạy lại các case trượt và nhóm của chúng.

- [ ] **Step 8: Tài liệu.**
  - README, đoạn phạm vi: trợ lý mua thêm được hành lý ký gửi, một mức cho mọi người lớn và trẻ em trên mọi chiều, giá lấy từ bảng giá của hãng từng chiều. Trợ lý hỏi hành lý ngay khi đưa thẻ duyệt. Trợ lý dùng mã giảm giá khách đưa; backend kiểm mã. `max_price` là số tiền khách trả. Thẻ duyệt hiện hành lý từng chiều và khoản giảm giá. `is_done` so hành lý từng hành khách trên từng chiều, và mã voucher.
  - Lộ trình, §1: tick "Xong" của Bước 5. §2: gắn link plan này vào dòng 5c và ghi "5c xong".
  - Lộ trình, §3: thêm hai dòng. Dòng thứ nhất có cột "Sau bước" là "5c" và cột "Số case" là "5 (chỉ hành lý và voucher)". Dòng thứ hai có cột "Sau bước" là "5" và cột "Số case" là số case của bộ đủ, ghi chú so với mốc 5a.

- [ ] **Step 9: Commit**

```bash
git add agent/ docs/superpowers/plans/
git commit -m "feat(agent): prompt và case eval hành lý, voucher; số liệu eval đủ bộ sau Bước 5"
```
