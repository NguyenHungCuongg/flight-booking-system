import { vnd } from "@/app/flights/itineraries";

/** Bảng giá của C-07 và C-09: từng dòng, rồi tổng thanh toán. Số âm là giảm giá. */
export default function PriceTable({
  rows,
  total,
}: {
  rows: [string, number][];
  total: number;
}) {
  return (
    <dl className="grid gap-8">
      {rows.map(([label, amount], i) => (
        <div key={i} className="flex justify-between gap-16 text-caption">
          <dt className="text-muted">{label}</dt>
          <dd>{amount < 0 ? `-${vnd(-amount)}` : vnd(amount)}</dd>
        </div>
      ))}
      <div className="flex justify-between gap-16 border-t border-line pt-13 font-bold">
        <dt>Tổng thanh toán</dt>
        <dd>{vnd(total)}</dd>
      </div>
    </dl>
  );
}
