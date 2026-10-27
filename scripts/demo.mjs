// Node.js 18+; no npm installation required. Creates and removes only its own data.
import { performance } from 'node:perf_hooks';

const base = (process.env.API_URL || 'http://localhost:8080').replace(/\/$/, '');
let checks = 0;
let accessToken;
const created = [];
const started = performance.now();

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

async function request(method, path, status, body, raw = false) {
  const response = await fetch(base + path, {
    method,
    headers: { ...(body === undefined ? {} : { 'Content-Type': 'application/json' }), ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}) },
    body: body === undefined ? undefined : raw ? body : JSON.stringify(body),
    signal: AbortSignal.timeout(5000),
  });
  const text = await response.text();
  assert(response.status === status, `${method} ${path}: expected ${status}, got ${response.status}: ${text}`);
  if (status === 204) assert(text === '', '204 must have an empty body');
  else assert((response.headers.get('content-type') || '').includes(status >= 400 ? 'application/problem+json' : 'application/json'), 'Incorrect Content-Type');
  const json = text ? JSON.parse(text) : null;
  if (status >= 400) assert(json.status === status && json.detail, 'Invalid problem response');
  if (status === 201) {
    assert(Number.isInteger(json.id) && json.id > 0, 'Missing created ID');
    assert(response.headers.get('location') === `${path}/${json.id}`, 'Incorrect Location');
    created.push(`${path}/${json.id}`);
  }
  checks++;
  console.log(`OK ${status} ${method.padEnd(6)} ${path}`);
  return json;
}

try {
  assert(process.env.ADMIN_PASSWORD, 'Set ADMIN_PASSWORD to the configured administrator password.');
  accessToken = (await request('POST', '/api/auth/login', 200, { username: process.env.ADMIN_USERNAME || 'admin', password: process.env.ADMIN_PASSWORD })).accessToken;
  // Each run creates its own hierarchy, preserving existing demonstration data.
  const org = await request('POST', '/api/organizations', 201, { name: 'Demo organization', description: 'Automated demonstration data' });
  const groupPath = `/api/organizations/${org.id}/groups`;
  const group = await request('POST', groupPath, 201, { name: 'REST laboratory', description: 'Demo group' });
  const notePath = `${groupPath}/${group.id}/notes`;
  const note = await request('POST', notePath, 201, { title: 'HTTP status codes', content: '201 when created; 204 when deleted.', category: 'REST' });

  for (const [resource, item, body] of [
    ['organizations', org, { name: 'Updated organization', description: 'Updated description' }],
    [groupPath.slice(5), group, { name: 'Updated group', description: 'Updated description' }],
    [notePath.slice(5), note, { title: 'Updated note', content: 'PUT replaces a resource.', category: 'HTTP' }],
  ]) {
    const list = await request('GET', `/api/${resource}`, 200);
    assert(Array.isArray(list) && list.some(x => x.id === item.id), 'Created resource missing from list');
    const read = await request('GET', `/api/${resource}/${item.id}`, 200);
    assert(read.id === item.id, 'Incorrect resource ID');
    const updated = await request('PUT', `/api/${resource}/${item.id}`, 200, body);
    const persisted = await request('GET', `/api/${resource}/${item.id}`, 200);
    for (const [key, value] of Object.entries(body)) {
      assert(updated[key] === value && persisted[key] === value, `Update not persisted: ${key}`);
    }
  }

  const groups = await request('GET', groupPath, 200);
  assert(groups.length === 1 && groups[0].id === group.id, 'Organization filter failed');
  const notes = await request('GET', notePath, 200);
  assert(notes.length === 1 && notes[0].id === note.id, 'Group filter failed');
  await request('GET', `${notePath}/9223372036854775807`, 404);
  await request('POST', notePath, 400, '{broken json', true);
  await request('POST', notePath, 422, { title: '', content: '', category: '' });
  await request('DELETE', `/api/organizations/${org.id}`, 409);
  await request('DELETE', `${groupPath}/${group.id}`, 409);
  for (const path of [...created].reverse()) {
    await request('DELETE', path, 204);
    created.splice(created.indexOf(path), 1);
    await request('GET', path, 404);
  }
  const elapsed = (performance.now() - started) / 1000;
  console.log(`\nPASS: all 15 API methods; ${checks} HTTP checks; ${elapsed.toFixed(2)} s.`);
  if (elapsed > 15) {
    console.error('Demonstration exceeded the 15 s target. Warm up the server and run again.');
    process.exitCode = 1;
  }
} catch (error) {
  console.error(`FAIL: ${error.message}`);
  process.exitCode = 1;
} finally {
  for (const path of [...created].reverse()) {
    try {
      const response = await fetch(base + path, { method: 'DELETE', headers: { Authorization: `Bearer ${accessToken}` }, signal: AbortSignal.timeout(5000) });
      if (![204, 404].includes(response.status)) console.error(`Cleanup failed: ${path}: ${response.status}`);
    } catch (error) { console.error(`Cleanup failed: ${path}: ${error.message}`); }
  }
}
