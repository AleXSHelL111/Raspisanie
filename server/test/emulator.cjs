// Эмулятор Google Apps Script (таблица в памяти) для локальной проверки server/Code.gs.
// node server/test/emulator.cjs [port] — поднимает HTTP-сервер с тем же API, что и веб-приложение.
const fs = require('fs'), path = require('path'), vm = require('vm'), crypto = require('crypto'), http = require('http');

function makeEnv() {
  const sheets = {};
  const strip = (v) => (typeof v === 'string' && v.startsWith("'") ? v.slice(1) : v);
  const makeSheet = (name) => {
    const data = [];
    const sh = {
      name, data,
      getLastRow: () => data.length,
      appendRow: (row) => { data.push(row.map(strip)); },
      deleteRow: (n) => { data.splice(n - 1, 1); },
      setFrozenRows: () => {},
      getRange: (r, c, nr = 1, nc = 1) => ({
        setValues: (vals) => {
          if (vals.length !== nr || vals.some((x) => x.length !== nc)) throw new Error('size mismatch');
          for (let i = 0; i < nr; i++) { while (data.length < r + i) data.push([]); for (let j = 0; j < nc; j++) data[r - 1 + i][c - 1 + j] = strip(vals[i][j]); }
          return { setFontWeight() {} };
        },
        getValues: () => { const out = []; for (let i = 0; i < nr; i++) { const row = []; for (let j = 0; j < nc; j++) { const v = (data[r - 1 + i] || [])[c - 1 + j]; row.push(v === undefined ? '' : v); } out.push(row); } return out; },
      }),
    };
    return sh;
  };
  const ss = {
    getSheetByName: (n) => sheets[n] || null,
    insertSheet: (n) => (sheets[n] = makeSheet(n)),
  };
  const ctx = {
    console,
    SpreadsheetApp: { getActiveSpreadsheet: () => ss },
    LockService: { getScriptLock: () => ({ waitLock() {}, releaseLock() {} }) },
    Utilities: {
      getUuid: () => crypto.randomUUID(),
      DigestAlgorithm: { SHA_256: 'sha256' }, Charset: { UTF_8: 'utf8' },
      computeDigest: (alg, s) => [...crypto.createHash('sha256').update(s, 'utf8').digest()].map((b) => (b > 127 ? b - 256 : b)),
    },
    ContentService: { MimeType: { JSON: 'json' }, createTextOutput: (t) => ({ text: t, setMimeType() { return this; } }) },
  };
  vm.createContext(ctx);
  vm.runInContext(fs.readFileSync(path.join(__dirname, '..', 'Code.gs'), 'utf8') + '\n;this.doGet=doGet;this.doPost=doPost;', ctx);
  return { ctx, sheets, call: (obj) => JSON.parse(ctx.doPost({ postData: { contents: JSON.stringify(obj) } }).text), get: () => JSON.parse(ctx.doGet().text) };
}
module.exports = { makeEnv };

if (require.main === module) {
  let env = makeEnv(); const port = +process.argv[2] || 8787;
  http.createServer((req, res) => {
    const h = { 'Access-Control-Allow-Origin': '*', 'Content-Type': 'application/json' };
    if (req.url.includes('reset')) { env = makeEnv(); res.writeHead(200, h); res.end('{"ok":true}'); return; }
    if (req.method === 'GET') { res.writeHead(200, h); res.end(JSON.stringify(env.get())); return; }
    let body = ''; req.on('data', (c) => (body += c)); req.on('end', () => {
      // Apps Script отвечает с задержкой — имитируем её.
      setTimeout(() => { res.writeHead(200, h); res.end(env.ctx.doPost({ postData: { contents: body } }).text); }, 150);
    });
  }).listen(port, () => console.log('emulator on ' + port));
}
