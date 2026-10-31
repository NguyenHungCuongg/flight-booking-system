import pytest

from flight_agent.policies import DOCS, POLICY_DIR, fill, heading_id, index, load_sections


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
    for slug in DOCS:
        (tmp_path / f"{slug}.md").write_text("Mở đầu\n", encoding="utf-8")
    (tmp_path / "dieu-khoan.md").write_text("Mở đầu\n\n## Một\n\nA\n\n### Con\n\nB\n\n## Hai\n\nC\n", encoding="utf-8")
    s = load_sections(tmp_path)
    assert list(s) == ["dieu-khoan#mot", "dieu-khoan#hai"]
    assert s["dieu-khoan#mot"].body == "A\n\n### Con\n\nB"


def test_index_lists_every_section_on_one_line():
    s = load_sections()
    lines = index(s).splitlines()
    assert len(lines) == len(s)
    assert any(line.startswith("- hoan-doi-hanh-ly#dieu-kien-chung: Điều kiện chung (") for line in lines)


def test_fill_replaces_placeholders_and_rejects_missing_values():
    assert fill("Giữ {{booking.hold_minutes}} phút", {"booking.hold_minutes": 20}) == "Giữ 20 phút"
    with pytest.raises(ValueError, match="Thiếu tham số booking.hold_minutes"):
        fill("{{booking.hold_minutes}}", {})


def test_docs_match_the_frontend_list():
    ts = (POLICY_DIR.parents[1] / "src" / "lib" / "policies.ts").read_text(encoding="utf-8")
    for slug, title in DOCS.items():
        assert f'slug: "{slug}", title: "{title}"' in ts


def test_assistant_section_is_readable_by_the_agent():
    assert "quyen-rieng-tu#tro-ly-dat-ve" in load_sections()
