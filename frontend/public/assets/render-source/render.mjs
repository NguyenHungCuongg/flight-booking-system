// Usage: node render.mjs <outDir> "<name>|<query>" ...
import { chromium } from 'playwright';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';

const root = path.dirname(new URL(import.meta.url).pathname);
const types = { '.html': 'text/html', '.js': 'text/javascript', '.mjs': 'text/javascript' };
const server = http.createServer((req, res) => {
  const p = path.join(root, decodeURIComponent(req.url.split('?')[0]));
  if (!p.startsWith(root) || !fs.existsSync(p) || fs.statSync(p).isDirectory()) { res.writeHead(404); return res.end(); }
  res.writeHead(200, { 'Content-Type': types[path.extname(p)] || 'application/octet-stream' });
  fs.createReadStream(p).pipe(res);
});
await new Promise((r) => server.listen(0, '127.0.0.1', r));
const port = server.address().port;

const [outDir, ...jobs] = process.argv.slice(2);
fs.mkdirSync(outDir, { recursive: true });
const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
for (const job of jobs) {
  const [name, query] = job.split('|');
  const page = await browser.newPage();
  page.on('console', (m) => { if (m.type() === 'error') console.log(`[${name}]`, m.text()); });
  page.on('pageerror', (e) => console.log(`[${name}] pageerror`, e.message));
  const t0 = Date.now();
  await page.goto(`http://127.0.0.1:${port}/scene.html?${query}`);
  await page.waitForFunction('window.__done === true', null, { timeout: 240000 });
  const data = await page.evaluate(() => document.querySelector('canvas').toDataURL('image/png'));
  fs.writeFileSync(path.join(outDir, `${name}.png`), Buffer.from(data.split(',')[1], 'base64'));
  console.log(`${name}: ${((Date.now() - t0) / 1000).toFixed(1)}s`);
  await page.close();
}
await browser.close();
server.close();
