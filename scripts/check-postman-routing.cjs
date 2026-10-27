// Check routing after imports assign fresh IDs and keep one script per event.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { randomUUID } = require('node:crypto');
const c = JSON.parse(fs.readFileSync(path.join(__dirname, '../docs/postman/ShareNote.postman_collection.json'), 'utf8'));
const suite = c.item.find(f => f.name === '09 - Full response-code suite');
const requests = suite.item.flatMap(f => f.item);
assert.equal(suite.item.length, 19);
assert.equal(requests.length, 149);
const byName = new Map();
for (const request of requests) {
  request.id = randomUUID();
  assert(!byName.has(request.name), 'Request names must be unique');
  byName.set(request.name, request);
  assert.equal(request.event.filter(e => e.listen === 'test').length, 1);
  for (const event of request.event) new vm.Script(event.script.exec.join('\n'));
}
const visited = new Set();
let current = requests[0];
while (current) {
  assert(!visited.has(current.name), 'Routing cycle');
  visited.add(current.name);
  let next;
  const script = current.event.find(e => e.listen === 'test').script.exec;
  vm.runInNewContext(script[0], { pm: { execution: { setNextRequest: name => { next = name; } } } });
  if (next === null) break;
  assert(byName.has(next), 'Missing route target: ' + next);
  current = byName.get(next);
}
assert.equal(visited.size, requests.length);
// A failing login must override its default route and stop fixture creation.
let next;
vm.runInNewContext(requests[0].event.find(e => e.listen === 'test').script.exec.join('\n'), {
  pm: {
    execution: { setNextRequest: name => { next = name; } },
    test: () => {}, response: { code: 401 },
    collectionVariables: { unset: () => {} }
  }
});
assert.equal(next, null);
console.log('PASS: 149 requests remain reachable after ID reassignment; one post-response script per request; failed login stops the run.');

const editors = c.item.find(f => f.name === '10 - Editor ownership suite').item;
assert.equal(editors.length, 46);
assert.equal(new Set(editors.map(r => r.name)).size, editors.length);
for (const request of editors) {
  assert.equal(request.event.filter(e => e.listen === 'test').length, 1);
  for (const event of request.event) new vm.Script(event.script.exec.join('\n'));
  let stopped = false;
  assert.throws(() => vm.runInNewContext(request.event.find(e => e.listen === 'test').script.exec.join('\n'), {
    pm: { test: () => {}, response: { code: 500 }, execution: { setNextRequest: value => { stopped = value === null; } } }
  }), /Unexpected response/);
  assert(stopped, 'Unexpected responses must stop editor fixture operations');
}
function runEditorLogin(index, role, subject, previousSubject) {
  const values = new Map([['editorOneUserId', previousSubject]]);
  let next = 'continue';
  const tokens = { accessToken: 'header.' + Buffer.from(JSON.stringify({ role, sub: subject })).toString('base64url') + '.signature', refreshToken: 'test-refresh' };
  const context = { atob: value => Buffer.from(value, 'base64').toString('utf8'), pm: {
    test: () => {}, response: { code: 200, json: () => tokens },
    execution: { setNextRequest: value => { next = value; } },
    collectionVariables: { set: (key, value) => values.set(key, value), get: key => values.get(key) }
  } };
  let error;
  try { vm.runInNewContext(editors[index].event.find(e => e.listen === 'test').script.exec.join('\n'), context); } catch (e) { error = e; }
  return { next, error, values };
}
assert.match(runEditorLogin(1, 'USER', '2').error.message, /EDITOR role/);
assert.equal(runEditorLogin(1, 'USER', '2').next, null);
assert.match(runEditorLogin(2, 'EDITOR', '2', '2').error.message, /two different/);
const valid = runEditorLogin(2, 'EDITOR', '3', '2');
assert.equal(valid.error, undefined);
assert.equal(valid.next, 'continue');
assert.equal(valid.values.get('editorTwoUserId'), '3');
console.log('PASS: 46 editor request scripts compile; unexpected responses, wrong roles, and identical editor accounts stop the suite.');
