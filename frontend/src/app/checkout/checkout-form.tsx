"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useRef, useState, type FocusEvent, type FormEvent } from "react";
import {
  Field,
  Notice,
  SelectField,
  pillDark,
  pillLine,
  textLink,
} from "@/components/form";
import PriceTable from "@/components/price-table";
import { ApiError, apiFetch, type Schemas, type User } from "@/lib/api";
import { DIRECTION_LABEL, PASSENGER_LABEL, dmy } from "@/lib/booking";
import { clearSelection, type Journey, type Selection } from "@/lib/selection";
import { duration, first, hhmm, last, vnd } from "../flights/itineraries";
import {
  asciiUpper,
  bagField,
  bookingRequest,
  passengerTypes,
  searchUrl,
  type PassengerType,
} from "./booking-request";

type Quote = Schemas["QuoteResponse"];
export type BaggageOption = Schemas["BaggageOptionResponse"];

/** Lỗi về hành trình: khách phải chọn lại chuyến ở C-02 (APP_FLOW C-07). */
const UNAVAILABLE = [
  "SEATS_UNAVAILABLE",
  "FLIGHT_NOT_BOOKABLE",
  "ITINERARY_INVALID",
];

/** BR-10, tính tại ngày cất cánh chặng đầu của chiều đi. */
const AGE_HINT: Record<PassengerType, string> = {
  ADULT: "Từ 12 tuổi trở lên tại ngày bay.",
  CHILD: "Từ 2 đến dưới 12 tuổi tại ngày bay.",
  INFANT: "Dưới 2 tuổi tại ngày bay.",
};

/**
 * Mọi ô đã thoả ràng buộc HTML. Không dùng form.checkValidity(): hàm đó bắn sự kiện invalid, làm hiện lỗi ở cả những
 * ô khách chưa nhập tới.
 */
const complete = (form: HTMLFormElement) =>
  Array.from(form.elements).every(
    (el) =>
      !(el instanceof HTMLInputElement || el instanceof HTMLSelectElement) ||
      el.validity.valid,
  );

/** Rời ô họ tên: hiện đúng giá trị sẽ gửi (BR-12). */
const toAscii = (e: FocusEvent<HTMLInputElement>) => {
  e.currentTarget.value = asciiUpper(e.currentTarget.value);
};
const toUpper = (e: FocusEvent<HTMLInputElement>) => {
  e.currentTarget.value = e.currentTarget.value.trim().toUpperCase();
};

function quoteRows(q: Quote, voucher: string) {
  const rows: [string, number][] = [];
  for (const j of q.journeys) {
    const dir = DIRECTION_LABEL[j.direction];
    for (const f of j.passengerFares) {
      rows.push([
        `${dir}, ${PASSENGER_LABEL[f.type].toLowerCase()} × ${f.count}`,
        f.total,
      ]);
    }
    if (j.baggageTotal) rows.push([`${dir}, hành lý`, j.baggageTotal]);
  }
  if (q.discountTotal) rows.push([`Giảm giá ${voucher}`, -q.discountTotal]);
  return rows;
}

function JourneySummary({ journey }: { journey: Journey }) {
  const it = journey.itinerary;
  const a = first(it);
  const z = last(it);
  const numbers = it.segments.map((s) => s.flightNumber).join(" + ");
  return (
    <div className="grid gap-4 border border-line p-22">
      <p className="text-caption text-muted">
        {`${DIRECTION_LABEL[journey.direction]}, ${dmy(a.departure.time)}`}
      </p>
      <p className="font-bold">
        {`${a.airline.name} ${numbers}: ${hhmm(a.departure)} ${a.departure.airport} → ${hhmm(z.arrival)} ${z.arrival.airport}`}
      </p>
      <p className="text-caption text-muted">
        {`Gói ${journey.fare.name}, ${it.stops ? `nối chuyến tại ${a.arrival.city}` : "bay thẳng"}, ${duration(it.totalDurationMinutes)}`}
      </p>
    </div>
  );
}

/** Một hành khách (FR-21). Tên ô theo đường dẫn trường của errors backend, VD passengers[0].dateOfBirth. */
function PassengerFields({
  index,
  label,
  type,
  departure,
  international,
  errors,
}: {
  index: number;
  label: string;
  type: PassengerType;
  departure: string;
  international: boolean;
  errors: Record<string, string>;
}) {
  const name = (k: string) => `passengers[${index}].${k}`;
  return (
    <fieldset className="grid gap-16">
      <legend className="mb-16 font-bold">{label}</legend>
      <div className="grid items-start gap-22 sm:grid-cols-2">
        <Field
          label="Họ"
          name={name("lastName")}
          required
          maxLength={50}
          autoComplete="off"
          onBlur={toAscii}
          error={errors[name("lastName")]}
        />
        <Field
          label="Tên đệm và tên"
          name={name("firstName")}
          required
          maxLength={50}
          autoComplete="off"
          onBlur={toAscii}
          error={errors[name("firstName")]}
        />
        <SelectField
          label="Giới tính"
          name={name("gender")}
          required
          defaultValue=""
          error={errors[name("gender")]}
        >
          <option value="" disabled>
            Chọn giới tính
          </option>
          <option value="MALE">Nam</option>
          <option value="FEMALE">Nữ</option>
        </SelectField>
        <Field
          label="Ngày sinh"
          name={name("dateOfBirth")}
          type="date"
          required
          max={departure}
          invalidMessage="Ngày sinh không được sau ngày bay."
          hint={AGE_HINT[type]}
          error={errors[name("dateOfBirth")]}
        />
        {international && (
          <>
            <Field
              label="Quốc tịch"
              name={name("nationality")}
              required
              maxLength={2}
              pattern="[A-Za-z]{2}"
              invalidMessage="Mã quốc gia gồm 2 chữ cái, VD VN."
              hint="Mã quốc gia 2 chữ cái, VD VN."
              autoComplete="off"
              onBlur={toUpper}
              error={errors[name("nationality")]}
            />
            <Field
              label="Số hộ chiếu"
              name={name("passportNumber")}
              required
              maxLength={20}
              pattern="[A-Za-z0-9]{6,20}"
              invalidMessage="Số hộ chiếu gồm 6 đến 20 chữ cái hoặc chữ số."
              autoComplete="off"
              onBlur={toUpper}
              error={errors[name("passportNumber")]}
            />
            <Field
              label="Ngày hết hạn hộ chiếu"
              name={name("passportExpiry")}
              type="date"
              required
              hint="Còn hạn ít nhất 6 tháng tính từ ngày bay của chặng cuối."
              error={errors[name("passportExpiry")]}
            />
          </>
        )}
      </div>
    </fieldset>
  );
}

/**
 * C-07 (APP_FLOW §2.1, UF-01). Form không điều khiển (đọc bằng FormData). Mỗi lần sửa, chờ 400 ms rồi báo giá nếu mọi ô
 * đã hợp lệ (FR-20); "Giữ chỗ" gửi POST /bookings (FR-24) rồi mở C-09.
 */
export default function CheckoutForm({
  selection,
  user,
  international,
  baggage,
}: {
  selection: Selection;
  user: User;
  international: boolean;
  /** Mức hành lý đang bán theo mã hãng; null khi tải lỗi. */
  baggage: Record<string, BaggageOption[]> | null;
}) {
  const router = useRouter();
  const formRef = useRef<HTMLFormElement>(null);
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined);
  /** Số thứ tự lần báo giá mới nhất: phản hồi về trễ của lần cũ bị bỏ. */
  const latest = useRef(0);
  /** Chặn bấm đúp "Giữ chỗ" trước khi React kịp vô hiệu hoá nút. */
  const busy = useRef(false);
  const [quote, setQuote] = useState<Quote | null>(null);
  const [quoting, setQuoting] = useState(false);
  const [voucher, setVoucher] = useState("");
  const [voucherError, setVoucherError] = useState<string>();
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [problem, setProblem] = useState<ApiError>();
  const [pending, setPending] = useState(false);
  const [held, setHeld] = useState<string>();

  const types = passengerTypes(selection);
  const labels = types.map(
    (t, i) =>
      `${PASSENGER_LABEL[t]} ${types.slice(0, i + 1).filter((x) => x === t).length}`,
  );
  const departure = first(selection.journeys[0].itinerary).departure.time.slice(
    0,
    10,
  );
  const estimate = selection.journeys.reduce(
    (sum, j) => sum + j.fare.totalPrice,
    0,
  );

  async function runQuote(code: string) {
    const seq = ++latest.current;
    const form = formRef.current;
    if (!form || !complete(form)) {
      setQuote(null);
      setQuoting(false);
      return;
    }
    setQuoting(true);
    try {
      const q = await apiFetch<Quote>("/bookings/quote", {
        method: "POST",
        body: bookingRequest(selection, new FormData(form), code),
      });
      if (seq !== latest.current) return;
      setQuote(q);
      setErrors({});
      setProblem(undefined);
    } catch (e) {
      if (!(e instanceof ApiError)) throw e;
      if (seq !== latest.current) return;
      setQuote(null);
      show(e);
    } finally {
      if (seq === latest.current) setQuoting(false);
    }
  }

  function show(e: ApiError) {
    if (e.status === 401) {
      router.replace("/login?next=%2Fcheckout");
      return;
    }
    if (e.code === "VOUCHER_INVALID") {
      // FR-23: báo lý do dưới ô, bỏ mã và tính lại giá không có mã.
      setVoucherError(e.detail);
      setVoucher("");
      void runQuote("");
      return;
    }
    setErrors(Object.fromEntries(e.errors.map((f) => [f.field, f.message])));
    setProblem(e);
  }

  function schedule(e: FormEvent<HTMLFormElement>) {
    // Mã voucher chỉ áp khi bấm "Áp dụng"; gõ mã không cần báo giá lại.
    if ((e.target as HTMLInputElement).name === "voucher") return;
    clearTimeout(timer.current);
    timer.current = setTimeout(() => void runQuote(voucher), 400);
  }

  function applyCode(code: string) {
    clearTimeout(timer.current);
    setVoucherError(undefined);
    setVoucher(code);
    void runQuote(code);
  }

  function applyVoucher() {
    const input = formRef.current?.elements.namedItem(
      "voucher",
    ) as HTMLInputElement;
    applyCode(input.value.trim().toUpperCase());
  }

  async function hold(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (busy.current) return;
    busy.current = true;
    clearTimeout(timer.current);
    latest.current++; // bỏ kết quả của lần báo giá đang chạy
    setQuoting(false);
    setPending(true);
    setProblem(undefined);
    try {
      const created = await apiFetch<Schemas["BookingCreatedResponse"]>(
        "/bookings",
        {
          method: "POST",
          body: bookingRequest(
            selection,
            new FormData(e.currentTarget),
            voucher,
          ),
        },
      );
      clearSelection();
      setHeld(created.code);
      router.replace(`/bookings/${created.code}`);
    } catch (err) {
      busy.current = false;
      setPending(false);
      if (!(err instanceof ApiError)) throw err;
      if (UNAVAILABLE.includes(err.code)) {
        router.push(`${searchUrl(selection)}&unavailable=1`);
      } else {
        show(err);
      }
    }
  }

  if (held) {
    return (
      <p role="status" className="text-muted">
        Đã giữ chỗ {held}. Đang mở chi tiết đặt chỗ...
      </p>
    );
  }

  const unavailable =
    problem !== undefined && UNAVAILABLE.includes(problem.code);
  // Lỗi không gắn với ô nào trên form: số khách (BR-11) và hành lý.
  const loose = Object.entries(errors).filter(
    ([f]) => f === "passengers" || f.startsWith("baggage"),
  );

  return (
    <form
      ref={formRef}
      onSubmit={hold}
      onChange={schedule}
      className="grid gap-53 lg:grid-cols-[minmax(0,1fr)_400px] lg:items-start"
    >
      <div className="grid gap-53">
        <section aria-label="Hành trình" className="grid gap-16">
          <div className="flex flex-wrap items-center justify-between gap-16">
            <h2 className="text-subheading font-bold">Hành trình</h2>
            <Link href={searchUrl(selection)} className={textLink}>
              Đổi chuyến bay
            </Link>
          </div>
          {selection.journeys.map((j) => (
            <JourneySummary key={j.direction} journey={j} />
          ))}
        </section>

        <section className="grid gap-22">
          <div className="grid gap-8">
            <h2 className="text-subheading font-bold">Hành khách</h2>
            <p className="text-caption text-muted">
              Họ tên như trên giấy tờ tuỳ thân. Chữ có dấu được tự bỏ dấu và
              viết hoa.
            </p>
          </div>
          {types.map((type, i) => (
            <PassengerFields
              key={i}
              index={i}
              label={labels[i]}
              type={type}
              departure={departure}
              international={international}
              errors={errors}
            />
          ))}
        </section>

        <section className="grid gap-22">
          <div className="grid gap-8">
            <h2 className="text-subheading font-bold">Liên hệ</h2>
            <p className="text-caption text-muted">
              Vé điện tử và mọi thông báo về đặt chỗ được gửi tới email này.
            </p>
          </div>
          <div className="grid items-start gap-22 sm:grid-cols-2">
            <Field
              label="Họ tên người liên hệ"
              name="contact.name"
              autoComplete="name"
              required
              maxLength={100}
              defaultValue={user.fullName}
              error={errors["contact.name"]}
            />
            <Field
              label="Email"
              name="contact.email"
              type="email"
              autoComplete="email"
              required
              maxLength={255}
              defaultValue={user.email}
              error={errors["contact.email"]}
            />
            <Field
              label="Số điện thoại"
              name="contact.phone"
              type="tel"
              autoComplete="tel"
              required
              pattern="\+?[0-9]{9,15}"
              invalidMessage="Số điện thoại gồm 9 đến 15 chữ số, có thể có dấu + ở đầu."
              defaultValue={user.phone}
              error={errors["contact.phone"]}
            />
          </div>
        </section>

        <section className="grid gap-22">
          <h2 className="text-subheading font-bold">Hành lý mua thêm</h2>
          {baggage === null && (
            <Notice>
              Không tải được bảng giá hành lý. Tải lại trang nếu muốn mua thêm
              hành lý.
            </Notice>
          )}
          {selection.journeys.map((j) => {
            const airline = first(j.itinerary).airline;
            const options = baggage?.[airline.code] ?? [];
            const included = j.fare.checkedBaggageKg
              ? ` và ${j.fare.checkedBaggageKg} kg ký gửi`
              : ", chưa có hành lý ký gửi";
            return (
              <fieldset key={j.direction} className="grid gap-16">
                <legend className="mb-8 font-bold">
                  {`Hành lý ${DIRECTION_LABEL[j.direction].toLowerCase()}`}
                </legend>
                <p className="text-caption text-muted">
                  {`${airline.name}, gói ${j.fare.name} đã gồm ${j.fare.carryOnKg} kg xách tay${included}.`}
                </p>
                {baggage !== null && options.length === 0 ? (
                  <p className="text-caption">
                    Hãng chưa bán hành lý mua thêm cho chiều này.
                  </p>
                ) : (
                  <div className="grid items-start gap-22 sm:grid-cols-2">
                    {types.map(
                      (type, i) =>
                        type !== "INFANT" && (
                          <SelectField
                            key={i}
                            label={labels[i]}
                            name={bagField(j.direction, i)}
                            defaultValue=""
                          >
                            <option value="">Không mua thêm</option>
                            {options.map((o) => (
                              <option key={o.id} value={o.id}>
                                {`${o.weightKg} kg, ${vnd(o.price)}`}
                              </option>
                            ))}
                          </SelectField>
                        ),
                    )}
                  </div>
                )}
              </fieldset>
            );
          })}
        </section>

        <section className="grid gap-13">
          <div className="flex flex-wrap items-start gap-13">
            <div className="min-w-0 flex-1">
              <Field
                label="Mã giảm giá"
                name="voucher"
                autoComplete="off"
                maxLength={50}
                error={voucherError}
                onKeyDown={(e) => {
                  // Enter trong ô này là áp mã, không phải giữ chỗ.
                  if (e.key === "Enter") {
                    e.preventDefault();
                    applyVoucher();
                  }
                }}
              />
            </div>
            <button
              type="button"
              className={`${pillLine} mt-30`}
              onClick={applyVoucher}
            >
              Áp dụng
            </button>
          </div>
          {voucher && (
            <p role="status" className="text-caption">
              {quote
                ? `Đã áp mã ${voucher}, giảm ${vnd(quote.discountTotal)}. `
                : `Mã ${voucher} sẽ được kiểm tra khi bạn điền đủ thông tin hành khách. `}
              <button
                type="button"
                className={textLink}
                onClick={() => applyCode("")}
              >
                Bỏ mã
              </button>
            </p>
          )}
        </section>
      </div>

      <aside className="grid gap-22 lg:sticky lg:top-22">
        <section
          aria-label="Bảng giá"
          aria-busy={quoting}
          className="grid gap-16 border border-ink p-22"
        >
          <h2 className="text-subheading font-bold">Bảng giá</h2>
          {quote ? (
            <PriceTable
              rows={quoteRows(quote, voucher)}
              total={quote.totalAmount}
            />
          ) : (
            <>
              <PriceTable
                rows={selection.journeys.map((j): [string, number] => [
                  `${DIRECTION_LABEL[j.direction]}, gói ${j.fare.name}`,
                  j.fare.totalPrice,
                ])}
                total={estimate}
              />
              <p className="text-caption text-muted">
                Tạm tính theo giá lúc tìm chuyến. Điền đủ thông tin hành khách
                để xem giá cuối cùng, gồm hành lý và mã giảm giá.
              </p>
            </>
          )}
          {quoting && (
            <p className="text-caption text-muted">Đang tính giá...</p>
          )}
        </section>
        {problem && (
          <Notice>
            <p className="font-bold">{problem.detail}</p>
            {loose.map(([f, m]) => (
              <p key={f}>{m}</p>
            ))}
            {unavailable && (
              <p className="mt-8">
                <Link
                  href={`${searchUrl(selection)}&unavailable=1`}
                  className={textLink}
                >
                  Chọn chuyến khác
                </Link>
              </p>
            )}
          </Notice>
        )}
        <button
          type="submit"
          className={`${pillDark} w-full`}
          disabled={pending}
        >
          {pending ? "Đang giữ chỗ..." : "Giữ chỗ"}
        </button>
        <p className="text-caption text-muted">
          Giữ chỗ chưa trừ tiền. Bạn thanh toán qua VNPay ở bước sau, trong thời
          hạn giữ chỗ.
        </p>
      </aside>
    </form>
  );
}
