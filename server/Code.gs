/**
 * КГМУ расписание — сервер профилей и синхронизации.
 *
 * Хранит данные в этой Google Таблице:
 *   Users    — профили: юзернейм, полное имя, номер группы, аватар, хеш пароля
 *   Sessions — токены входа на устройствах
 *   Backups  — облачная копия ДЗ, правок и настроек (частями по 40 000 символов)
 *   Files    — файл расписания из копии (отдельно: он большой и меняется редко)
 *   Groups   — группы: название, создатель, староста
 *   Members  — участники групп и их роли
 *   News     — объявления (хранятся 3 недели, затем удаляются сами)
 *   Absences — личные пропуски и отработки
 *   GroupHw  — домашние задания для всей группы
 *   Duty     — дежурные на пары
 *
 * Установка: Расширения → Apps Script → вставить этот код → Сохранить →
 * Начать развертывание → Новое развертывание → тип «Веб-приложение»,
 * Запуск от имени: «Я», Доступ: «Все» → Развернуть → скопировать ссылку …/exec.
 */

const VERSION = 6;
const HW_KEEP_DAYS = 30;   // ДЗ группы хранится столько дней после даты пары
const DUTY_KEEP_DAYS = 7;  // дежурства — столько дней после пары
const NEWS_DAYS = 21; // сколько дней хранятся объявления
const CHUNK = 40000;
const SHEETS = {
  Users: ['id', 'username', 'fullName', 'group', 'avatar', 'salt', 'hash', 'created', 'seen'],
  Sessions: ['token', 'userId', 'created', 'seen'],
  Backups: ['userId', 'updated', 'part', 'data'],
  Files: ['userId', 'updated', 'part', 'data'],
  Groups: ['id', 'name', 'creatorId', 'starostaId', 'created'],
  Members: ['groupId', 'userId', 'role', 'added'],
  News: ['id', 'groupId', 'authorId', 'text', 'created'],
  Absences: ['id', 'userId', 'date', 'subject', 'note', 'makeupDate', 'makeupTime', 'makeupPlace', 'done', 'created', 'updated'],
  GroupHw: ['id', 'groupId', 'authorId', 'subject', 'date', 'start', 'text', 'created'],
  Duty: ['groupId', 'date', 'start', 'subject', 'userId', 'setBy', 'created'],
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
  const fn = ACTIONS[req.action];
  if (!fn) return out({ ok: false, error: 'unknown_action' });
  // Чтение идёт параллельно, без очереди; блокируются только изменения.
  const lock = READ_ONLY[req.action] ? null : LockService.getScriptLock();
  try {
    if (lock && !lock.tryLock(15000)) return out({ ok: false, error: 'busy' });
    setup_();
    return out(Object.assign({ ok: true }, fn(req)));
  } catch (err) {
    const msg = String((err && err.message) || err);
    return out({ ok: false, error: msg.indexOf('E:') === 0 ? msg.slice(2) : 'server', detail: msg });
  } finally {
    if (lock) try { lock.releaseLock(); } catch (e2) {}
  }
}

function out(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}

function fail(code) { throw new Error('E:' + code); }

/* ---------------- Таблица ---------------- */

/** Действия, которые только читают таблицу, — их не нужно ставить в очередь. */
const READ_ONLY = { ping: true, checkUsername: true, me: true, sync: true, poll: true, backupLoad: true, backupInfo: true };

let ready_ = false;
function setup_() {
  if (ready_) return;
  ready_ = true;
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

/**
 * Строки листа, у которых первый столбец равен value — без чтения всего листа
 * (лист копий большой: в нём файлы расписания всех пользователей).
 */
function findRows_(name, value) {
  const sh = sheet_(name), cols = SHEETS[name], last = sh.getLastRow();
  if (last < 2 || !value) return [];
  const cells = sh.getRange(2, 1, last - 1, 1).createTextFinder(String(value)).matchEntireCell(true).findAll();
  return cells.map(function (c) {
    const r = c.getRow(), vals = sh.getRange(r, 1, 1, cols.length).getValues()[0], o = { _row: r };
    cols.forEach(function (k, j) { o[k] = vals[j] === null ? '' : String(vals[j]); });
    return o;
  });
}

/** Номера строк по первому столбцу (без чтения содержимого). */
function findRowNums_(name, value) {
  const sh = sheet_(name), last = sh.getLastRow();
  if (last < 2 || !value) return [];
  return sh.getRange(2, 1, last - 1, 1).createTextFinder(String(value)).matchEntireCell(true).findAll()
    .map(function (c) { return c.getRow(); });
}

/** Записать большой текст частями по CHUNK символов вместо прежних строк пользователя. */
function saveParts_(name, userId, text) {
  const old = findRowNums_(name, userId), updated = now_(), sh = sheet_(name), rows = [];
  for (let i = 0, p = 0; i < text.length; i += CHUNK, p++) rows.push([cell_(userId), cell_(updated), cell_(p), cell_(text.slice(i, i + CHUNK))]);
  if (rows.length) sh.getRange(sh.getLastRow() + 1, 1, rows.length, 4).setValues(rows);
  old.sort(function (a, b) { return b - a; }).forEach(function (n) { sh.deleteRow(n); });
  return updated;
}

function loadParts_(name, userId) {
  const parts = findRows_(name, userId).sort(function (a, b) { return Number(a.part) - Number(b.part); });
  return parts.length ? { data: parts.map(function (r) { return r.data; }).join(''), updated: parts[0].updated } : null;
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
  const s = findRows_('Sessions', token)[0];
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

/* ---------------- Группы ---------------- */

function membership_(u) { return rows_('Members').filter(function (r) { return r.userId === u.id; })[0] || null; }

function groupOf_(u) {
  const m = membership_(u);
  return m ? rows_('Groups').filter(function (g) { return g.id === m.groupId; })[0] || null : null;
}

/** Группа пользователя или ошибка, если он ни в одной не состоит. */
function myGroup_(u) {
  const g = groupOf_(u);
  if (!g) fail('no_group');
  return g;
}

/** Создатель и староста управляют составом и ролями. */
function canManage_(g, u) { return g.creatorId === u.id || g.starostaId === u.id; }

function deleteGroup_(g) {
  ['Members', 'News', 'GroupHw', 'Duty'].forEach(function (n) {
    deleteRows_(n, rows_(n).filter(function (r) { return r.groupId === g.id; }));
  });
  deleteRows_('Groups', [g]);
}

/** Удаляет объявления старше NEWS_DAYS дней — чтобы таблица не разрасталась. */
function pruneNews_() {
  const border = new Date(Date.now() - NEWS_DAYS * 86400000).toISOString();
  const old = rows_('News').filter(function (r) { return r.created < border; });
  if (old.length) deleteRows_('News', old);
  return old.length;
}

/** Дата «ГГГГ-ММ-ДД» на n дней раньше сегодняшней. */
function daysAgo_(n) { return new Date(Date.now() - n * 86400000).toISOString().slice(0, 10); }

/** Удаляет старые ДЗ группы и прошедшие дежурства. */
function pruneOld_() {
  const hw = rows_('GroupHw').filter(function (r) { return r.date < daysAgo_(HW_KEEP_DAYS); });
  if (hw.length) deleteRows_('GroupHw', hw);
  const duty = rows_('Duty').filter(function (r) { return r.date < daysAgo_(DUTY_KEEP_DAYS); });
  if (duty.length) deleteRows_('Duty', duty);
}

/** Для триггера по времени (необязательно): Триггеры → cleanupOldNews → раз в день. */
function cleanupOldNews() {
  setup_();
  pruneOld_();
  return pruneNews_();
}

function groupView_(u) {
  const g = groupOf_(u);
  if (!g) return { group: null, members: [], news: [], homework: [], duties: [] };
  const users = {};
  rows_('Users').forEach(function (r) { users[r.id] = r; });
  const members = rows_('Members').filter(function (m) { return m.groupId === g.id && users[m.userId]; }).map(function (m) {
    const x = users[m.userId];
    return {
      id: x.id, username: x.username, fullName: x.fullName, avatar: x.avatar, role: m.role,
      creator: x.id === g.creatorId, starosta: x.id === g.starostaId,
    };
  }).sort(function (a, b) {
    const w = function (m) { return m.creator ? 0 : m.starosta ? 1 : m.role ? 2 : 3; };
    return w(a) - w(b) || a.fullName.localeCompare(b.fullName, 'ru');
  });
  const border = new Date(Date.now() - NEWS_DAYS * 86400000).toISOString();
  const news = rows_('News').filter(function (n) { return n.groupId === g.id && n.created >= border; }).map(function (n) {
    const a = users[n.authorId];
    return { id: n.id, text: n.text, created: n.created, authorId: n.authorId,
      author: a ? a.fullName : '', authorUsername: a ? a.username : '', authorAvatar: a ? a.avatar : '' };
  }).sort(function (a, b) { return a.created < b.created ? 1 : -1; });
  const homework = rows_('GroupHw').filter(function (h) { return h.groupId === g.id && h.date >= daysAgo_(HW_KEEP_DAYS); }).map(function (h) {
    const a = users[h.authorId];
    return { id: h.id, subject: h.subject, date: h.date, start: Number(h.start) || 0, text: h.text, created: h.created,
      authorId: h.authorId, author: a ? a.fullName : '' };
  }).sort(function (a, b) { return a.date < b.date ? -1 : a.date > b.date ? 1 : a.start - b.start; });
  const duties = rows_('Duty').filter(function (d) { return d.groupId === g.id && d.date >= daysAgo_(DUTY_KEEP_DAYS); }).map(function (d) {
    const x = users[d.userId];
    return { date: d.date, start: Number(d.start) || 0, subject: d.subject, userId: d.userId,
      fullName: x ? x.fullName : '', avatar: x ? x.avatar : '' };
  });
  return {
    group: { id: g.id, name: g.name, creatorId: g.creatorId, starostaId: g.starostaId, created: g.created },
    members: members, news: news, homework: homework, duties: duties,
  };
}

function absences_(u) {
  return rows_('Absences').filter(function (r) { return r.userId === u.id; }).map(function (r) {
    return { id: r.id, date: r.date, subject: r.subject, note: r.note, makeupDate: r.makeupDate,
      makeupTime: r.makeupTime, makeupPlace: r.makeupPlace, done: r.done === 'true', created: r.created };
  }).sort(function (a, b) { return a.date < b.date ? 1 : a.date > b.date ? -1 : 0; });
}

function date_(v, required) {
  const s = str_(v, 10);
  if (!s && !required) return '';
  if (!/^\d{4}-\d{2}-\d{2}$/.test(s)) fail('bad_date');
  return s;
}

function time_(v) {
  const s = str_(v, 5);
  if (s && !/^\d{1,2}:\d{2}$/.test(s)) fail('bad_time');
  return s;
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
    deleteRows_('Sessions', findRows_('Sessions', token));
    return {};
  },

  me: function (req) {
    return { user: publicUser_(auth_(req)) };
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
    const g = groupOf_(u);
    if (g && g.creatorId === u.id) deleteGroup_(g);
    else if (g && g.starostaId === u.id) update_('Groups', g, { starostaId: '' });
    ['Sessions', 'Backups', 'Files', 'Members', 'Absences', 'Duty'].forEach(function (n) {
      deleteRows_(n, rows_(n).filter(function (r) { return r.userId === u.id; }));
    });
    deleteRows_('Users', [u]);
    return {};
  },

  /* ----- Облачная копия ----- */

  /**
   * data — ДЗ, правки и настройки (маленькие, сохраняются часто).
   * file — файл расписания: присылается, только когда изменился.
   */
  backupSave: function (req) {
    const u = auth_(req);
    const data = String(req.data || '');
    if (!data) fail('empty');
    if (data.length > 4000000) fail('too_big');
    const updated = saveParts_('Backups', u.id, data);
    if (req.file) {
      if (String(req.file).length > 6000000) fail('too_big');
      saveParts_('Files', u.id, String(req.file));
    }
    if (req.group !== undefined && str_(req.group, 20) !== u.group) update_('Users', u, { group: str_(req.group, 20) });
    return { updated: updated, size: data.length };
  },

  backupLoad: function (req) {
    const u = auth_(req);
    const b = loadParts_('Backups', u.id);
    if (!b) return { data: null, updated: null, file: null };
    const f = loadParts_('Files', u.id);
    return { data: b.data, updated: b.updated, file: f ? f.data : null };
  },

  backupInfo: function (req) {
    const u = auth_(req);
    const p = findRows_('Backups', u.id)[0];
    return { updated: p ? p.updated : null, hasFile: findRowNums_('Files', u.id).length > 0 };
  },

  /* ----- Группа ----- */

  /** Всё для экрана «Моя группа» одним запросом: группа, участники, объявления, мои пропуски. */
  sync: function (req) {
    const u = auth_(req);
    return Object.assign({ user: publicUser_(u), absences: absences_(u) }, groupView_(u));
  },

  groupCreate: function (req) {
    const u = auth_(req);
    if (membership_(u)) fail('already_in_group');
    const name = str_(req.name, 60);
    if (!name) fail('bad_name');
    const g = { id: uid_(), name: name, creatorId: u.id, starostaId: '', created: now_() };
    append_('Groups', g);
    append_('Members', { groupId: g.id, userId: u.id, role: '', added: now_() });
    return groupView_(u);
  },

  groupRename: function (req) {
    const u = auth_(req), g = myGroup_(u);
    if (g.creatorId !== u.id) fail('forbidden');
    const name = str_(req.name, 60);
    if (!name) fail('bad_name');
    update_('Groups', g, { name: name });
    return groupView_(u);
  },

  /** Добавить одногруппника по юзернейму (создатель или староста). */
  memberAdd: function (req) {
    const u = auth_(req), g = myGroup_(u);
    if (!canManage_(g, u)) fail('forbidden');
    const name = String(req.username || '').trim().replace(/^@/, '').toLowerCase();
    const t = rows_('Users').filter(function (r) { return r.username === name; })[0];
    if (!t) fail('user_not_found');
    const m = membership_(t);
    if (m) fail(m.groupId === g.id ? 'already_member' : 'in_other_group');
    append_('Members', { groupId: g.id, userId: t.id, role: '', added: now_() });
    return groupView_(u);
  },

  memberRemove: function (req) {
    const u = auth_(req), g = myGroup_(u);
    if (!canManage_(g, u)) fail('forbidden');
    if (req.userId === g.creatorId || req.userId === u.id) fail('forbidden');
    const m = rows_('Members').filter(function (r) { return r.groupId === g.id && r.userId === req.userId; })[0];
    if (!m) fail('not_found');
    deleteRows_('Members', [m]);
    deleteRows_('Duty', rows_('Duty').filter(function (d) { return d.groupId === g.id && d.userId === req.userId; }));
    if (g.starostaId === req.userId) update_('Groups', g, { starostaId: '' });
    return groupView_(u);
  },

  /** Своя роль: любая подпись — «Профорг», «Журнал», «Ответственный за ДЗ»… Пусто — снять. */
  memberRole: function (req) {
    const u = auth_(req), g = myGroup_(u);
    if (!canManage_(g, u)) fail('forbidden');
    const m = rows_('Members').filter(function (r) { return r.groupId === g.id && r.userId === req.userId; })[0];
    if (!m) fail('not_found');
    update_('Members', m, { role: str_(req.role, 40) });
    return groupView_(u);
  },

  /** Назначить старосту (только создатель). userId пустой — снять. */
  setStarosta: function (req) {
    const u = auth_(req), g = myGroup_(u);
    if (g.creatorId !== u.id) fail('forbidden');
    const id = String(req.userId || '');
    if (id && !rows_('Members').some(function (r) { return r.groupId === g.id && r.userId === id; })) fail('not_found');
    update_('Groups', g, { starostaId: id });
    return groupView_(u);
  },

  groupLeave: function (req) {
    const u = auth_(req), g = myGroup_(u);
    if (g.creatorId === u.id) fail('creator_cannot_leave');
    deleteRows_('Members', rows_('Members').filter(function (r) { return r.userId === u.id; }));
    deleteRows_('Duty', rows_('Duty').filter(function (d) { return d.groupId === g.id && d.userId === u.id; }));
    if (g.starostaId === u.id) update_('Groups', g, { starostaId: '' });
    return { group: null, members: [], news: [], homework: [], duties: [] };
  },

  groupDelete: function (req) {
    const u = auth_(req), g = myGroup_(u);
    if (g.creatorId !== u.id) fail('forbidden');
    deleteGroup_(g);
    return { group: null, members: [], news: [], homework: [], duties: [] };
  },

  /* ----- Объявления: пишут все участники, хранятся 3 недели ----- */

  newsAdd: function (req) {
    const u = auth_(req), g = myGroup_(u);
    const text = str_(req.text, 3000);
    if (!text) fail('empty');
    append_('News', { id: uid_(), groupId: g.id, authorId: u.id, text: text, created: now_() });
    pruneNews_();
    return groupView_(u);
  },

  newsDelete: function (req) {
    const u = auth_(req), g = myGroup_(u);
    const n = rows_('News').filter(function (r) { return r.id === req.id && r.groupId === g.id; })[0];
    if (!n) fail('not_found');
    if (n.authorId !== u.id && !canManage_(g, u)) fail('forbidden');
    deleteRows_('News', [n]);
    return groupView_(u);
  },

  /**
   * Лёгкая проверка для уведомлений (фоновая, раз в 15 минут): моя группа и
   * объявления других участников новее since.
   */
  poll: function (req) {
    const u = auth_(req), m = membership_(u), now = now_();
    if (!m) return { group: null, news: [], homework: [], now: now, duty: '' };
    const g = rows_('Groups').filter(function (x) { return x.id === m.groupId; })[0];
    if (!g) return { group: null, news: [], homework: [], now: now, duty: '' };
    const since = String(req.since || '');
    const names = {};
    rows_('Users').forEach(function (r) { names[r.id] = r.fullName; });
    const news = rows_('News').filter(function (n) {
      return n.groupId === g.id && n.authorId !== u.id && n.created > since;
    }).map(function (n) {
      return { id: n.id, text: n.text, created: n.created, author: names[n.authorId] || '' };
    }).sort(function (a, b) { return a.created < b.created ? -1 : 1; });
    const homework = rows_('GroupHw').filter(function (h) {
      return h.groupId === g.id && h.authorId !== u.id && h.created > since;
    }).map(function (h) {
      return { id: h.id, subject: h.subject, date: h.date, text: h.text, created: h.created, author: names[h.authorId] || '' };
    });
    // Отпечаток дежурств: меняется при любом назначении или снятии — тогда клиент обновит данные.
    const duties = rows_('Duty').filter(function (d) { return d.groupId === g.id; });
    const duty = duties.length + '|' + duties.map(function (d) { return d.created; }).sort().pop();
    // now — время сервера: клиент запоминает его как since (часы телефона могут спешить).
    return { group: { id: g.id, name: g.name }, added: m.added, news: news, homework: homework, now: now, duty: duty };
  },

  /* ----- ДЗ для всей группы: добавляет любой участник ----- */

  groupHwAdd: function (req) {
    const u = auth_(req), g = myGroup_(u);
    const item = {
      id: uid_(), groupId: g.id, authorId: u.id, subject: str_(req.subject, 200), date: date_(req.date, true),
      start: Math.max(0, Math.min(1439, Number(req.start) || 0)), text: str_(req.text, 3000), created: now_(),
    };
    if (!item.subject) fail('bad_subject');
    if (!item.text) fail('empty');
    append_('GroupHw', item);
    pruneOld_();
    return groupView_(u);
  },

  groupHwDelete: function (req) {
    const u = auth_(req), g = myGroup_(u);
    const h = rows_('GroupHw').filter(function (r) { return r.id === req.id && r.groupId === g.id; })[0];
    if (!h) fail('not_found');
    if (h.authorId !== u.id && !canManage_(g, u)) fail('forbidden');
    deleteRows_('GroupHw', [h]);
    return groupView_(u);
  },

  /* ----- Дежурный на пару: назначают создатель и староста ----- */

  /** userId пустой — снять дежурного с этой пары. */
  dutySet: function (req) {
    const u = auth_(req), g = myGroup_(u);
    if (!canManage_(g, u)) fail('forbidden');
    const date = date_(req.date, true), start = String(Math.max(0, Math.min(1439, Number(req.start) || 0)));
    const userId = String(req.userId || '');
    if (userId && !rows_('Members').some(function (r) { return r.groupId === g.id && r.userId === userId; })) fail('not_found');
    deleteRows_('Duty', rows_('Duty').filter(function (d) { return d.groupId === g.id && d.date === date && d.start === start; }));
    if (userId) append_('Duty', { groupId: g.id, date: date, start: start, subject: str_(req.subject, 200), userId: userId, setBy: u.id, created: now_() });
    return groupView_(u);
  },

  /* ----- Личные пропуски и отработки (видит только сам пользователь) ----- */

  absSave: function (req) {
    const u = auth_(req);
    const item = {
      date: date_(req.date, true), subject: str_(req.subject, 200), note: str_(req.note, 500),
      makeupDate: date_(req.makeupDate, false), makeupTime: time_(req.makeupTime), makeupPlace: str_(req.makeupPlace, 200),
      done: req.done ? 'true' : 'false', updated: now_(),
    };
    if (!item.subject) fail('bad_subject');
    if (req.id) {
      const r = rows_('Absences').filter(function (x) { return x.id === req.id && x.userId === u.id; })[0];
      if (!r) fail('not_found');
      update_('Absences', r, item);
    } else {
      append_('Absences', Object.assign({ id: uid_(), userId: u.id, created: now_() }, item));
    }
    return { absences: absences_(u) };
  },

  absDelete: function (req) {
    const u = auth_(req);
    const r = rows_('Absences').filter(function (x) { return x.id === req.id && x.userId === u.id; })[0];
    if (!r) fail('not_found');
    deleteRows_('Absences', [r]);
    return { absences: absences_(u) };
  },
};
