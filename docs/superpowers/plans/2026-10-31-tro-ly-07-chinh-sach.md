# Trợ lý Bước 7: Hỏi đáp chính sách Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Trợ lý trả lời câu hỏi về chính sách SkyLine và điều kiện gói giá của hãng chỉ từ dữ liệu thật, có nêu nguồn; không có dữ liệu thì nói không biết.

**Architecture:** Backend thêm endpoint công khai `GET /api/airlines/{code}/fare-families` (trên một nhánh tách từ `develop`), rồi `develop` được merge vào `feature/flight-agent` để có thêm ba văn bản chính sách và `GET /api/settings/public`. Agent đọc Markdown từ đĩa theo từng mục (mục lục nằm trong system prompt, model gọi `read_policy` để đọc mục cần), điền số bằng `/api/settings/public`, và gọi `airline_rules` cho gói giá cùng giá hành lý của một hãng. Eval thêm cách chấm citation bằng code và case "không biết".

**Tech Stack:** Spring Boot 4 (backend), Python LangChain + pytest (agent), Next.js 16 + Playwright (frontend `/assistant`).

**Spec:** [Bước 7 trong lộ trình mở rộng](2026-10-27-tro-ly-dat-ve-agent-roadmap-mo-rong.md#bước-7--hỏi-đáp-chính-sách-hãng), mục "Đã quyết (chủ dự án, 2026-10-31)" · Văn bản chính sách: [spec](../specs/2026-10-31-chinh-sach-skyline-design.md)

## Global Constraints

- Task 1 làm trên nhánh `feature/goi-gia-cong-khai` tách từ `develop`, rồi merge vào `develop` ở máy (chủ dự án đã duyệt thứ tự này). Từ Task 2 trở đi làm trên `feature/flight-agent`. Không push.
- Bất biến MVP (spec §2 của trợ lý) giữ nguyên: tool mới chỉ `GET`, không đổi `propose_hold`, `is_done` hay thẻ duyệt. Trợ lý vẫn chỉ đặt gói rẻ nhất.
- Câu "không biết" cố định: `SkyLine không có thông tin về` (hằng `NO_INFO`), dùng chung cho prompt và eval.
- Mã mục chính sách dạng `<slug>#<id>`, link trích dẫn dạng `/chinh-sach/<slug>#<id>`; `id` sinh đúng như `headingId` của `frontend/src/lib/policies.ts`.
- Câu trả lời của trợ lý vẫn là văn bản thuần (không Markdown); đường dẫn nội bộ được frontend biến thành link.
- Eval tốn quota free tier (500 request/ngày): ngày đầu chỉ chạy `--case cs-` với k=3; đủ bộ k=3 chạy ngày sau (chủ dự án quyết, 2026-10-31).
- Comment, tin nhắn và tài liệu viết tiếng Việt. Sửa file tài liệu lớn đã có (`docs/TDD.md`, roadmap, `CLAUDE.md`) bằng script Node, không bằng Edit: hook định dạng sẽ format lại cả file.

## Review Focus

1. **Model trích dẫn mục chưa đọc hoặc bịa link** → link bấm được dẫn tới mục không có. Test: Task 5 `test_citing_an_unread_section_fails`.
2. **`read_policy` nhận tham số hỏng** (mã lạ, danh sách rỗng, chuỗi thay cho danh sách, quá 4 mục) → `invalid_param`, không crash, không gọi backend. Test: Task 4 `test_read_policy_rejects_bad_sections_without_calling_the_backend`.
3. **`airline_rules` nhận tên hãng thay cho mã, chữ thường, hoặc hãng không có** → mã thường được nhận, tên lạ ra `invalid_param` kèm danh sách hãng. Test: Task 4 `test_airline_rules_accepts_a_code_in_any_case_and_lists_airlines_otherwise`.
4. **`/api/settings/public` lỗi** → `backend_error` cho model, không trả văn bản còn `{{...}}`. Test: Task 4 `test_read_policy_reports_a_settings_error`.
5. **`heading_id` Python lệch với frontend** → link trợ lý đưa ra nhảy sai mục trên web. Test: Task 3 `test_heading_id_matches_the_frontend`.

---

### Task 1: Endpoint công khai `GET /api/airlines/{code}/fare-families` (nhánh từ `develop`)

**Files:**

- Create: `backend/src/main/java/vn/edu/uit/flightbooking/catalog/web/FareFamilyResponse.java` (chuyển record lồng trong `AdminFareFamilyController` ra file riêng, như `BaggageOptionResponse`)
- Modify: `backend/.../catalog/web/AdminFareFamilyController.java`, `backend/.../catalog/web/CatalogController.java`, `backend/.../catalog/domain/FareFamilyService.java`, `backend/.../catalog/infra/FareFamilyRepository.java`
- Test: `backend/src/test/java/vn/edu/uit/flightbooking/catalog/web/FareFamilyControllerTest.java`
- Modify: `docs/TDD.md` (§8.2), `docs/superpowers/plans/2026-10-09-00-roadmap.md` (§6), `frontend/src/lib/api-types.ts` (sinh lại)

**Interfaces:**

- Produces: `GET /api/airlines/{code}/fare-families` → `200` `FareFamilyResponse[]` (`id, airlineCode, cabinClass, name, carryOnKg, checkedBaggageKg, refundable, refundFee, changeable, changeFee, active`), chỉ gói `active`, theo `id`; hãng không có → 404 `RESOURCE_NOT_FOUND` như `baggage-options`. `FareFamilyService.listActive(String airlineCode): List<FareFamily>`.

- [ ] **Step 1: Tạo nhánh**

```bash
git switch develop && git switch -c feature/goi-gia-cong-khai
```

- [ ] **Step 2: Viết test hỏng** trong `FareFamilyControllerTest` (dùng `create`, `update`, `fare` đã có):

```java
@Test
void publicSeesOnlyActiveFareFamiliesOfTheAirline() // tạo Q1 "Economy Classic", Q1 "Business Classic", Q2 "Của Hãng Khác";
    // sửa "Business Classic" thành active=false; GET /api/airlines/Q1/fare-families KHÔNG session:
    // 200, $.length() == 1, $[0].name == "Economy Classic", $[0].refundFee == 600000, $[0].changeFee == 300000
@Test
void unknownAirlineIsNotFoundOnPublicFareFamilies() // GET /api/airlines/ZZ/fare-families → 404, $.code == "RESOURCE_NOT_FOUND"
```

- [ ] **Step 3: Chạy, thấy hỏng**

Run: `cd backend && ./mvnw -q test -Dtest=FareFamilyControllerTest`
Expected: FAIL, 2 test mới nhận 404 `NOT_FOUND`/không khớp (route chưa có; `GET /api/airlines/**` đã `permitAll` nên không phải 401).

- [ ] **Step 4: Viết code** — repository `findByAirlineCodeAndActiveTrueOrderById(String)`; `FareFamilyService.listActive` (`@Transactional(readOnly = true)`, gọi `airlines.get(airlineCode)` trước như `list`, Javadoc tiếng Việt: gói đang bán, cho khách và trợ lý); `CatalogController` thêm `@GetMapping("/airlines/{code}/fare-families")` trả `FareFamilyResponse::of` (Javadoc: điều kiện gói giá đang bán của hãng). `AdminFareFamilyController` dùng record ở file mới.

- [ ] **Step 5: Chạy, thấy qua**

Run: `cd backend && ./mvnw -q test "-Dtest=FareFamilyControllerTest,ModularityTest"`
Expected: PASS.

- [ ] **Step 6: Tài liệu và kiểu API**
  - `docs/TDD.md` §8.2 thêm dưới dòng `baggage-options`: `| GET | \`/airlines/{code}/fare-families\` | Công khai | Gói giá đang bán của hãng (điều kiện hành lý, hoàn, đổi) | FR-82 |`.
  - Roadmap 21 plan §6, dòng "Chính sách": thêm `GET /api/airlines/{code}/fare-families` (công khai, `FareFamilyService.listActive`).
  - Dừng container backend Docker nếu đang giữ cổng 8080 (`docker compose stop backend frontend`), chạy backend profile `dev`, `cd frontend && npm run gen:api`; `git diff src/lib/api-types.ts` có path `/api/airlines/{code}/fare-families`.

- [ ] **Step 7: Kiểm toàn bộ, commit và merge vào `develop`**

Run: `cd backend && ./mvnw -q verify` rồi `cd frontend && npm run lint && npm run build`
Expected: xanh.

```bash
git add backend docs/TDD.md docs/superpowers/plans/2026-10-09-00-roadmap.md frontend/src/lib/api-types.ts
git commit -m "feat(catalog): GET /api/airlines/{code}/fare-families công khai cho khách và trợ lý"
git switch develop && git merge --ff-only feature/goi-gia-cong-khai && git branch -d feature/goi-gia-cong-khai
```

---

### Task 2: Merge `develop` vào `feature/flight-agent`

**Files:** kết quả merge. Có thể xung đột ở `CLAUDE.md`, `frontend/e2e/smoke.spec.ts`, `frontend/next.config.ts` (hai phía cùng sửa).

**Interfaces:**

- Produces trên nhánh agent: `frontend/content/chinh-sach/*.md`, `frontend/src/lib/policies.ts`, `GET /api/settings/public`, `GET /api/airlines/{code}/fare-families`.

- [ ] **Step 1: Merge**

```bash
git switch feature/flight-agent && git merge develop
```

Xung đột thì giữ cả hai phía: `next.config.ts` giữ rewrite `/agent/*` của nhánh agent và `redirects()` của `develop`; `smoke.spec.ts` giữ mọi route của cả hai; `CLAUDE.md` giữ câu của cả hai trong mục Status.

- [ ] **Step 2: Kiểm**

Run: `cd agent && uv run pytest -q` rồi `cd frontend && npm run lint && npm run build && npx playwright test policies agent-stream --project chromium --grep-invert "dẫn tới"`
Expected: pytest xanh; lint, build sạch; test thuần của chính sách và stream qua.

- [ ] **Step 3: Commit merge** (nếu có xung đột): `git commit` với message mặc định của merge.

---

### Task 3: Đọc văn bản chính sách trong agent (`flight_agent/policies.py`)

**Files:**

- Create: `agent/flight_agent/policies.py`
- Test: `agent/tests/test_policies.py`

**Interfaces:**

- Produces:
  - `DOCS: dict[str, str]` — slug → tên văn bản, đúng thứ tự và tên như `POLICIES` của frontend: `dieu-khoan` "Điều khoản & điều kiện", `quyen-rieng-tu` "Chính sách quyền riêng tư", `hoan-doi-hanh-ly` "Chính sách hoàn, đổi vé và hành lý".
  - `POLICY_DIR = Path(__file__).resolve().parents[2] / "frontend" / "content" / "chinh-sach"`.
  - `PLACEHOLDER = re.compile(r"\{\{([a-z_.]+)\}\}")`.
  - `heading_id(text: str) -> str` — như `headingId` frontend: chữ thường, `NFD`, bỏ dấu (`unicodedata.combining`), `đ`→`d`, chuỗi ký tự ngoài `a-z0-9` thành `-`, cắt `-` hai đầu.
  - `@dataclass(frozen=True) class Section: key: str; document: str; title: str; body: str` với `source` (property) = `/chinh-sach/<slug>#<id>`.
  - `load_sections(directory: Path = POLICY_DIR) -> dict[str, Section]` — mỗi dòng `## ` mở một mục tới dòng `## ` kế tiếp; `body` không gồm dòng heading, đã `strip()`; bỏ phần mở đầu trước mục đầu tiên. Đọc file mỗi lần gọi (không cache).
  - `index(sections: dict[str, Section]) -> str` — mỗi mục một dòng `- <key>: <title> (<document>)`, theo thứ tự `DOCS` rồi thứ tự trong file.
  - `fill(text: str, settings: dict[str, int]) -> str` — thay `{{khoá}}` bằng `str(settings[khoá])`; thiếu khoá thì `ValueError("Thiếu tham số <khoá>")`.

- [ ] **Step 1: Viết test hỏng** `agent/tests/test_policies.py`:

```python
def test_heading_id_matches_the_frontend():
    # cùng đáp án với e2e/policies.spec.ts "headingId bỏ dấu tiếng Việt"
    assert heading_id("Đổi chuyến") == "doi-chuyen"
    assert heading_id("Cookie và lưu trữ trên trình duyệt") == "cookie-va-luu-tru-tren-trinh-duyet"
    assert heading_id("Giá & thanh toán") == "gia-thanh-toan"

def test_sections_of_the_real_documents_are_keyed_by_slug_and_id():
    s = load_sections()
    assert "hoan-doi-hanh-ly#dieu-kien-chung" in s and "dieu-khoan#lien-he" in s and "quyen-rieng-tu#lien-he" in s
    assert s["hoan-doi-hanh-ly#dieu-kien-chung"].source == "/chinh-sach/hoan-doi-hanh-ly#dieu-kien-chung"
    assert s["hoan-doi-hanh-ly#dieu-kien-chung"].document == "Chính sách hoàn, đổi vé và hành lý"
    assert "{{aftersales.min_hours_before_departure}}" in s["hoan-doi-hanh-ly#dieu-kien-chung"].body
    assert not any(x.body.startswith("##") or "\n## " in x.body for x in s.values())

def test_sections_split_on_level_two_headings_only(tmp_path):
    for slug in DOCS: (tmp_path / f"{slug}.md").write_text("Mở đầu\n", encoding="utf-8")
    (tmp_path / "dieu-khoan.md").write_text("Mở đầu\n\n## Một\n\nA\n\n### Con\n\nB\n\n## Hai\n\nC\n", encoding="utf-8")
    s = load_sections(tmp_path)
    assert list(s) == ["dieu-khoan#mot", "dieu-khoan#hai"]
    assert s["dieu-khoan#mot"].body == "A\n\n### Con\n\nB"

def test_index_lists_every_section_on_one_line():
    s = load_sections()
    lines = index(s).splitlines()
    assert len(lines) == len(s)
    assert any(l.startswith("- hoan-doi-hanh-ly#dieu-kien-chung: Điều kiện chung (") for l in lines)

def test_fill_replaces_placeholders_and_rejects_missing_values():
    assert fill("Giữ {{booking.hold_minutes}} phút", {"booking.hold_minutes": 20}) == "Giữ 20 phút"
    with pytest.raises(ValueError, match="Thiếu tham số booking.hold_minutes"):
        fill("{{booking.hold_minutes}}", {})

def test_docs_match_the_frontend_list():
    ts = (POLICY_DIR.parents[1] / "src" / "lib" / "policies.ts").read_text(encoding="utf-8")
    for slug, title in DOCS.items():
        assert f'slug: "{slug}", title: "{title}"' in ts
```

- [ ] **Step 2: Chạy, thấy hỏng**

Run: `cd agent && uv run pytest tests/test_policies.py -q`
Expected: FAIL, `ModuleNotFoundError: flight_agent.policies`.

- [ ] **Step 3: Viết `flight_agent/policies.py`** theo Interfaces. Docstring đầu file tiếng Việt: nguồn chung với trang `/chinh-sach`, đọc theo mục (progressive disclosure, Buổi 7).

- [ ] **Step 4: Chạy, thấy qua**

Run: `cd agent && uv run pytest tests/test_policies.py -q`
Expected: 6 passed.

- [ ] **Step 5: Commit**

```bash
git add agent/flight_agent/policies.py agent/tests/test_policies.py
git commit -m "feat(agent): đọc văn bản chính sách theo mục, điền tham số hệ thống"
```

---

### Task 4: Tool `read_policy`, `airline_rules` và điều kiện gói giá trong `search_flights`

**Files:**

- Modify: `agent/flight_agent/core.py`, `agent/tests/fakes.py`
- Test: `agent/tests/test_harness.py`

**Interfaces:**

- Consumes: `load_sections`, `fill`, `Section` (Task 3).
- Produces:
  - `MAX_SECTIONS = 4`; `CABINS = {"ECONOMY": "phổ thông", "PREMIUM_ECONOMY": "phổ thông đặc biệt", "BUSINESS": "thương gia", "FIRST": "hạng nhất"}`.
  - `_conditions(f: dict) -> str` — từ một gói (`carryOnKg, checkedBaggageKg, refundable, refundFee, changeable, changeFee`), dạng: `"xách tay 10 kg, ký gửi 23 kg, hoàn vé phí 600.000 đ mỗi khách, đổi vé phí 300.000 đ mỗi khách"`; ký gửi 0 → `"không có ký gửi"`; không hoàn → `"không hoàn vé"`; không đổi → `"không đổi vé"`; phí 0 → `"hoàn vé miễn phí"` / `"đổi vé miễn phí"`.
  - Harness field `airlines: dict[str, dict] | None = None` (cache `GET /api/airlines` theo `code`).
  - `Harness.read_policy(self, sections: list[str]) -> dict` → `{"status": "ok", "sections": [{"source", "document", "title", "text"}]}`; `text` đã `fill` bằng một lần `GET /api/settings/public`.
  - `Harness.airline_rules(self, airline: str) -> dict` → `{"status": "ok", "airline": "VN (Vietnam Airlines)", "fares": [{"name", "cabin", "conditions"}], "baggage": ["15 kg: 250.000 đ", ...]}`; dùng lại cache `baggage_prices` (cùng dạng `{id, weightKg, price}`, kg tăng dần) nếu đã có.
  - `_legs` thêm `"conditions": _conditions(fare)` vào mỗi hành trình.
  - Thêm hai tool vào `Harness.tools`. Không sửa `check_permission`: chỉ `search_flights` và `propose_hold` cần ràng buộc.
  - `tests/fakes.py`: `SETTINGS` (như PRD §7 nhưng `booking.hold_minutes` = 20, để eval phân biệt số lấy từ tool với số model nhớ), `AIRLINES` (VN Vietnam Airlines, VJ Vietjet Air, QH Bamboo Airways, đúng dạng `AirlineResponse`), `FARE_FAMILIES` (VN/VJ/QH chép từ seed `V102`, dạng `FareFamilyResponse`); `FakeBackend(settings_status=200)` và các route `GET /api/settings/public`, `GET /api/airlines`, `GET /api/airlines/{code}/fare-families` (hãng lạ → 404 `RESOURCE_NOT_FOUND`).

- [ ] **Step 1: Viết test hỏng** trong `test_harness.py`:

```python
def test_policy_and_airline_tools_need_no_constraints_and_only_get():
    h, fake = ready()
    assert h.execute("read_policy", {"sections": ["hoan-doi-hanh-ly#dieu-kien-chung"]})["status"] == "ok"
    assert h.execute("airline_rules", {"airline": "VN"})["status"] == "ok"
    assert {r.method for r in fake.requests} == {"GET"}

def test_read_policy_fills_numbers_and_cites_each_section():
    h, _ = ready()
    r = h.execute("read_policy", {"sections": ["hoan-doi-hanh-ly#dieu-kien-chung", "dieu-khoan#gia-ve"]})
    first = r["sections"][0]
    assert first["source"] == "/chinh-sach/hoan-doi-hanh-ly#dieu-kien-chung"
    assert "24 giờ" in first["text"] and "{{" not in first["text"]
    assert [s["source"] for s in r["sections"]][1] == "/chinh-sach/dieu-khoan#gia-ve"

def test_read_policy_rejects_bad_sections_without_calling_the_backend():
    h, fake = ready()
    for sections in ([], "dieu-khoan#gia-ve", ["khong-co#muc"], ["dieu-khoan#gia-ve"] * 5):
        assert h.execute("read_policy", {"sections": sections})["status"] == "invalid_param"
    assert fake.requests == []

def test_read_policy_reports_a_settings_error():
    h, _ = ready(FakeBackend(settings_status=500))
    assert h.execute("read_policy", {"sections": ["dieu-khoan#gia-ve"]})["status"] == "backend_error"

def test_airline_rules_lists_fares_and_baggage_of_one_airline():
    h, _ = ready()
    r = h.execute("airline_rules", {"airline": "VN"})
    assert r["airline"] == "VN (Vietnam Airlines)"
    classic = next(f for f in r["fares"] if f["name"] == "Economy Classic")
    assert classic == {"name": "Economy Classic", "cabin": "phổ thông",
                       "conditions": "xách tay 10 kg, ký gửi 23 kg, hoàn vé phí 600.000 đ mỗi khách, "
                                     "đổi vé phí 300.000 đ mỗi khách"}
    assert r["baggage"][1] == "20 kg: 350.000 đ"

def test_airline_rules_accepts_a_code_in_any_case_and_lists_airlines_otherwise():
    h, _ = ready()
    assert h.execute("airline_rules", {"airline": " vj "})["airline"] == "VJ (Vietjet Air)"
    r = h.execute("airline_rules", {"airline": "Vietnam Airlines"})
    assert r["status"] == "invalid_param" and "VN (Vietnam Airlines)" in r["hint"]

def test_conditions_say_no_refund_no_checked_bag_and_free_change():
    lite = {"carryOnKg": 7, "checkedBaggageKg": 0, "refundable": False, "refundFee": 0, "changeable": True,
            "changeFee": 0}
    assert _conditions(lite) == "xách tay 7 kg, không có ký gửi, không hoàn vé, đổi vé miễn phí"

def test_search_shows_the_fare_conditions_of_each_journey():
    h, _ = ready()
    h.execute("set_constraints", ROUTE)
    r = h.execute("search_flights", {})
    assert r["matching"][0]["conditions"] == "xách tay 7 kg, không có ký gửi, không hoàn vé, đổi vé phí 300.000 đ mỗi khách"
```

- [ ] **Step 2: Chạy, thấy hỏng**

Run: `cd agent && uv run pytest tests/test_harness.py -q -k "policy or airline or conditions"`
Expected: FAIL (`Không có tool read_policy`, `ImportError: _conditions`).

- [ ] **Step 3: Viết code** theo Interfaces. Docstring hai tool viết cho model (tiếng Việt), nói rõ: `read_policy` — đọc tối đa 4 mục theo mã trong mục lục chính sách của system prompt, mỗi mục có `source` để trích dẫn; `airline_rules` — `airline` là mã IATA 2 ký tự (VN, VJ, QH…), trả gói giá đang bán và giá mua thêm hành lý ký gửi mỗi khách mỗi chiều. Hint `invalid_param` của `read_policy`: "sections là danh sách 1–4 mã mục trong mục lục chính sách, ví dụ [\"hoan-doi-hanh-ly#dieu-kien-chung\"]." (thêm mã lạ nếu có). Kiểm tham số trước khi gọi backend.

- [ ] **Step 4: Chạy, thấy qua**

Run: `cd agent && uv run pytest -q`
Expected: toàn bộ xanh (test cũ của `search_flights` vẫn qua vì chỉ thêm khoá).

- [ ] **Step 5: Commit**

```bash
git add agent/flight_agent/core.py agent/tests/fakes.py agent/tests/test_harness.py
git commit -m "feat(agent): tool read_policy, airline_rules và điều kiện gói giá khi tìm chuyến"
```

---

### Task 5: Prompt bám nguồn và eval chính sách

**Files:**

- Modify: `agent/flight_agent/react.py`, `agent/evals/cases.py`, `agent/evals/run.py`
- Test: `agent/tests/test_evals.py`, `agent/tests/test_chat.py` (cạnh `test_prompt_names_todays_weekday`)

**Interfaces:**

- Consumes: `load_sections`, `index` (Task 3); tool Task 4.
- Produces:
  - `react.NO_INFO = "SkyLine không có thông tin về"`.
  - `system_prompt(harness)` thêm sau quy tắc: `"Mục lục chính sách (mã mục: tên mục (văn bản)):\n" + index(load_sections())`.
  - `Case.abstain: bool = False`; `grade` thêm: `abstain` → câu trả lời phải chứa `NO_INFO`; mọi case → mỗi link `/chinh-sach/<slug>#<id>` trong câu trả lời phải là mục đã đọc bằng `read_policy` có `status == "ok"` trong lượt cuối (đọc từ `harness.trace[*]["args"]["sections"]`), sai thì `cites: <mã> chưa đọc`.

- [ ] **Step 1: Viết test hỏng** trong `test_evals.py` (model kịch bản như `model()`):

```python
POLICY_Q = "Đổi vé phải làm trước giờ bay bao lâu?"

def policy_case(**kw):
    return Case(**{"name": "cs-x", "group": "chính sách", "turns": [POLICY_Q], "end": "reply", **kw})

def test_citing_a_read_section_passes():
    m = scripted(call("read_policy", sections=["hoan-doi-hanh-ly#dieu-kien-chung"]),
                 AIMessage("Trước 24 giờ (/chinh-sach/hoan-doi-hanh-ly#dieu-kien-chung)."))
    assert run_case(policy_case(reply_has=["24"], calls=["read_policy"]), m)["passed"]

def test_citing_an_unread_section_fails():
    m = scripted(call("read_policy", sections=["dieu-khoan#gia-ve"]),
                 AIMessage("Trước 24 giờ (/chinh-sach/hoan-doi-hanh-ly#dieu-kien-chung)."))
    r = run_case(policy_case(), m)
    assert not r["passed"] and any("cites" in f for f in r["failures"])

def test_abstain_case_needs_the_fixed_sentence():
    ok = scripted(AIMessage("SkyLine không có thông tin về thú cưng. Bạn hỏi hãng bay nhé."))
    guess = scripted(AIMessage("Được mang chó dưới 7 kg."))
    assert run_case(policy_case(abstain=True), ok)["passed"]
    assert not run_case(policy_case(abstain=True), guess)["passed"]

def test_cases_cover_policy_questions_and_abstention():
    cs = [c for c in CASES if c.name.startswith("cs-")]
    assert sum(c.abstain for c in cs) >= 4 and sum(not c.abstain for c in cs) >= 6
```

Thêm `test_prompt_lists_policy_sections_and_the_no_info_sentence` vào `test_chat.py`, dựng harness như `test_prompt_names_todays_weekday`: `system_prompt(h)` chứa `"hoan-doi-hanh-ly#dieu-kien-chung: Điều kiện chung"` và `NO_INFO`.

- [ ] **Step 2: Chạy, thấy hỏng**

Run: `cd agent && uv run pytest tests/test_evals.py tests/test_chat.py -q`
Expected: FAIL (`Case` chưa có `abstain`, chưa có case `cs-`, prompt chưa có mục lục).

- [ ] **Step 3: Sửa prompt** (`SYSTEM_PROMPT`), thêm một quy tắc trước dòng "Gặp denied…":

> - Khách hỏi về chính sách, quy định hay điều kiện (hoàn, đổi, hành lý, giá trẻ em, giữ chỗ, thanh toán, dữ liệu cá nhân…): chỉ trả lời từ kết quả tool, không dùng hiểu biết riêng. Chính sách SkyLine: gọi read_policy với mã mục trong mục lục bên dưới. Gói giá, hành lý kèm gói, phí hoàn đổi và giá mua thêm hành lý của một hãng: gọi airline_rules. Bảng ví dụ gói giá trong văn bản hoàn đổi chỉ minh hoạ, không dùng làm số của hãng. Mỗi ý trả lời nêu nguồn: source của mục đã đọc (ví dụ /chinh-sach/hoan-doi-hanh-ly#dieu-kien-chung), hoặc "gói X của hãng Y". Không có thông tin trong kết quả tool thì trả lời đúng câu "{NO_INFO} <điều khách hỏi>." rồi mời khách xem /chinh-sach/dieu-khoan hoặc hỏi hãng bay; không được đoán. Trợ lý chỉ đặt gói rẻ nhất của mỗi chuyến: khách muốn gói khác thì nói rõ giới hạn này và mời tự đặt trên /flights.

(`SYSTEM_PROMPT` dùng `.format`, nên chèn `NO_INFO` bằng f-string hoặc ghép chuỗi, không để `{NO_INFO}` lọt vào `.format`.)

- [ ] **Step 4: Sửa `grade` và thêm case** vào `CASES` (nhóm "chính sách", tên bắt đầu `cs-` để chạy chung bằng `--case cs-`):

| Tên                        | Câu của khách                                                             | Chấm                                                                             |
| -------------------------- | ------------------------------------------------------------------------- | -------------------------------------------------------------------------------- |
| `cs-han-hoan-doi`          | "Mình muốn đổi hoặc hoàn vé thì phải làm trước giờ bay bao lâu?"          | `end="reply"`, `reply_has=["24", "/chinh-sach/"]`, `calls=["read_policy"]`       |
| `cs-giu-cho`               | "Giữ chỗ xong thì mình có bao lâu để thanh toán?"                         | `reply_has=["20"]` (backend giả = 20, khác mặc định 15), `calls=["read_policy"]` |
| `cs-gia-tre-em`            | "Bé nhà mình 5 tuổi thì giá vé tính thế nào?"                             | `reply_has=["90"]`, `calls=["read_policy"]`                                      |
| `cs-so-the`                | "SkyLine có lưu số thẻ ngân hàng của mình không?"                         | `reply_has=["VNPay"]`, `calls=["read_policy"]`                                   |
| `cs-phi-hoan-goi`          | "Gói Economy Classic của Vietnam Airlines hoàn vé mất phí bao nhiêu?"     | `reply_has=["600.000"]`, `calls=["airline_rules"]`                               |
| `cs-gia-hanh-ly`           | "Mua thêm 20kg hành lý ký gửi của Vietjet giá bao nhiêu?"                 | `reply_has=["350.000"]`, `calls=["airline_rules"]`                               |
| `cs-goi-khac`              | `f"Đặt vé SGN đi DAD ngày 30/10, bay trước 12h, gói hoàn được, cho {AN}"` | `end=("reply", "proposal")`, `reply_has=["/flights"]`                            |
| `cs-khong-biet-thu-cung`   | "Mình mang chó cưng lên máy bay được không?"                              | `abstain=True`                                                                   |
| `cs-khong-biet-kich-thuoc` | "Hành lý xách tay được phép kích thước tối đa bao nhiêu cm?"              | `abstain=True`                                                                   |
| `cs-khong-biet-visa`       | "Bay sang Thái Lan có cần xin visa không?"                                | `abstain=True`                                                                   |
| `cs-khong-biet-check-in`   | "Check-in online ở đâu, trước mấy tiếng?"                                 | `abstain=True`                                                                   |

Mọi case trên dùng `end="reply"` trừ `cs-goi-khac`.

- [ ] **Step 5: Chạy, thấy qua**

Run: `cd agent && uv run pytest -q`
Expected: toàn bộ xanh.

- [ ] **Step 6: Commit**

```bash
git add agent/flight_agent/react.py agent/evals agent/tests
git commit -m "feat(agent): prompt bám nguồn chính sách, eval citation và câu không biết"
```

---

### Task 6: Link bấm được trong câu trả lời của `/assistant`

**Files:**

- Modify: `frontend/src/app/assistant/agent-api.ts`, `frontend/src/app/assistant/assistant-chat.tsx:388`
- Test: `frontend/e2e/agent-links.spec.ts` (thuần, không trình duyệt), `frontend/e2e/assistant.spec.ts`

**Interfaces:**

- Produces: `replyParts(text: string): (string | { href: string })[]` trong `agent-api.ts`; nhận đường dẫn bắt đầu bằng `/chinh-sach`, `/flights`, `/bookings`, gồm các đoạn `/x` hoặc `#x` với `x` là `[A-Za-z0-9-]+` (dấu chấm, phẩy, ngoặc cuối câu không thuộc link).

- [ ] **Step 1: Viết test hỏng**

```ts
// e2e/agent-links.spec.ts
test("replyParts tách đường dẫn nội bộ, bỏ dấu câu ở cuối", () => {
  expect(
    replyParts(
      "Xem /chinh-sach/hoan-doi-hanh-ly#doi-chuyen. Hoặc tự đặt trên /flights, nhé",
    ),
  ).toEqual([
    "Xem ",
    { href: "/chinh-sach/hoan-doi-hanh-ly#doi-chuyen" },
    ". Hoặc tự đặt trên ",
    { href: "/flights" },
    ", nhé",
  ]);
  expect(replyParts("Không có link")).toEqual(["Không có link"]);
  expect(replyParts("/bookings/K7M2QX/refund/outbound")).toEqual([
    { href: "/bookings/K7M2QX/refund/outbound" },
  ]);
});
```

Trong `assistant.spec.ts`: test "Đường dẫn trong câu trả lời bấm được" — dựng `VIEW` riêng có một lượt agent với `reply: "Xem /chinh-sach/hoan-doi-hanh-ly#dieu-kien-chung nhé."`, mở trang, `expect(page.getByRole("link", { name: "/chinh-sach/hoan-doi-hanh-ly#dieu-kien-chung" })).toHaveAttribute("href", "/chinh-sach/hoan-doi-hanh-ly#dieu-kien-chung")`.

- [ ] **Step 2: Chạy, thấy hỏng**

Run: `cd frontend && npx playwright test agent-links assistant --project chromium`
Expected: FAIL (`replyParts` chưa có; không có link).

- [ ] **Step 3: Viết code** — `replyParts` bằng một regex toàn cục; trong `assistant-chat.tsx` thay `{entry.turn.reply}` bằng `replyParts(...).map((p, k) => typeof p === "string" ? p : <Link key={k} href={p.href} className={...}>{p.href}</Link>)`, kiểu link theo `textLink` của `components/form.tsx`.

- [ ] **Step 4: Chạy, thấy qua**

Run: `cd frontend && npx playwright test agent-links assistant agent-stream --project chromium && npm run lint`
Expected: PASS, lint sạch.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/assistant frontend/e2e/agent-links.spec.ts frontend/e2e/assistant.spec.ts
git commit -m "feat(assistant): đường dẫn trong câu trả lời bấm được"
```

---

### Task 7: Mục "Trợ lý đặt vé" trong chính sách quyền riêng tư

**Files:**

- Modify: `frontend/content/chinh-sach/quyen-rieng-tu.md`
- Test: `frontend/e2e/policies.spec.ts` (thêm), `agent/tests/test_policies.py` (thêm)

- [ ] **Step 1: Viết test hỏng**

```ts
test("quyền riêng tư nói về trợ lý đặt vé", () => {
  const s = md("quyen-rieng-tu");
  expect(s).toContain("## Trợ lý đặt vé");
  expect(s).toContain("assistant-chat"); // dòng mới trong bảng lưu trữ trên trình duyệt
});
```

```python
def test_assistant_section_is_readable_by_the_agent():
    assert "quyen-rieng-tu#tro-ly-dat-ve" in load_sections()
```

- [ ] **Step 2: Chạy, thấy hỏng**

Run: `cd frontend && npx playwright test policies --project chromium --grep-invert "dẫn tới"` và `cd agent && uv run pytest tests/test_policies.py -q`
Expected: FAIL ở hai test mới.

- [ ] **Step 3: Viết mục** `## Trợ lý đặt vé` (đặt trước `## Thay đổi chính sách`) và thêm dòng `assistant-chat` (Session storage, mã cuộc trò chuyện để tải lại trang vẫn thấy chat, tới khi đóng tab) vào bảng lưu trữ. Mỗi ý kiểm với code trước khi viết:
  - Nội dung chat (cả kết quả tool như tên, ngày sinh hành khách, mã đặt chỗ) gửi tới nhà cung cấp mô hình AI cấu hình trong `agent/.env` (`OPENAI_BASE_URL`; hiện là Google Gemini) — `flight_agent/model.py`.
  - Chat chỉ giữ trong bộ nhớ của service, mất khi service khởi động lại; không ghi xuống đĩa — `flight_agent/chat.py`, `app.py` (log chỉ ghi traceback khi lượt hỏng).
  - Trợ lý dùng phiên đăng nhập của chính khách, chỉ xem được booking của khách; chỉ giữ chỗ sau khi khách bấm duyệt; không thanh toán, huỷ, hoàn hay đổi vé.
  - Không dùng `{{khoá}}` trong heading; dùng câu "không có giá trị pháp lý" sẵn có, không thêm.

- [ ] **Step 4: Chạy, thấy qua**

Run: như Step 2, thêm `cd frontend && npm run build` (kiểm `checkPlaceholders`).
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/content/chinh-sach/quyen-rieng-tu.md frontend/e2e/policies.spec.ts agent/tests/test_policies.py
git commit -m "docs(chinh-sach): quyền riêng tư nói về trợ lý đặt vé"
```

---

### Task 8: Chạy eval, kiểm bằng tay, cập nhật tài liệu

**Files:**

- Modify: `docs/superpowers/plans/2026-10-27-tro-ly-dat-ve-agent-roadmap-mo-rong.md` (§1 tick và link plan, §3 số liệu), `agent/README.md`

- [ ] **Step 1: Eval case mới (ngày đầu)**

Run: `cd agent && uv run python -m evals.run --k 3 --case cs-`
Expected: bảng 11 case. Mục tiêu: mọi case `cs-khong-biet-*` 3/3 (abstention); tỉ lệ đạt chung ghi lại dù thấp. Case trượt thì đọc trace ở `evals/results/`; sửa prompt hay tool chỉ khi trượt vì lỗi của agent, không sửa case cho dễ qua.

- [ ] **Step 2: Kiểm bằng tay** (backend `dev`, agent `uvicorn`, frontend `npm run dev`): hỏi "đổi vé trước bao lâu" → câu trả lời có link bấm được, mở đúng mục `/chinh-sach/hoan-doi-hanh-ly#dieu-kien-chung`; Admin đổi `aftersales.min_hours_before_departure` thành 48 → hỏi lại → trợ lý nói 48; hỏi "mang thú cưng được không" → câu cố định; hỏi phí hoàn gói Economy Classic của VN → 600.000 đ, có nêu gói và hãng. Trả tham số về 24.

- [ ] **Step 3: Tài liệu**
  - Roadmap §1: dòng 7 tick "Xong", gắn link plan; §3 thêm dòng "7 (cs-)" với số liệu Step 1 và ghi chú đủ bộ chạy ngày sau.
  - `agent/README.md`: thêm một đoạn sau đoạn tra cứu booking: `read_policy` (mục lục trong system prompt, đọc tối đa 4 mục, số điền từ `/api/settings/public`, nguồn là link `/chinh-sach/...#mục`), `airline_rules` (gói giá đang bán và giá hành lý của một hãng), câu cố định khi không có thông tin.
  - Sửa bằng script Node (Global Constraints).

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/plans/2026-10-27-tro-ly-dat-ve-agent-roadmap-mo-rong.md agent/README.md
git commit -m "docs(agent): xong Bước 7 của lộ trình trợ lý"
```

- [ ] **Step 5: Đủ bộ (ngày sau, khi quota đã đặt lại)**

Run: `cd agent && uv run python -m evals.run --k 3`
Expected: tỉ lệ đạt các case cũ không thấp hơn mốc sau 5a (99%); ghi §3. Hết quota giữa chừng thì ghi phần đã đo và chạy tiếp phần còn lại bằng `--case` hôm sau.
