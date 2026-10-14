/**
 * КГМУ расписание — сервер для профиля и раздела «Моя группа».
 *
 * Хранит данные в этой Google-таблице:
 *   Users       — профили (имя, аватар, контакт, роль, хеш пароля)
 *   Sessions    — токены входа
 *   Backups     — облачная копия ДЗ, правок и настроек (частями по 40 000 символов)
 *   Homework    — общие ДЗ группы
 *   News        — объявления и переносы пар
 *   Attendance  — пропуски и отработки
 *
 * Установка: Расширения → Apps Script → вставить этот код → Начать развертывание →
 * Новое развертывание → Тип «Веб-приложение» → Запуск от имени: «Я»,
 * Доступ: «Все» → Развернуть. Полученную ссылку (…/exec) вставить в приложение:
 * Настройки → Профиль → «Адрес сервера».
 */

const VERSION = 1;
const CHUNK = 40000;
const SHEETS = {
  Users: ['id', 'group', 'name', 'nameKey', 'avatar', 'contact', 'role', 'salt', 'hash', 'created', 'seen'],
  Sessions: ['token', 'userId', 'created', 'seen'],
  Backups: ['userId', 'updated', 'part', 'data'],
  Homework: ['id', 'group', 'subject', 'date', 'start', 'text', 'authorId', 'author', 'created'],
  News: ['id', 'group', 'kind', 'title', 'text', 'authorId', 'author', 'created'],
  Attendance: ['id', 'group', 'userId', 'date', 'subject', 'status', 'note', 'created', 'updated'],
};
const ROLES = ['starosta', 'deputy', 'member'];
const KINDS = ['news', 'move', 'important'];
const STATUSES = ['absent', 'excused', 'done'];

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
    const res = fn(req);
    return out(Object.assign({ ok: true }, res));
  } catch (err) {
    const msg = String(err && err.message || err);
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
    let sh = ss.getSheetByName(name);
    if (!sh) {
      sh = ss.insertSheet(name);
      sh.getRange(1, 1, 1, SHEETS[name].length).setValues([SHEETS[name]]).setFontWeight('bold');
      sh.setFrozenRows(1);
    }
  });
}

function sheet_(name) { return SpreadsheetApp.getActiveSpreadsheet().getSheetByName(name); }

/** Все строки листа как объекты; _row — номер строки в таблице. */
function rows_(name) {
  const sh = sheet_(name), cols = SHEETS[name];
  const last = sh.getLastRow();
  if (last < 2) return [];
  const vals = sh.getRange(2, 1, last - 1, cols.length).getValues();
  const res = [];
  for (let i = 0; i < vals.length; i++) {
    if (vals[i][0] === '' || vals[i][0] === null) continue;
    const o = { _row: i + 2 };
    cols.forEach(function (c, j) { o[c] = vals[i][j] === null ? '' : String(vals[i][j]); });
    res.push(o);
  }
  return res;
}

/** Текст всегда записываем с апострофом — таблица не превратит его в формулу, число или дату. */
function cell_(v) {
  if (v === null || v === undefined) return '';
  return "'" + String(v);
}

function append_(name, obj) {
  const sh = sheet_(name);
  sh.appendRow(SHEETS[name].map(function (c) { return cell_(obj[c]); }));
}

function update_(name, row, obj) {
  const cols = SHEETS[name];
  sheet_(name).getRange(row._row, 1, 1, cols.length)
    .setValues([cols.map(function (c) { return cell_(c in obj ? obj[c] : row[c]); })]);
}

/** Удаление строк снизу вверх, чтобы номера не сдвигались. */
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

function str_(v, max, required, code) {
  const s = String(v === undefined || v === null ? '' : v).trim();
  if (required && !s) fail(code || 'empty');
  return s.slice(0, max);
}

function key_(name) { return String(name).trim().toLowerCase().replace(/ё/g, 'е').replace(/\s+/g, ' '); }

function publicUser_(u) {
  return { id: u.id, group: u.group, name: u.name, avatar: u.avatar, contact: u.contact, role: u.role };
}

function isLead_(u) { return u.role === 'starosta' || u.role === 'deputy'; }

/** Пользователь по токену. */
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

  register: function (req) {
    const group = str_(req.group, 20, true, 'group');
    const name = str_(req.name, 60, true, 'name');
    const password = String(req.password || '');
    if (password.length < 4) fail('password_short');
    const users = rows_('Users');
    if (users.some(function (u) { return u.group === group && u.nameKey === key_(name); })) fail('name_taken');
    const salt = uid_();
    const first = !users.some(function (u) { return u.group === group; });
    const u = {
      id: uid_(), group: group, name: name, nameKey: key_(name),
      avatar: str_(req.avatar, 40), contact: str_(req.contact, 100),
      role: first ? 'starosta' : 'member', salt: salt, hash: hash_(salt, password), created: now_(), seen: now_(),
    };
    append_('Users', u);
    return { token: newSession_(u), user: publicUser_(u) };
  },

  login: function (req) {
    const group = str_(req.group, 20, true, 'group');
    const name = str_(req.name, 60, true, 'name');
    const u = rows_('Users').filter(function (r) { return r.group === group && r.nameKey === key_(name); })[0];
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
    return { user: publicUser_(u) };
  },

  updateProfile: function (req) {
    const u = auth_(req);
    const ch = {};
    if (req.name !== undefined) {
      const name = str_(req.name, 60, true, 'name');
      if (key_(name) !== u.nameKey && rows_('Users').some(function (r) { return r.group === u.group && r.nameKey === key_(name); })) fail('name_taken');
      ch.name = name; ch.nameKey = key_(name);
    }
    if (req.avatar !== undefined) ch.avatar = str_(req.avatar, 40);
    if (req.contact !== undefined) ch.contact = str_(req.contact, 100);
    if (req.password) {
      if (String(req.password).length < 4) fail('password_short');
      ch.salt = uid_(); ch.hash = hash_(ch.salt, String(req.password));
    }
    update_('Users', u, ch);
    return { user: publicUser_(Object.assign({}, u, ch)) };
  },

  deleteAccount: function (req) {
    const u = auth_(req);
    ['Sessions', 'Backups'].forEach(function (n) {
      deleteRows_(n, rows_(n).filter(function (r) { return r.userId === u.id; }));
    });
    deleteRows_('Attendance', rows_('Attendance').filter(function (r) { return r.userId === u.id; }));
    // Если уходит староста — роль переходит заму или самому раннему участнику.
    if (u.role === 'starosta') {
      const rest = rows_('Users').filter(function (r) { return r.group === u.group && r.id !== u.id; });
      const next = rest.filter(function (r) { return r.role === 'deputy'; })[0] || rest[0];
      if (next) update_('Users', next, { role: 'starosta' });
    }
    deleteRows_('Users', rows_('Users').filter(function (r) { return r.id === u.id; }));
    return {};
  },

  /* ----- Облачная копия ----- */

  backupSave: function (req) {
    const u = auth_(req);
    const data = String(req.data || '');
    if (!data) fail('empty');
    if (data.length > 4000000) fail('too_big');
    deleteRows_('Backups', rows_('Backups').filter(function (r) { return r.userId === u.id; }));
    const updated = now_();
    const sh = sheet_('Backups'), rowsOut = [];
    for (let i = 0, p = 0; i < data.length; i += CHUNK, p++) rowsOut.push([cell_(u.id), cell_(updated), cell_(p), cell_(data.slice(i, i + CHUNK))]);
    sh.getRange(sh.getLastRow() + 1, 1, rowsOut.length, 4).setValues(rowsOut);
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

  /* ----- Группа ----- */

  /** Всё для экрана «Моя группа» одним запросом. */
  group: function (req) {
    const u = auth_(req);
    update_('Users', u, { seen: now_() });
    const mine = function (r) { return r.group === u.group; };
    const users = rows_('Users').filter(mine);
    const names = {};
    users.forEach(function (r) { names[r.id] = r.name; });
    const att = rows_('Attendance').filter(mine).filter(function (r) { return isLead_(u) || r.userId === u.id; });
    return {
      user: publicUser_(u),
      members: users.map(publicUser_).sort(function (a, b) {
        return ROLES.indexOf(a.role) - ROLES.indexOf(b.role) || a.name.localeCompare(b.name, 'ru');
      }),
      homework: rows_('Homework').filter(mine).map(function (r) {
        return { id: r.id, subject: r.subject, date: r.date, start: Number(r.start) || 0, text: r.text, authorId: r.authorId, author: names[r.authorId] || r.author, created: r.created };
      }).sort(function (a, b) { return a.date < b.date ? -1 : a.date > b.date ? 1 : a.start - b.start; }),
      news: rows_('News').filter(mine).map(function (r) {
        return { id: r.id, kind: r.kind, title: r.title, text: r.text, authorId: r.authorId, author: names[r.authorId] || r.author, created: r.created };
      }).sort(function (a, b) { return a.created < b.created ? 1 : -1; }),
      attendance: att.map(function (r) {
        return { id: r.id, userId: r.userId, name: names[r.userId] || '', date: r.date, subject: r.subject, status: r.status, note: r.note, created: r.created };
      }).sort(function (a, b) { return a.date < b.date ? 1 : a.date > b.date ? -1 : 0; }),
    };
  },

  setRole: function (req) {
    const u = auth_(req);
    if (u.role !== 'starosta') fail('forbidden');
    const role = String(req.role);
    if (ROLES.indexOf(role) < 0) fail('bad_role');
    const t = rows_('Users').filter(function (r) { return r.id === req.userId && r.group === u.group; })[0];
    if (!t) fail('not_found');
    if (t.id === u.id) fail('forbidden');
    update_('Users', t, { role: role });
    // Передача роли старосты: прежний староста становится заместителем.
    if (role === 'starosta') update_('Users', u, { role: 'deputy' });
    return {};
  },

  removeMember: function (req) {
    const u = auth_(req);
    if (u.role !== 'starosta') fail('forbidden');
    const t = rows_('Users').filter(function (r) { return r.id === req.userId && r.group === u.group; })[0];
    if (!t || t.id === u.id) fail('not_found');
    ['Sessions', 'Backups', 'Attendance'].forEach(function (n) {
      deleteRows_(n, rows_(n).filter(function (r) { return r.userId === t.id; }));
    });
    deleteRows_('Users', [t]);
    return {};
  },

  hwAdd: function (req) {
    const u = auth_(req);
    const item = {
      id: uid_(), group: u.group, subject: str_(req.subject, 200, true, 'subject'),
      date: str_(req.date, 10, true, 'date'), start: Number(req.start) || 0,
      text: str_(req.text, 3000, true, 'text'), authorId: u.id, author: u.name, created: now_(),
    };
    if (!/^\d{4}-\d{2}-\d{2}$/.test(item.date)) fail('date');
    append_('Homework', item);
    return { id: item.id };
  },

  hwDelete: function (req) {
    const u = auth_(req);
    const t = rows_('Homework').filter(function (r) { return r.id === req.id && r.group === u.group; })[0];
    if (!t) fail('not_found');
    if (t.authorId !== u.id && !isLead_(u)) fail('forbidden');
    deleteRows_('Homework', [t]);
    return {};
  },

  newsAdd: function (req) {
    const u = auth_(req);
    if (!isLead_(u)) fail('forbidden');
    const kind = KINDS.indexOf(req.kind) >= 0 ? req.kind : 'news';
    const item = {
      id: uid_(), group: u.group, kind: kind, title: str_(req.title, 200),
      text: str_(req.text, 5000, true, 'text'), authorId: u.id, author: u.name, created: now_(),
    };
    append_('News', item);
    return { id: item.id };
  },

  newsDelete: function (req) {
    const u = auth_(req);
    const t = rows_('News').filter(function (r) { return r.id === req.id && r.group === u.group; })[0];
    if (!t) fail('not_found');
    if (t.authorId !== u.id && u.role !== 'starosta') fail('forbidden');
    deleteRows_('News', [t]);
    return {};
  },

  attAdd: function (req) {
    const u = auth_(req);
    const item = {
      id: uid_(), group: u.group, userId: u.id, date: str_(req.date, 10, true, 'date'),
      subject: str_(req.subject, 200, true, 'subject'),
      status: STATUSES.indexOf(req.status) >= 0 ? req.status : 'absent',
      note: str_(req.note, 500), created: now_(), updated: now_(),
    };
    if (!/^\d{4}-\d{2}-\d{2}$/.test(item.date)) fail('date');
    append_('Attendance', item);
    return { id: item.id };
  },

  attUpdate: function (req) {
    const u = auth_(req);
    const t = rows_('Attendance').filter(function (r) { return r.id === req.id && r.group === u.group; })[0];
    if (!t) fail('not_found');
    if (t.userId !== u.id && !isLead_(u)) fail('forbidden');
    const ch = { updated: now_() };
    if (STATUSES.indexOf(req.status) >= 0) ch.status = req.status;
    if (req.note !== undefined) ch.note = str_(req.note, 500);
    update_('Attendance', t, ch);
    return {};
  },

  attDelete: function (req) {
    const u = auth_(req);
    const t = rows_('Attendance').filter(function (r) { return r.id === req.id && r.group === u.group; })[0];
    if (!t) fail('not_found');
    if (t.userId !== u.id && !isLead_(u)) fail('forbidden');
    deleteRows_('Attendance', [t]);
    return {};
  },
};
