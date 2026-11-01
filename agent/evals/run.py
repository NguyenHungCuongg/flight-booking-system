"""Chạy bộ eval với model thật (backend giả), mỗi case k lần. Chạy từ agent/:

    uv run python -m evals.run [--k 3] [--case <chuỗi con của tên>]

Một case đạt khi đạt cả k lần. Lượt trượt ghi trace ra evals/results/<thời điểm>/.
"""
import argparse
import json
import os
import re
import sys
import time
import unicodedata
from datetime import datetime
from pathlib import Path

from langchain_core.callbacks import get_usage_metadata_callback

from evals.cases import CASES, Case
from flight_agent.chat import Chat, turn
from flight_agent.core import Constraints, Harness
from flight_agent.policies import DOCS
from flight_agent.react import NO_INFO
from tests.fakes import FakeBackend

CITATION = re.compile(r"/chinh-sach/([a-z0-9-]+(?:#[a-z0-9-]+)?)")

# Gemini free tier cho 15 request/phút, retry của client chờ không đủ lâu: lượt dừng vì "infra" thì chờ
# hết phút rồi chạy lại. Vẫn "infra" sau INFRA_RETRIES lần thì không tính vào tỉ lệ đạt.
INFRA_WAIT = 60
INFRA_RETRIES = 3


def _norm(s: str) -> str:
    return unicodedata.normalize("NFC", s).lower()


def grade(case: Case, harness: Harness, reply: str) -> list[str]:
    """Các lý do trượt; rỗng = đạt."""
    failures = []
    p = harness.proposal
    end = harness.stop_reason or ("proposal" if p else "reply")
    if end not in ((case.end,) if isinstance(case.end, str) else case.end):
        failures.append(f"end: mong {case.end}, được {end}")
    if case.constraints is not None and harness.constraints != Constraints(**case.constraints):
        failures.append(f"constraints: được {harness.constraints}")
    if p and case.flight_ids is not None and p.body["journeys"][0]["flightIds"] != case.flight_ids:
        failures.append(f"flight_ids: được {p.body['journeys'][0]['flightIds']}")
    if p and case.return_flight_ids is not None:
        got = p.body["journeys"][1]["flightIds"] if len(p.body["journeys"]) > 1 else None
        if got != case.return_flight_ids:
            failures.append(f"return_flight_ids: được {got}")
    if p and case.passengers is not None:
        got = [(x["lastName"], x["firstName"], x["gender"], x["dateOfBirth"]) for x in p.body["passengers"]]
        if sorted(got) != sorted(case.passengers):      # như is_done: không theo thứ tự
            failures.append(f"passengers: được {got}")
    if p:
        got = sorted({(j["baggage"] or {}).get("weightKg", 0) for j in p.card["journeys"]})
        if got != [case.baggage_kg]:
            failures.append(f"baggage_kg: được {got}")
        if p.body.get("voucherCode") != case.voucher:
            failures.append(f"voucher: được {p.body.get('voucherCode')}")
    failures += [f"reply_has: thiếu {s!r}" for s in case.reply_has if _norm(s) not in _norm(reply)]
    failures += [f"reply_lacks: có {s!r}" for s in case.reply_lacks if _norm(s) in _norm(reply)]
    called = {t["tool"] for t in harness.trace}
    failures += [f"calls: thiếu {t}" for t in case.calls if t not in called]
    if case.abstain and _norm(NO_INFO) not in _norm(reply):
        failures.append(f"abstain: thiếu {NO_INFO!r}")
    # Citation existence: link tới một mục chính sách phải là mục đã đọc thành công trong lượt cuối;
    # link tới cả văn bản (không có #) phải là văn bản có thật
    read = {k for t in harness.trace if t["tool"] == "read_policy" and t["status"] == "ok"
            for k in t["args"]["sections"]}
    failures += [f"cites: {k} chưa đọc" if "#" in k else f"cites: {k} không có văn bản này"
                 for k in dict.fromkeys(CITATION.findall(reply)) if k not in read and k not in DOCS]
    return failures


def run_case(case: Case, model) -> dict:
    for _ in range(INFRA_RETRIES):
        r = _run_once(case, model)
        if not r["infra"]:
            return r
        time.sleep(INFRA_WAIT)
    return _run_once(case, model)


def _run_once(case: Case, model) -> dict:
    fake, chat = FakeBackend(**case.backend), Chat("eval", "s1")
    turns, calls, reply = [], 0, ""
    with get_usage_metadata_callback() as cb:
        try:
            for text in case.turns:
                out = turn(chat, model, text, fake.backend(), case.today)
                reply, calls = out["reply"], calls + chat.harness.model_calls
                turns.append({"user": text, "reply": reply, "trace": list(out["trace"]),
                              "cards": list(out["cards"]), "stop_reason": chat.harness.stop_reason})
                if chat.harness.stop_reason == "infra":     # lượt sau sẽ đặt lại stop_reason
                    break
            failures = grade(case, chat.harness, reply)
        except Exception as e:      # một lượt hỏng không làm dừng cả bộ eval
            failures = [f"lỗi: {type(e).__name__}: {e}"]
    tokens = sum(u.get("total_tokens", 0) for u in cb.usage_metadata.values())
    return {"passed": not failures, "failures": failures, "model_calls": calls, "tokens": tokens, "turns": turns,
            "infra": chat.harness.stop_reason == "infra"}


def summarize(results: dict[str, list[dict]]) -> str:
    lines = [f"{'Case':<24} {'Đạt':>5}  {'':<6} {'Gọi model TB':>12} {'Token TB':>9}"]
    runs = [r for rs in results.values() for r in rs if not r.get("infra")]
    for name, rs in results.items():
        ok = sum(r["passed"] for r in rs)
        lines.append(f"{name:<24} {ok:>3}/{len(rs)}  {'ĐẠT' if ok == len(rs) else 'TRƯỢT':<6} "
                     f"{sum(r['model_calls'] for r in rs) / len(rs):>12.1f} "
                     f"{sum(r['tokens'] for r in rs) // len(rs):>9}")
    passed, n = sum(r["passed"] for r in runs), max(len(runs), 1)     # n: mọi lượt đều infra thì runs rỗng
    lines += ["",
              f"Case đạt: {sum(all(r['passed'] for r in rs) for rs in results.values())}/{len(results)}",
              f"Lượt đạt: {passed}/{len(runs)} ({round(100 * passed / n)}%)",
              f"Gọi model TB/lượt: {sum(r['model_calls'] for r in runs) / n:.1f}",
              f"Token TB/lượt: {sum(r['tokens'] for r in runs) // n}",
              f"Lỗi hạ tầng (không tính): {sum(r.get('infra', False) for rs in results.values() for r in rs)}"]
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser(description="Eval trợ lý đặt vé với model thật.")
    parser.add_argument("--k", type=int, default=3, help="số lần chạy mỗi case")
    parser.add_argument("--case", default="", help="chỉ chạy case có tên chứa chuỗi này")
    args = parser.parse_args()
    sys.stdout.reconfigure(encoding="utf-8")       # Windows: stdout chuyển hướng ra file mặc định là cp1252
    from flight_agent.model import make_model     # đọc .env; import ở đây để test không cần key
    model = make_model()
    out_dir = Path(__file__).parent / "results" / datetime.now().strftime("%Y%m%d-%H%M%S")
    results = {}
    for case in (c for c in CASES if args.case in c.name):
        results[case.name] = []
        for i in range(1, args.k + 1):
            r = run_case(case, model)
            results[case.name].append(r)
            print(f"{case.name} #{i}: {'ĐẠT' if r['passed'] else r['failures'][0]}", flush=True)
            if not r["passed"]:
                out_dir.mkdir(parents=True, exist_ok=True)
                (out_dir / f"{case.name}-{i}.json").write_text(
                    json.dumps({"case": case.name, "failures": r["failures"], "turns": r["turns"]},
                               ensure_ascii=False, indent=2), encoding="utf-8")
    if not results:
        raise SystemExit(f"Không có case nào khớp {args.case!r}.")
    print(f"\nModel: {os.environ.get('OPENAI_MODEL')}\n{summarize(results)}")
    if out_dir.exists():
        print(f"Trace lượt trượt: {out_dir}")


if __name__ == "__main__":
    main()
