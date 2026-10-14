// Проверка логики сервера: node server/test/api.test.cjs
const assert = require('assert');
const { makeEnv } = require('./emulator.cjs');
const env = makeEnv(), c = env.call;
let r;

assert.deepStrictEqual(env.get().app, 'kgmu');
r = c({ action: 'register', group: '313', name: 'Анна Иванова', password: '12' }); assert.equal(r.error, 'password_short');
const a = c({ action: 'register', group: '313', name: 'Анна Иванова', password: 'secret', avatar: '🦊|#00E5FF', contact: '+79990001122' });
assert.ok(a.ok && a.token); assert.equal(a.user.role, 'starosta'); assert.equal(a.user.contact, '+79990001122');
r = c({ action: 'register', group: '313', name: 'анна  иванова', password: 'xxxx' }); assert.equal(r.error, 'name_taken');
const b = c({ action: 'register', group: '313', name: 'Борис', password: '=1+1', contact: '@boris' });
assert.equal(b.user.role, 'member');
const z = c({ action: 'register', group: '401', name: 'Зоя', password: 'pass' });
assert.equal(z.user.role, 'starosta');

r = c({ action: 'login', group: '313', name: 'борис', password: 'nope' }); assert.equal(r.error, 'bad_login');
r = c({ action: 'login', group: '313', name: 'борис', password: '=1+1' }); assert.ok(r.ok); const b2 = r.token;
r = c({ action: 'me', token: 'bad' }); assert.equal(r.error, 'auth');
r = c({ action: 'me', token: b2 }); assert.equal(r.user.name, 'Борис');

// Профиль
r = c({ action: 'updateProfile', token: b.token, name: 'Анна Иванова' }); assert.equal(r.error, 'name_taken');
r = c({ action: 'updateProfile', token: b.token, name: 'Борис П.', avatar: '🐼|#FF2E97' }); assert.equal(r.user.name, 'Борис П.');

// Облачная копия, включая длинные данные (несколько частей)
const big = JSON.stringify({ hw: [], file: 'x'.repeat(130000) + 'END' });
r = c({ action: 'backupLoad', token: b.token }); assert.equal(r.data, null);
r = c({ action: 'backupSave', token: b.token, data: big }); assert.ok(r.ok, JSON.stringify(r));
assert.equal(env.sheets.Backups.data.length - 1, 4);
r = c({ action: 'backupSave', token: b.token, data: big }); // перезапись не плодит строки
assert.equal(env.sheets.Backups.data.length - 1, 4);
r = c({ action: 'backupLoad', token: b2 }); assert.equal(r.data, big);

// ДЗ группы
r = c({ action: 'hwAdd', token: b.token, subject: 'Фармакология', date: '2026-10-20', start: 480, text: '-тема 5' }); assert.ok(r.ok);
const hwId = r.id;
r = c({ action: 'hwAdd', token: b.token, subject: 'X', date: '20.10', text: 't' }); assert.equal(r.error, 'date');
r = c({ action: 'group', token: z.token }); assert.equal(r.homework.length, 0); // другая группа не видит
r = c({ action: 'group', token: a.token });
assert.equal(r.homework[0].text, '-тема 5'); assert.equal(r.homework[0].author, 'Борис П.'); assert.equal(r.homework[0].start, 480);
assert.equal(r.members.length, 2); assert.equal(r.members[0].role, 'starosta');

// Объявления: только староста/зам
r = c({ action: 'newsAdd', token: b.token, text: 'hi' }); assert.equal(r.error, 'forbidden');
r = c({ action: 'newsAdd', token: a.token, kind: 'move', title: 'Перенос', text: 'Пара в 10:00' }); assert.ok(r.ok);
r = c({ action: 'setRole', token: b.token, userId: a.user.id, role: 'member' }); assert.equal(r.error, 'forbidden');
r = c({ action: 'setRole', token: a.token, userId: b.user.id, role: 'deputy' }); assert.ok(r.ok);
r = c({ action: 'newsAdd', token: b.token, text: 'от зама' }); assert.ok(r.ok);
r = c({ action: 'hwDelete', token: a.token, id: hwId }); assert.ok(r.ok); // староста может удалить чужое

// Посещаемость
r = c({ action: 'attAdd', token: b.token, date: '2026-10-13', subject: 'Гигиена', status: 'absent' }); const attId = r.id;
r = c({ action: 'attAdd', token: a.token, date: '2026-10-12', subject: 'Гигиена', status: 'excused' });
r = c({ action: 'attUpdate', token: b.token, id: attId, status: 'done' }); assert.ok(r.ok);
r = c({ action: 'setRole', token: a.token, userId: b.user.id, role: 'member' });
r = c({ action: 'group', token: b.token }); assert.equal(r.attendance.length, 1); assert.equal(r.attendance[0].status, 'done');
r = c({ action: 'group', token: a.token }); assert.equal(r.attendance.length, 2); // староста видит всех
assert.equal(r.news.length, 2);

// Передача роли старосты
r = c({ action: 'setRole', token: a.token, userId: b.user.id, role: 'starosta' }); assert.ok(r.ok);
r = c({ action: 'group', token: a.token }); assert.equal(r.user.role, 'deputy');

// Выход и удаление
r = c({ action: 'logout', token: b2 }); r = c({ action: 'me', token: b2 }); assert.equal(r.error, 'auth');
r = c({ action: 'deleteAccount', token: b.token }); assert.ok(r.ok);
r = c({ action: 'group', token: a.token }); assert.equal(r.user.role, 'starosta'); assert.equal(r.members.length, 1); assert.equal(r.attendance.length, 1);
assert.equal(env.sheets.Backups.data.length - 1, 0);
r = c({ action: 'nope' }); assert.equal(r.error, 'unknown_action');
console.log('server tests: OK');
