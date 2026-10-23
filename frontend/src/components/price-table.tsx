import { vnd } from "@/app/flights/itineraries";

/** Bảng giá của C-07, C-09, C-10 và C-11: từng dòng, rồi dòng tổng. Số âm là giảm giá hoặc phí trừ. */
export default function PriceTable({
  rows,
  total,
  totalLabel = "Tổng thanh toán",
}: {
  rows: [string, number][];
  total: number;
  totalLabel?: string;
}) {
  return (
    <dl className="grid gap-8">
      {rows.map(([label, amount], i) => (
        <div key={i} className="flex justify-between gap-16 text-caption">
          <dt className="text-muted">{label}</dt>
          <dd>{amount < 0 ? `-${vnd(-amount)}` : vnd(amount || 0)}</dd>
        </div>
      ))}
      <div className="flex items-baseline justify-between gap-16 border-t border-line pt-13 font-bold">
        <dt>{totalLabel}</dt>
        <dd className="text-subheading text-accent">{vnd(total)}</dd>
      </div>
    </dl>
  );
}
