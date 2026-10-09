# SkyLine: render máy bay (Three.js)

Ảnh trong `images/` được dựng hoàn toàn bằng code trong thư mục này (thân, cánh, động cơ, đuôi đều là hình học
procedural, không dùng model hay ảnh của bên thứ ba), nên dự án dùng được mà không cần ghi công. Thân máy bay sơn
trắng, không logo hãng, đúng mục Imagery trong DESIGN.md.

## Cài đặt

```bash
npm i three@0.170.0 playwright sharp
npx playwright install chromium   # nếu máy chưa có Chromium cho Playwright
```

## Render lại

```bash
L="env=0.42&hemi=0.12&sunI=2.4&sun=10,100,-40&exp=1.06"
node render.mjs final \
  "hero|view=hero&w=3200&h=1900&yaw=-48&roll=12&pitch=7&cam=0,38,150&fill=0.92&$L" \
  "tail|view=tail&w=1600&h=1900&$L" \
  "engine|view=engine&w=2000&h=1430&cam=-6,-6,52&tgt=0,-1.2,9.4&fov=30&$L" \
  "climb|view=hero&w=2600&h=1550&yaw=-72&pitch=10&roll=-6&cam=0,-4,150&fov=26&fill=0.9&$L"
node post.mjs   # cắt viền trong suốt, xuất WebP vào web/
```

Ảnh đuôi trên landing là bản lật ngang của `tail` (`sharp(...).flop()`).
Tham số URL: `yaw`, `pitch`, `roll` (độ) xoay máy bay; `cam`, `tgt` đặt camera; `fill` là tỉ lệ khung hình máy bay chiếm;
`sun`, `sunI`, `hemi`, `env`, `exp` chỉnh ánh sáng.
