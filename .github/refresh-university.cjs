const { writeFile } = require('node:fs/promises');
const { resolve } = require('node:path');

const origin = 'https://kirovgma.ru';
const faculties = [
  'lechebnyy-fakultet-raspisanie',
  'raspisanie-pediatricheskiy-fakultet',
  'raspisanie-stomatologicheskiy-fakultet',
  'raspisanie-fakultet-inostrannyh-obuchayushchihsya',
  'raspisanie-socialno-ekonomicheskiy-fakultet',
  'raspisanie-ordinatura',
  'raspisanie-aspirantura',
];
const maxFile = 4 * 1024 * 1024;
const maxSnapshot = 12 * 1024 * 1024;

function textOf(markup) {
  const entities = { amp: '&', quot: '"', apos: "'", lt: '<', gt: '>', nbsp: ' ', ndash: '–', mdash: '—' };
  return markup.replace(/<[^>]+>/g, ' ').replace(/&([a-z]+|#\d+|#x[0-9a-f]+);/gi, (value, entity) => {
    if (!entity.startsWith('#')) return entities[entity.toLowerCase()] ?? value;
    const code = entity[1].toLowerCase() === 'x' ? parseInt(entity.slice(2), 16) : Number(entity.slice(1));
    return code <= 0x10ffff ? String.fromCodePoint(code) : value;
  }).replace(/\s+/g, ' ').trim();
}
function documentUrl(raw) {
  try {
    const url = new URL(raw, origin);
    const path = decodeURIComponent(url.pathname);
    if (url.origin === origin && !url.username && !url.password && !url.search && !url.hash &&
        path.startsWith('/sites/default/files/') && /\.(xlsx|docx)$/i.test(path) &&
        !path.includes('\\') && !path.split('/').some((part) => part === '.' || part === '..')) return url.href;
  } catch {}
  return null;
}
function catalog(html) {
  let program = '', course = '';
  const rows = [];
  const fields = /<div\b[^>]*class="[^"]*\bfield-name-field-kgma-rasp-(spec-name|kurs-name)\b[^"]*"[^>]*>([\s\S]*?)<\/div>|<a\b[^>]*\bhref="([^"]+)"[^>]*>([\s\S]*?)<\/a>/gi;
  for (const [, type, field, href, label] of html.matchAll(fields)) {
    if (type === 'spec-name') { program = textOf(field); course = ''; continue; }
    if (type === 'kurs-name') { course = textOf(field); continue; }
    const url = documentUrl(textOf(href));
    const title = url && textOf(label);
    if (title && !rows.some((row) => row.url === url)) rows.push({ url, title, course, program });
  }
  return rows;
}
async function download(url) {
  const response = await fetch(url, { redirect: 'error', signal: AbortSignal.timeout(18000) });
  if (!response.ok || Number(response.headers.get('content-length')) > maxFile) {
    await response.body?.cancel();
    throw new Error(`${url} returned ${response.status} or exceeds the size limit`);
  }
  let length = 0;
  const chunks = [];
  for await (const chunk of response.body) {
    length += chunk.length;
    if (length > maxFile) throw new Error(`${url} exceeds the size limit`);
    chunks.push(chunk);
  }
  return Buffer.concat(chunks);
}
async function mapInBatches(values, transform) {
  const entries = [];
  for (let i = 0; i < values.length; i += 4) {
    entries.push(...await Promise.all(values.slice(i, i + 4).map(transform)));
  }
  return entries;
}
async function main() {
  const [destination] = process.argv.slice(2);
  if (!destination) throw new Error('Provide the output JSON path');
  const catalogs = Object.fromEntries(await mapInBatches(faculties, async (faculty) => {
    const entries = catalog((await download(`${origin}/${faculty}`)).toString('utf8'));
    if (!entries.length || entries.length > 150) throw new Error(`Could not read ${faculty}`);
    return [faculty, entries];
  }));
  const links = [...new Set(Object.values(catalogs).flat().map((row) => row.url).filter((url) => /\.xlsx$/i.test(url)))];
  if (!links.length || links.length > 200) throw new Error('Unexpected number of spreadsheets');

  let totalBytes = 0;
  const workbooks = Object.fromEntries(await mapInBatches(links, async (url) => {
    const data = await download(url);
    totalBytes += data.length;
    if (totalBytes > maxSnapshot) throw new Error('The snapshot exceeds the size limit');
    if (data.length < 4 || data.readUInt32LE(0) !== 0x04034b50) throw new Error(`Invalid spreadsheet at ${url}`);
    return [url, data.toString('base64')];
  }));
  await writeFile(resolve(destination), JSON.stringify({ version: 1, generatedAt: new Date().toISOString(), catalogs, workbooks }) + '\n');
  console.log(`Downloaded ${links.length} timetables from ${faculties.length} university sections`);
}
main().catch((error) => { console.error(error.message); process.exitCode = 1; });
