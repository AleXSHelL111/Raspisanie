// Проверка логики сервера: node server/test/api.test.cjs
const assert = require('assert');
const { makeEnv } = require('./emulator.cjs');
const env = makeEnv(), c = env.call;
let r;

assert.equal(env.get().app, 'kgmu');

// Регистрация: обязательны полное имя (имя + фамилия) и юзернейм
r = c({ action: 'register', username: 'anna', fullName: 'Анна', password: 'secret' }); assert.equal(r.error, 'bad_fullname');
r = c({ action: 'register', username: 'аня', fullName: 'Анна Иванова', password: 'secret' }); assert.equal(r.error, 'bad_username');
r = c({ action: 'register', username: 'an', fullName: 'Анна Иванова', password: 'secret' }); assert.equal(r.error, 'bad_username');
r = c({ action: 'register', username: 'anna', fullName: 'Анна Иванова', password: '12' }); assert.equal(r.error, 'password_short');
const a = c({ action: 'register', username: '@Anna.Ivanova', fullName: '  Анна   Иванова ', password: 'secret', group: '313', avatar: '🦊|#00E5FF' });
assert.ok(a.ok, JSON.stringify(a)); assert.equal(a.user.username, 'anna.ivanova'); assert.equal(a.user.fullName, 'Анна Иванова'); assert.equal(a.user.group, '313');
r = c({ action: 'checkUsername', username: 'ANNA.IVANOVA' }); assert.equal(r.free, false);
r = c({ action: 'checkUsername', username: 'boris' }); assert.equal(r.free, true);
r = c({ action: 'register', username: 'anna.ivanova', fullName: 'Другая Анна', password: 'xxxx' }); assert.equal(r.error, 'username_taken');
const b = c({ action: 'register', username: 'boris_p', fullName: 'Борис Петров', password: '=1+1' });

// Вход по юзернейму (регистр и @ не важны)
r = c({ action: 'login', username: 'anna.ivanova', password: 'nope' }); assert.equal(r.error, 'bad_login');
r = c({ action: 'login', username: '@ANNA.IVANOVA', password: 'secret' }); assert.ok(r.ok); const a2 = r.token;
r = c({ action: 'login', username: 'boris_p', password: '=1+1' }); assert.ok(r.ok); // пароль-«формула» хранится как текст
r = c({ action: 'me', token: 'bad' }); assert.equal(r.error, 'auth');
r = c({ action: 'me', token: a2 }); assert.equal(r.user.fullName, 'Анна Иванова');

// Профиль
r = c({ action: 'updateProfile', token: b.token, username: 'anna.ivanova' }); assert.equal(r.error, 'username_taken');
r = c({ action: 'updateProfile', token: b.token, username: 'boris', fullName: 'Борис Петров-Водкин' }); assert.equal(r.user.username, 'boris');
r = c({ action: 'updateProfile', token: b.token, password: 'newpass', oldPassword: 'wrong' }); assert.equal(r.error, 'bad_password');
r = c({ action: 'updateProfile', token: b.token, password: 'newpass', oldPassword: '=1+1' }); assert.ok(r.ok);
r = c({ action: 'login', username: 'boris', password: 'newpass' }); assert.ok(r.ok);

// Облачная копия: длинные данные — несколько строк, перезапись не плодит строки
const big = JSON.stringify({ hw: [], file: 'x'.repeat(130000) + 'END' });
r = c({ action: 'backupLoad', token: a.token }); assert.equal(r.data, null);
r = c({ action: 'backupInfo', token: a.token }); assert.equal(r.updated, null);
r = c({ action: 'backupSave', token: a.token, data: big, group: '314' }); assert.ok(r.ok, JSON.stringify(r));
assert.equal(env.sheets.Backups.data.length - 1, 4);
r = c({ action: 'backupSave', token: a.token, data: big }); assert.equal(env.sheets.Backups.data.length - 1, 4);
r = c({ action: 'backupLoad', token: a2 }); assert.equal(r.data, big); // другое устройство того же профиля
r = c({ action: 'backupInfo', token: a2 }); assert.ok(r.updated);
r = c({ action: 'me', token: a2 }); assert.equal(r.user.group, '314');
r = c({ action: 'backupLoad', token: b.token }); assert.equal(r.data, null); // чужую копию не видно

// ---------- Группа ----------
const v = c({ action: 'register', username: 'vera', fullName: 'Вера Смирнова', password: 'pass' });
const g1 = c({ action: 'register', username: 'gleb', fullName: 'Глеб Орлов', password: 'pass' });
r = c({ action: 'sync', token: a.token }); assert.ok(r.ok); assert.equal(r.group, null); assert.deepEqual(r.absences, []);
r = c({ action: 'newsAdd', token: a.token, text: 'hi' }); assert.equal(r.error, 'no_group');
r = c({ action: 'groupCreate', token: a.token, name: '' }); assert.equal(r.error, 'bad_name');
r = c({ action: 'groupCreate', token: a.token, name: '313 группа' }); assert.ok(r.ok);
assert.equal(r.group.creatorId, a.user.id); assert.equal(r.members.length, 1); assert.ok(r.members[0].creator);
r = c({ action: 'groupCreate', token: a.token, name: 'ещё' }); assert.equal(r.error, 'already_in_group');
// добавление по юзернейму
r = c({ action: 'memberAdd', token: a.token, username: '@nobody' }); assert.equal(r.error, 'user_not_found');
r = c({ action: 'memberAdd', token: a.token, username: '@Boris' }); assert.ok(r.ok); assert.equal(r.members.length, 2);
r = c({ action: 'memberAdd', token: a.token, username: 'boris' }); assert.equal(r.error, 'already_member');
r = c({ action: 'memberAdd', token: b.token, username: 'vera' }); assert.equal(r.error, 'forbidden'); // обычный участник не может
// староста и роли
r = c({ action: 'setStarosta', token: b.token, userId: b.user.id }); assert.equal(r.error, 'forbidden');
r = c({ action: 'setStarosta', token: a.token, userId: b.user.id }); assert.ok(r.ok);
assert.ok(r.members.find((m) => m.id === b.user.id).starosta);
r = c({ action: 'memberAdd', token: b.token, username: 'vera' }); assert.ok(r.ok); // староста может добавлять
r = c({ action: 'memberRole', token: b.token, userId: v.user.id, role: 'Профорг' }); assert.ok(r.ok);
assert.equal(r.members.find((m) => m.id === v.user.id).role, 'Профорг');
assert.deepEqual(r.members.map((m) => m.username), ['anna.ivanova', 'boris', 'vera']); // создатель, староста, роль
r = c({ action: 'memberRole', token: v.token, userId: v.user.id, role: 'Король' }); assert.equal(r.error, 'forbidden');
// другая группа
r = c({ action: 'groupCreate', token: g1.token, name: '314' }); assert.ok(r.ok);
r = c({ action: 'memberAdd', token: g1.token, username: 'vera' }); assert.equal(r.error, 'in_other_group');
// объявления пишут все, чужая группа не видит
r = c({ action: 'newsAdd', token: v.token, text: '=Пара в 3-819' }); assert.ok(r.ok); assert.equal(r.news[0].text, '=Пара в 3-819');
assert.equal(r.news[0].author, 'Вера Смирнова');
r = c({ action: 'sync', token: g1.token }); assert.equal(r.news.length, 0);
const nid = c({ action: 'sync', token: a.token }).news[0].id;
r = c({ action: 'newsDelete', token: g1.token, id: nid }); assert.equal(r.error, 'not_found');
// старое объявление (22 дня) удаляется само
env.sheets.News.data.push(['old1', c({ action: 'sync', token: a.token }).group.id, a.user.id, 'старое', new Date(Date.now() - 22 * 86400000).toISOString()]);
r = c({ action: 'sync', token: a.token }); assert.equal(r.news.length, 1); assert.equal(env.sheets.News.data.length - 1, 1);
r = c({ action: 'newsDelete', token: b.token, id: nid }); assert.ok(r.ok); assert.equal(r.news.length, 0); // староста удаляет чужое
// выход, удаление участника, удаление группы
r = c({ action: 'groupLeave', token: a.token }); assert.equal(r.error, 'creator_cannot_leave');
r = c({ action: 'memberRemove', token: b.token, userId: a.user.id }); assert.equal(r.error, 'forbidden');
r = c({ action: 'memberRemove', token: a.token, userId: v.user.id }); assert.ok(r.ok); assert.equal(r.members.length, 2);
r = c({ action: 'groupLeave', token: b.token }); assert.ok(r.ok); assert.equal(r.group, null);
r = c({ action: 'sync', token: a.token }); assert.equal(r.group.starostaId, ''); assert.equal(r.members.length, 1);
r = c({ action: 'groupRename', token: a.token, name: '313 лечфак' }); assert.equal(r.group.name, '313 лечфак');
r = c({ action: 'groupDelete', token: g1.token }); assert.ok(r.ok);
r = c({ action: 'sync', token: g1.token }); assert.equal(r.group, null);

// ---------- Проверка для уведомлений ----------
r = c({ action: 'poll', token: g1.token }); assert.equal(r.group, null);
r = c({ action: 'memberAdd', token: a.token, username: 'vera' }); assert.ok(r.ok);
r = c({ action: 'poll', token: v.token, since: '' }); assert.equal(r.group.name, '313 лечфак'); assert.ok(r.added); assert.equal(r.news.length, 0);
const t0 = new Date(Date.now() - 1).toISOString();
c({ action: 'newsAdd', token: a.token, text: 'Новость для всех' });
c({ action: 'newsAdd', token: v.token, text: 'Своё не уведомляет' });
r = c({ action: 'poll', token: v.token, since: t0 }); assert.equal(r.news.length, 1); assert.equal(r.news[0].author, 'Анна Иванова');
r = c({ action: 'poll', token: v.token, since: r.news[0].created }); assert.equal(r.news.length, 0);

// ---------- Личные пропуски ----------
r = c({ action: 'absSave', token: b.token, date: '14.10', subject: 'Гигиена' }); assert.equal(r.error, 'bad_date');
r = c({ action: 'absSave', token: b.token, date: '2026-10-14', subject: '' }); assert.equal(r.error, 'bad_subject');
r = c({ action: 'absSave', token: b.token, date: '2026-10-14', subject: 'Гигиена', note: 'болел', makeupTime: '25' }); assert.equal(r.error, 'bad_time');
r = c({ action: 'absSave', token: b.token, date: '2026-10-14', subject: 'Гигиена', note: 'болел' }); assert.ok(r.ok);
c({ action: 'absSave', token: b.token, date: '2026-10-16', subject: 'Фармакология' });
const abs = r.absences[0];
assert.equal(abs.done, false); assert.equal(abs.makeupDate, '');
r = c({ action: 'absSave', token: b.token, id: abs.id, date: '2026-10-14', subject: 'Гигиена', note: 'болел', makeupDate: '2026-10-20', makeupTime: '14:20', makeupPlace: 'каф. гигиены, 3-512', done: true });
const upd = r.absences.find((x) => x.id === abs.id);
assert.equal(upd.makeupPlace, 'каф. гигиены, 3-512'); assert.equal(upd.makeupTime, '14:20'); assert.equal(upd.done, true);
assert.equal(r.absences[0].date, '2026-10-16'); // свежие сверху
r = c({ action: 'sync', token: a.token }); assert.equal(r.absences.length, 0); // чужие пропуски не видно
r = c({ action: 'absDelete', token: a.token, id: abs.id }); assert.equal(r.error, 'not_found');
r = c({ action: 'absDelete', token: b.token, id: abs.id }); assert.equal(r.absences.length, 1);

// Выход и удаление
r = c({ action: 'logout', token: a2 }); r = c({ action: 'me', token: a2 }); assert.equal(r.error, 'auth');
r = c({ action: 'deleteAccount', token: a.token, password: 'wrong' }); assert.equal(r.error, 'bad_password');
r = c({ action: 'deleteAccount', token: a.token, password: 'secret' }); assert.ok(r.ok);
assert.equal(env.sheets.Backups.data.length - 1, 0);
assert.equal(env.sheets.Groups.data.length - 1, 0); // группа создателя удалена вместе с ним
r = c({ action: 'deleteAccount', token: b.token, password: 'newpass' }); assert.ok(r.ok); assert.equal(env.sheets.Absences.data.length - 1, 0);
r = c({ action: 'login', username: 'anna.ivanova', password: 'secret' }); assert.equal(r.error, 'bad_login');
r = c({ action: 'nope' }); assert.equal(r.error, 'unknown_action');
console.log('server tests: OK');
