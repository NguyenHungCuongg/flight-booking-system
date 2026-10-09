import Image from "next/image";

const FEATURES = [
  ["Bay thẳng hoặc nối chuyến", "Kết quả có cả chuyến nối một điểm dừng cùng hãng, thời gian nối từ 1 đến 12 tiếng."],
  ["Giá trọn gói, đúng số khách", "Giá đã gồm thuế phí, tính sẵn cho số người lớn, trẻ em và em bé bạn chọn."],
  ["Gói giá rõ ràng", "Xem hành lý xách tay, ký gửi và điều kiện đổi, hoàn của từng gói trước khi đặt."],
  ["Lọc và sắp xếp tức thì", "Lọc theo hãng, điểm dừng, giờ cất cánh, khoảng giá. Sắp xếp theo giá, giờ bay hoặc tổng thời gian."],
];

export default function Airlines() {
  return (
    <section className="airlines sec" aria-labelledby="air-h">
      <div className="air">
        <div className="air-media">
          <Image className="tail-img" src="/assets/images/skyline-tail-fin.webp" width={1100} height={875}
            sizes="(min-width: 900px) 40vw, 100vw" alt="Đuôi máy bay sơn trắng, không mang logo của hãng nào" />
        </div>
        <div className="air-copy">
          <h2 id="air-h" className="h-sec rv">Đủ thông tin để chọn đúng vé.</h2>
          <div className="feat-grid">
            {FEATURES.map(([title, desc], i) => (
              <div key={title} className={`feat rv${i ? ` rv-${i + 1}` : ""}`}>
                <h3 className="h-sub">{title}</h3>
                <p className="body muted">{desc}</p>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
