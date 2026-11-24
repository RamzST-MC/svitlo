// Сервер без залежностей: node server.js   (потрібен Node 18+)
// ADMIN_TOKEN=секрет PORT=3000 node server.js
const http = require('http'), fs = require('fs'), crypto = require('crypto'), { execFile, execFileSync } = require('child_process');
const FILE = process.env.DATA_FILE || __dirname + '/../data/schedule.json', ACCESS_FILE = FILE + '.maintenance-access', TOKEN = process.env.ADMIN_TOKEN || 'change-me', PORT = process.env.PORT || 3000;
fs.mkdirSync(require('path').dirname(FILE), { recursive: true });
const read = () => { try { return JSON.parse(fs.readFileSync(FILE)); } catch { return { queues: {}, adminAccess: false, maintenance: false, maintenanceMessage: '' }; } };

// Розбір тексту: "1.1: 00:00-04:00, 12:00-16:00"
function importText(text, date) {
  const data = read();
  for (const line of text.split('\n')) {
    const m = line.match(/^\s*(\d\.\d)\s*[:\-]\s*(.+)$/); if (!m) continue;
    const a = Array(24).fill(0), re = /(\d{1,2}):(\d\d)\s*[-–—]\s*(\d{1,2}):(\d\d)/g; let r;
    while ((r = re.exec(m[2]))) { let s = +r[1], e = Math.ceil(+r[3] + +r[4] / 60); if (e <= s) e += 24; for (let h = s; h < e; h++) a[h % 24] = 1; }
    (data.queues[m[1]] ||= {})[date] = a;
  }
  fs.writeFileSync(FILE, JSON.stringify(data)); return data;
}

// Автосинхронізація. За замовчуванням — сайт Хмельницькобленерго (hoe.com.ua). SOURCE_URL=off вимикає, SOURCE_URL=https://... — довільне джерело.
// Джерело може віддавати JSON {"queues":{"1.1":{"2026-11-19":[24 числа]}}} або текст/HTML з рядками "1.1: 00:00-04:00, 12:00-16:00".
const SOURCE = process.env.SOURCE_URL || 'hoe', EVERY = (+process.env.SYNC_MINUTES || 15) * 60000;
const today = () => new Date().toLocaleDateString('sv-SE', { timeZone: 'Europe/Kyiv' });
let lastSync = 'ще не було';
async function syncGeneric() {
  if (!SOURCE) throw new Error('SOURCE_URL не задано');
  const r = await fetch(SOURCE, { signal: AbortSignal.timeout(15000) });
  if (!r.ok) throw new Error('джерело відповіло HTTP ' + r.status);
  const t = await r.text();
  try {
    const j = JSON.parse(t);
    if (j.queues) {
      const d = read();
      for (const q in j.queues) d.queues[q] = { ...(d.queues[q] || {}), ...j.queues[q] };
      fs.writeFileSync(FILE, JSON.stringify(d)); lastSync = new Date().toISOString(); return;
    }
  } catch {}
  importText(t.replace(/<[^>]+>/g, '\n').replace(/&nbsp;/g, ' '), today());
  lastSync = new Date().toISOString();
}

// ---- Парсер hoe.com.ua: пости «Погодинні відключення на N листопада» / «Прогноз ГПВ на N листопада»
// Формат поста: "Підчерга 1.1 – 06:00-10:00; 14:00-18:00." (підчерги 1.1 … 6.2), дата у тексті "18.11.2026 р."
const HOE = 'https://hoe.com.ua', ALLQ = ['1.1','1.2','2.1','2.2','3.1','3.2','4.1','4.2','5.1','5.2','6.1','6.2'];
const getHtml = async u => { const r = await fetch(u, { signal: AbortSignal.timeout(15000), headers: { 'User-Agent': 'Mozilla/5.0 SvitloBot' } }); if (!r.ok) throw new Error(u + ' HTTP ' + r.status); return r.text(); };
const toText = h => h.replace(/<(script|style)[\s\S]*?<\/\1>/gi, '').replace(/<br\s*\/?>|<\/p>|<\/div>|<\/li>/gi, '\n').replace(/<[^>]+>/g, '').replace(/&nbsp;/g, ' ');
function hours(str) {
  const a = Array(24).fill(1 - 1), re = /(\d{1,2}):(\d\d)\s*[-–—]\s*(\d{1,2}):(\d\d)/g; let r;
  while ((r = re.exec(str))) { const s = +r[1], e = Math.ceil(+r[3] + +r[4] / 60); for (let h = s; h < (e <= s ? e + 24 : e); h++) a[h % 24] = 1; }
  return a;
}
function parsePost(text) {
  const d = text.match(/(\d{2})\.(\d{2})\.(20\d{2})/); if (!d) return null;
  const out = {}, re = /Підчерга\s+(\d\.\d)\s*[–—-]\s*([^\n]*)/g; let r;
  while ((r = re.exec(text))) out[r[1]] = hours(r[2]);
  if (!Object.keys(out).length) return null;
  for (const q of ALLQ) out[q] ||= Array(24).fill(0);   // підчерги, яких немає в списку, без відключень
  return { date: `${d[3]}-${d[2]}-${d[1]}`, out };
}
async function syncHoe() {
  const pages = await Promise.allSettled([getHtml(HOE + '/'), getHtml(HOE + '/post/novini-kompaniji')]);
  const links = [];
  for (const p of pages) if (p.status === 'fulfilled')
    for (const m of p.value.matchAll(/href="([^"]*\/post\/(?:pogodinni-vidkljuchennja|prognoz-gpv)[^"]*\.html)"/g)) { const u = new URL(m[1], HOE).href; if (!links.includes(u)) links.push(u); }
  if (!links.length) throw new Error('на hoe.com.ua не знайдено постів з графіками (змінилась верстка?)');
  const data = read(), seen = new Set(), min = new Date(Date.now() - 3 * 864e5).toLocaleDateString('sv-SE', { timeZone: 'Europe/Kyiv' });
  for (const u of links.slice(0, 8)) {            // новіші пости йдуть першими; перший для дати виграє
    let post; try { post = parsePost(toText(await getHtml(u))); } catch (e) { console.error('post:', e.message); continue; }
    if (!post || post.date < min || seen.has(post.date)) continue;
    seen.add(post.date);
    for (const q in post.out) (data.queues[q] ||= {})[post.date] = post.out[q];
  }
  for (const q in data.queues) for (const d in data.queues[q]) if (d < min) delete data.queues[q][d];
  if (!seen.size) throw new Error('жоден пост не розібрано');
  fs.writeFileSync(FILE, JSON.stringify(data)); lastSync = new Date().toISOString();
  console.log('sync ok, дати:', [...seen].join(', '));
}
const sync = () => SOURCE === 'hoe' ? syncHoe() : syncGeneric();

if (SOURCE !== 'off') { const run = () => sync().catch(e => console.error('sync:', e.message)); run(); setInterval(run, EVERY); }

http.createServer((req, res) => {
  const url = new URL(req.url, 'http://x'); let body = '';
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Headers', '*'); res.setHeader('Access-Control-Allow-Methods', '*');
  if (req.method === 'OPTIONS') return res.end();
  req.on('data', c => body += c).on('end', () => {
    const json = (c, o) => { res.writeHead(c, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(o)); };
    if (req.method === 'GET' && url.pathname === '/schedule') return json(200, read());
    if (req.method === 'POST' && url.pathname === '/maintenance-access') {
      const configured = (() => { try { return fs.readFileSync(ACCESS_FILE, 'utf8').trim(); } catch { return ''; } })();
      const submitted = crypto.createHash('sha256').update(body.trim()).digest('hex');
      const valid = configured.length === submitted.length && crypto.timingSafeEqual(Buffer.from(configured), Buffer.from(submitted));
      return json(valid && body.trim() ? 200 : 403, { authorized: valid && !!body.trim() });
    }
    if (req.method === 'GET' && (url.pathname === '/admin' || url.pathname === '/')) {
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' }); return res.end(fs.readFileSync(__dirname + '/admin.html'));
    }
    if (req.headers['x-admin-token'] !== TOKEN) return json(401, { error: 'unauthorized' });
    try {
      if (req.method === 'POST' && url.pathname === '/maintenance-access-config') {
        const code = body.trim();
        if (code && code.length < 8) return json(400, { error: 'Код має містити щонайменше 8 символів' });
        if (!code) { try { fs.unlinkSync(ACCESS_FILE); } catch {} }
        else fs.writeFileSync(ACCESS_FILE, crypto.createHash('sha256').update(code).digest('hex'), { mode: 0o600 });
        return json(200, { ok: true });
      }
      if (req.method === 'POST' && url.pathname === '/sync') { return sync().then(() => json(200, { ok: true, lastSync }), e => json(502, { error: e.message })); }
      if (req.method === 'PUT' && url.pathname === '/schedule') {
        const nd = JSON.parse(body), current = read();
        if (nd.notice === undefined) nd.notice = current.notice || '';
        if (nd.adminAccess === undefined) nd.adminAccess = current.adminAccess || false;
        if (nd.maintenance === undefined) nd.maintenance = current.maintenance || false;
        if (nd.maintenanceMessage === undefined) nd.maintenanceMessage = current.maintenanceMessage || '';
        fs.writeFileSync(FILE, JSON.stringify(nd)); return json(200, { ok: true });
      }
      if (req.method === 'GET' && url.pathname === '/status') {
        let version = ''; try { version = execFileSync('git', ['rev-parse', '--short', 'HEAD'], { cwd: __dirname }).toString().trim(); } catch {}
        return json(200, { lastSync, source: SOURCE, everyMinutes: EVERY / 60000, version });
      }
      if (req.method === 'POST' && url.pathname === '/notice') { const d = read(); d.notice = body.trim().slice(0, 500); fs.writeFileSync(FILE, JSON.stringify(d)); return json(200, { ok: true }); }
      if (req.method === 'POST' && url.pathname === '/update') {
        // fetch + reset --hard на upstream: працює і тоді, коли історія розійшлась (force-push, локальні коміти)
        const run = require('util').promisify(execFile), o = { cwd: __dirname, timeout: 60000 };
        const rev = async () => (await run('git', ['rev-parse', '--short', 'HEAD'], o)).stdout.trim();
        (async () => {
          const before = await rev();
          await run('git', ['fetch', 'origin'], o);
          await run('git', ['reset', '--hard', '@{u}'], o);
          const after = await rev();
          json(200, { output: before === after ? 'Already up to date (' + after + ')' : before + ' → ' + after });
          if (before !== after) setTimeout(() => process.exit(0), 500); // systemd перезапустить з новим кодом
        })().catch(e => json(500, { error: String(e.stderr || e.message).trim() }));
        return;
      }
      if (req.method === 'POST' && url.pathname === '/import') return json(200, importText(body, url.searchParams.get('date')));
    } catch (e) { return json(400, { error: String(e) }); }
    json(404, { error: 'not found' });
  });
}).listen(PORT, () => console.log('Svitlo server on :' + PORT));
