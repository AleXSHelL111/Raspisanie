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

// Выход и удаление
r = c({ action: 'logout', token: a2 }); r = c({ action: 'me', token: a2 }); assert.equal(r.error, 'auth');
r = c({ action: 'deleteAccount', token: a.token, password: 'wrong' }); assert.equal(r.error, 'bad_password');
r = c({ action: 'deleteAccount', token: a.token, password: 'secret' }); assert.ok(r.ok);
assert.equal(env.sheets.Backups.data.length - 1, 0);
r = c({ action: 'login', username: 'anna.ivanova', password: 'secret' }); assert.equal(r.error, 'bad_login');
r = c({ action: 'nope' }); assert.equal(r.error, 'unknown_action');
console.log('server tests: OK');
