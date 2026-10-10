import sharp from 'sharp';
import fs from 'node:fs';
fs.mkdirSync('web', { recursive: true });
const jobs = [
  ['hero', 2000, 28], ['tail', 1100, 24], ['engine', 1400, 0], ['climb', 1600, 24],
];
for (const [name, width, pad] of jobs) {
  let img = sharp(`final/${name}.png`).ensureAlpha();
  // trim fully transparent borders
  const { data, info } = await img.clone().raw().toBuffer({ resolveWithObject: true });
  let minX = info.width, minY = info.height, maxX = -1, maxY = -1;
  for (let y = 0; y < info.height; y++) for (let x = 0; x < info.width; x++) {
    if (data[(y * info.width + x) * 4 + 3] > 2) { if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y; }
  }
  const left = Math.max(0, minX - pad), top = Math.max(0, minY - pad);
  const w = Math.min(info.width, maxX + pad + 1) - left, h = Math.min(info.height, maxY + pad + 1) - top;
  const out = await sharp(`final/${name}.png`).extract({ left, top, width: w, height: h })
    .resize({ width: Math.min(width, w) }).webp({ quality: 84, alphaQuality: 90, effort: 6 }).toFile(`web/${name}.webp`);
  console.log(name, `crop ${w}x${h}`, '->', out.width + 'x' + out.height, (out.size / 1024).toFixed(0) + 'KB');
}
