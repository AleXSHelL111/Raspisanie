/**
 * КГМУ расписание — сервер профилей и синхронизации.
 *
 * Хранит данные в этой Google Таблице:
 *   Users    — профили: юзернейм, полное имя, группа, аватар, хеш пароля
 *   Sessions — токены входа на устройствах
 *   Backups  — облачная копия ДЗ, правок, настроек и файла расписания
 *              (частями по 40 000 символов — ограничение ячейки)
 *
 * Установка: Расширения → Apps Script → вставить этот код → Сохранить →
 * Начать развертывание → Новое развертывание → тип «Веб-приложение»,
 * Запуск от имени: «Я», Доступ: «Все» → Развернуть → скопировать ссылку …/exec.
 */

const VERSION = 2;
const CHUNK = 40000;
const SHEETS = {
  Users: ['id', 'username', 'fullName', 'group', 'avatar', 'salt', 'hash', 'created', 'seen'],
  Sessions: ['token', 'userId', 'created', 'seen'],
  Backups: ['userId', 'updated', 'part', 'data'],
};

/* ---------------- Вход ---------------- */

function doGet() {
  return out({ ok: true, app: 'kgmu', version: VERSION });
}

function doPost(e) {
  let req;
  try {
    req = JSON.parse((e && e.postData && e.postData.contents) || '{}');
  } catch (err) {
    return out({ ok: false, error: 'bad_json' });
  }
  const lock = LockService.getScriptLock();
  try {
    lock.waitLock(20000);
    setup_();
    const fn = ACTIONS[req.action];
    if (!fn) return out({ ok: false, error: 'unknown_action' });
    return out(Object.assign({ ok: true }, fn(req)));
  } catch (err) {
    const msg = String((err && err.message) || err);
    return out({ ok: false, error: msg.indexOf('E:') === 0 ? msg.slice(2) : 'server', detail: msg });
  } finally {
    try { lock.releaseLock(); } catch (e2) {}
  }
}

function out(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}

function fail(code) { throw new Error('E:' + code); }

/* ---------------- Таблица ---------------- */

function setup_() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  Object.keys(SHEETS).forEach(function (name) {
    if (ss.getSheetByName(name)) return;
    const sh = ss.insertSheet(name);
    sh.getRange(1, 1, 1, SHEETS[name].length).setValues([SHEETS[name]]).setFontWeight('bold');
    sh.setFrozenRows(1);
  });
}

function sheet_(name) { return SpreadsheetApp.getActiveSpreadsheet().getSheetByName(name); }

/** Все строки листа как объекты; _row — номер строки в таблице. */
function rows_(name) {
  const sh = sheet_(name), cols = SHEETS[name], last = sh.getLastRow();
  if (last < 2) return [];
  const vals = sh.getRange(2, 1, last - 1, cols.length).getValues(), res = [];
  for (let i = 0; i < vals.length; i++) {
    if (vals[i][0] === '' || vals[i][0] === null) continue;
    const o = { _row: i + 2 };
    cols.forEach(function (c, j) { o[c] = vals[i][j] === null ? '' : String(vals[i][j]); });
    res.push(o);
  }
  return res;
}

/** Текст пишем с апострофом — таблица не превратит его в формулу, число или дату. */
function cell_(v) { return v === null || v === undefined ? '' : "'" + String(v); }

function append_(name, obj) {
  sheet_(name).appendRow(SHEETS[name].map(function (c) { return cell_(obj[c]); }));
}

function update_(name, row, obj) {
  const cols = SHEETS[name];
  sheet_(name).getRange(row._row, 1, 1, cols.length)
    .setValues([cols.map(function (c) { return cell_(c in obj ? obj[c] : row[c]); })]);
}

/** Удаление снизу вверх, чтобы номера строк не сдвигались. */
function deleteRows_(name, list) {
  const sh = sheet_(name);
  list.map(function (r) { return r._row; }).sort(function (a, b) { return b - a; })
    .forEach(function (n) { sh.deleteRow(n); });
}

/* ---------------- Утилиты ---------------- */

function uid_() { return Utilities.getUuid().replace(/-/g, ''); }
function now_() { return new Date().toISOString(); }

function hash_(salt, password) {
  const bytes = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, salt + '|' + password, Utilities.Charset.UTF_8);
  return bytes.map(function (b) { return ('0' + (b & 0xff).toString(16)).slice(-2); }).join('');
}

function str_(v, max) { return String(v === undefined || v === null ? '' : v).trim().slice(0, max); }

/** Юзернейм: 3–24 символа, латиница, цифры, «_» и «.», без учёта регистра. */
function username_(v) {
  const u = String(v || '').trim().replace(/^@/, '').toLowerCase();
  if (!/^[a-z0-9_.]{3,24}$/.test(u)) fail('bad_username');
  return u;
}

function fullName_(v) {
  const n = str_(v, 80).replace(/\s+/g, ' ');
  if (n.split(' ').filter(Boolean).length < 2) fail('bad_fullname');
  return n;
}

function password_(v) {
  const p = String(v || '');
  if (p.length < 4) fail('password_short');
  return p;
}

function publicUser_(u) {
  return { id: u.id, username: u.username, fullName: u.fullName, group: u.group, avatar: u.avatar, created: u.created };
}

function auth_(req) {
  const token = String(req.token || '');
  if (!token) fail('auth');
  const s = rows_('Sessions').filter(function (r) { return r.token === token; })[0];
  if (!s) fail('auth');
  const u = rows_('Users').filter(function (r) { return r.id === s.userId; })[0];
  if (!u) fail('auth');
  return u;
}

function newSession_(u) {
  const token = uid_() + uid_();
  append_('Sessions', { token: token, userId: u.id, created: now_(), seen: now_() });
  return token;
}

/* ---------------- Действия ---------------- */

const ACTIONS = {
  ping: function () { return { app: 'kgmu', version: VERSION }; },

  /** Свободен ли юзернейм (для подсказки при регистрации). */
  checkUsername: function (req) {
    const u = username_(req.username);
    return { free: !rows_('Users').some(function (r) { return r.username === u; }) };
  },

  register: function (req) {
    const username = username_(req.username);
    const fullName = fullName_(req.fullName);
    const password = password_(req.password);
    if (rows_('Users').some(function (r) { return r.username === username; })) fail('username_taken');
    const salt = uid_();
    const u = {
      id: uid_(), username: username, fullName: fullName, group: str_(req.group, 20), avatar: str_(req.avatar, 40),
      salt: salt, hash: hash_(salt, password), created: now_(), seen: now_(),
    };
    append_('Users', u);
    return { token: newSession_(u), user: publicUser_(u) };
  },

  login: function (req) {
    const username = String(req.username || '').trim().replace(/^@/, '').toLowerCase();
    const u = rows_('Users').filter(function (r) { return r.username === username; })[0];
    if (!u || hash_(u.salt, String(req.password || '')) !== u.hash) fail('bad_login');
    update_('Users', u, { seen: now_() });
    return { token: newSession_(u), user: publicUser_(u) };
  },

  logout: function (req) {
    const token = String(req.token || '');
    deleteRows_('Sessions', rows_('Sessions').filter(function (r) { return r.token === token; }));
    return {};
  },

  me: function (req) {
    const u = auth_(req);
    update_('Users', u, { seen: now_() });
    return { user: publicUser_(u) };
  },

  updateProfile: function (req) {
    const u = auth_(req), ch = {};
    if (req.username !== undefined) {
      const name = username_(req.username);
      if (name !== u.username && rows_('Users').some(function (r) { return r.username === name; })) fail('username_taken');
      ch.username = name;
    }
    if (req.fullName !== undefined) ch.fullName = fullName_(req.fullName);
    if (req.group !== undefined) ch.group = str_(req.group, 20);
    if (req.avatar !== undefined) ch.avatar = str_(req.avatar, 40);
    if (req.password) {
      if (!req.oldPassword || hash_(u.salt, String(req.oldPassword)) !== u.hash) fail('bad_password');
      ch.salt = uid_();
      ch.hash = hash_(ch.salt, password_(req.password));
    }
    update_('Users', u, ch);
    return { user: publicUser_(Object.assign({}, u, ch)) };
  },

  deleteAccount: function (req) {
    const u = auth_(req);
    if (hash_(u.salt, String(req.password || '')) !== u.hash) fail('bad_password');
    ['Sessions', 'Backups'].forEach(function (n) {
      deleteRows_(n, rows_(n).filter(function (r) { return r.userId === u.id; }));
    });
    deleteRows_('Users', [u]);
    return {};
  },

  /* ----- Облачная копия ----- */

  backupSave: function (req) {
    const u = auth_(req);
    const data = String(req.data || '');
    if (!data) fail('empty');
    if (data.length > 4000000) fail('too_big');
    deleteRows_('Backups', rows_('Backups').filter(function (r) { return r.userId === u.id; }));
    const updated = now_(), sh = sheet_('Backups'), rows = [];
    for (let i = 0, p = 0; i < data.length; i += CHUNK, p++) rows.push([cell_(u.id), cell_(updated), cell_(p), cell_(data.slice(i, i + CHUNK))]);
    sh.getRange(sh.getLastRow() + 1, 1, rows.length, 4).setValues(rows);
    if (req.group !== undefined) update_('Users', u, { group: str_(req.group, 20), seen: now_() });
    return { updated: updated, size: data.length };
  },

  backupLoad: function (req) {
    const u = auth_(req);
    const parts = rows_('Backups').filter(function (r) { return r.userId === u.id; })
      .sort(function (a, b) { return Number(a.part) - Number(b.part); });
    if (!parts.length) return { data: null, updated: null };
    return { data: parts.map(function (r) { return r.data; }).join(''), updated: parts[0].updated };
  },

  backupInfo: function (req) {
    const u = auth_(req);
    const p = rows_('Backups').filter(function (r) { return r.userId === u.id; })[0];
    return { updated: p ? p.updated : null };
  },
};
