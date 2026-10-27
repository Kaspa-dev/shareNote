// Rebuild the standalone response-code suite from the existing demonstration.
const fs = require('node:fs');
const path = require('node:path');
const root = path.join(__dirname, '..');
const file = path.join(root, 'docs/postman/ShareNote.postman_collection.json');
const c = JSON.parse(fs.readFileSync(file, 'utf8'));
const spec = JSON.parse(fs.readFileSync(path.join(root, 'docs/openapi.json'), 'utf8'));
const copy = value => structuredClone(value);
const event = (listen, exec) => ({ listen, script: { type: 'text/javascript', exec } });
const suite = { name: '09 - Full response-code suite', description: 'Run this folder alone. Requires admin credentials. Creates one persistent reader account per run for registration and authorization checks; removes only its demo library records. See response-code-coverage.md for documented codes that cannot be reproduced through the API.', item: [] };
const coverage = new Map();
const endpointKeys = new WeakMap();
const normalize = url => url.replace('{{baseUrl}}', '').replace(/\{\{[^}]+\}\}|\{[^}]+\}/g, '{}');
function record(method, url, code) {
  const key = method + ' ' + normalize(url);
  if (!coverage.has(key)) coverage.set(key, new Set());
  coverage.get(key).add(String(code));
}
function tests(code) {
  const lines = [`pm.test('HTTP ${code}', () => pm.response.to.have.status(${code}));`];
  if (code === 204) lines.push("pm.test('Empty response', () => pm.expect(pm.response.text()).to.eql(''));");
  else {
    lines.push(`pm.test('Content type', () => pm.expect(pm.response.headers.get('Content-Type')).to.include('${code >= 400 ? 'application/problem+json' : 'application/json'}'));`);
    if (code >= 400) lines.push(`pm.test('Problem status', () => pm.expect(pm.response.json().status).to.eql(${code}));`, "pm.test('Problem title', () => pm.expect(pm.response.json().title).to.be.a('string').and.not.empty);");
  }
  return lines;
}
function add(name, method, url, code, body, auth, extra = [], pre = [], contentType = 'application/json', canonical = url) {
  const request = { method, url, header: [], description: `Expected HTTP ${code}. ${name}.` };
  if (auth) request.auth = auth;
  if (body !== undefined) {
    request.header.push({ key: 'Content-Type', value: contentType });
    request.body = { mode: 'raw', raw: typeof body === 'string' ? body : JSON.stringify(body), options: { raw: { language: 'json' } } };
  }
  const item = { name: `${code} - ${name}`, request, event: [event('test', [...tests(code), ...extra])] };
  if (pre.length) item.event.unshift(event('prerequest', pre));
  suite.item.push(item);
  endpointKeys.set(item, method + ' ' + normalize(canonical));
  record(method, canonical, code);
  return item;
}
function existing(source, code, label) {
  const item = copy(source);
  item.name = `${code} - ${label || item.name}`;
  suite.item.push(item);
  endpointKeys.set(item, item.request.method + ' ' + normalize(item.request.url));
  record(item.request.method, item.request.url, code);
  return item;
}
const noauth = { type: 'noauth' };
const reader = { type: 'bearer', bearer: [{ key: 'token', value: '{{readerAccessToken}}', type: 'string' }] };
const authUrl = endpoint => '{{baseUrl}}/api/auth/' + endpoint;
existing(c.item[0].item[0], 200);
const refresh = existing(c.item[0].item[1], 200);
refresh.event.unshift(event('prerequest', ["pm.collectionVariables.set('consumedRefreshToken', pm.collectionVariables.get('refreshToken'));"]));
const registrationFolder = c.item.find(f => f.name === '08 - Optional reader registration');
const registration = existing(registrationFolder.item[0], 201);
registration.event.find(e => e.listen === 'prerequest').script.exec = [
  "['readerAccessToken', 'readerRefreshToken'].forEach(key => pm.collectionVariables.unset(key));",
  "const username = 'postman_' + pm.variables.replaceIn('{{$guid}}');",
  "const password = pm.variables.replaceIn('{{$guid}}');",
  "pm.collectionVariables.set('suiteReaderUsername', username);",
  "pm.variables.set('suiteReaderCredentials', JSON.stringify({username, password}));",
  "pm.variables.set('credentialsJson', pm.variables.get('suiteReaderCredentials'));"
];
add('Duplicate registration', 'POST', authUrl('register'), 409, '{{suiteReaderCredentials}}', noauth);
add('Unknown login credentials', 'POST', authUrl('login'), 401,
  {username: '{{suiteReaderUsername}}', password: 'intentionally-wrong-password'}, noauth);
add('Consumed refresh token', 'POST', authUrl('refresh'), 401, {refreshToken: '{{consumedRefreshToken}}'}, noauth);
for (const endpoint of ['register', 'login', 'refresh', 'logout']) {
  add(`${endpoint}: malformed JSON`, 'POST', authUrl(endpoint), 400, '{broken', noauth);
  add(`${endpoint}: unknown field`, 'POST', authUrl(endpoint), 400, {unexpected: true}, noauth);
  add(`${endpoint}: invalid fields`, 'POST', authUrl(endpoint), 422,
    ['register', 'login'].includes(endpoint) ? {username: '', password: ''} : {refreshToken: ''}, noauth);
  add(`${endpoint}: unsupported media type`, 'POST', authUrl(endpoint), 415, '{}', noauth, [], [], 'text/plain');
}
for (const r of c.item[1].item) existing(r, 201);
const CRUD = c.item.slice(1, 5).flatMap(f => f.item).concat(c.item[6].item.filter(r => r.request.method === 'DELETE'));
for (const source of CRUD) {
  const { method, url } = source.request;
  const label = method + ' ' + url.replace('{{baseUrl}}', '');
  const body = source.request.body?.raw;
  add(`${label}: missing token`, method, url, 401, body, noauth);
  add(`${label}: invalid token`, method, url, 401, body, {type:'bearer',bearer:[{key:'token',value:'invalid-token',type:'string'}]});
  if (method !== 'GET') add(`${label}: reader forbidden`, method, url, 403, body, reader);
  else add(`${label}: reader permitted`, method, url, 200, undefined, reader);
  const placeholders = [...url.matchAll(/\{\{(orgId|groupId|noteId)\}\}/g)];
  if (placeholders.length) {
    const last = placeholders.at(-1)[0];
    add(`${label}: nonnumeric ID`, method, url.replace(last, 'not-a-number'), 400, body, undefined, [], [], 'application/json', url);
    add(`${label}: missing resource`, method, url.replace(last, '9223372036854775807'), 404, body, undefined, [], [], 'application/json', url);
    if (placeholders.length > 1) add(`${label}: missing parent`, method, url.replace(placeholders[0][0], '9223372036854775807'), 404, body, undefined, [], [], 'application/json', url);
  }
  if (method === 'POST' || method === 'PUT') {
    add(`${label}: malformed JSON`, method, url, 400, '{broken');
    add(`${label}: unknown field`, method, url, 400, {...JSON.parse(body), unexpected:true});
    add(`${label}: invalid fields`, method, url, 422,
      url.includes('/notes') ? {title:'',content:'',category:''} : {name:'',description:''});
    add(`${label}: unsupported media type`, method, url, 415, body, undefined, [], [], 'text/plain');
  }
  if (method === 'DELETE' && !url.includes('/notes')) add(`${label}: dependent children`, method, url, 409);
}
for (const f of c.item.slice(2, 5)) for (const r of f.item) existing(r, 200);
for (const r of c.item[6].item) existing(r, r.request.method === 'DELETE' ? 204 : 404);
const readerLogout = existing(registrationFolder.item[2], 204);
readerLogout.event.unshift(event('prerequest', ["pm.collectionVariables.set('revokedReaderRefreshToken', pm.collectionVariables.get('readerRefreshToken'));"]));
add('Logout is idempotent', 'POST', authUrl('logout'), 204, {refreshToken:'{{revokedReaderRefreshToken}}'}, noauth);
add('Revoked refresh token', 'POST', authUrl('refresh'), 401, {refreshToken:'{{revokedReaderRefreshToken}}'}, noauth);
existing(c.item[7].item[0], 204, 'Administrator logout and finish');
// Keep endpoint folders together visually, while the runner follows the proven
// dependency order through unique request names, which survive desktop imports.
// Use one post-response script: desktop editors may not preserve duplicate test
// events. Route before assertions so setup failures can still stop the run.
const executionOrder = suite.item;
const requestCount = executionOrder.length;
for (const [index, item] of executionOrder.entries()) {
  delete item.id;
  item.name += ` [09-${String(index + 1).padStart(3, '0')}]`;
}
for (const [index, item] of executionOrder.entries()) {
  const nextName = executionOrder[index + 1]?.name || null;
  const assertions = item.event.filter(e => e.listen === 'test').flatMap(e => e.script.exec);
  item.event = item.event.filter(e => e.listen !== 'test');
  item.event.push(event('test', [`pm.execution.setNextRequest(${JSON.stringify(nextName)});`, ...assertions]));
}
const groups = new Map();
for (const [url, methods] of Object.entries(spec.paths)) {
  for (const verb of Object.keys(methods)) {
    const method = verb.toUpperCase();
    groups.set(method + ' ' + normalize(url), {
      name: `${method} ${url}`,
      description: 'All response-code cases for this endpoint. Run the enclosing 09 folder with every request selected; scripts route between endpoints to satisfy setup, token, and cleanup dependencies. Sending a case manually requires its fixtures and tokens to exist.',
      item: []
    });
  }
}
for (const item of executionOrder) groups.get(endpointKeys.get(item)).item.push(item);
suite.item = [...groups.values()].sort((a,b) => {
  const [am, ap] = a.name.split(' '), [bm, bp] = b.name.split(' ');
  return ap.localeCompare(bp) || ['GET','POST','PUT','DELETE'].indexOf(am) - ['GET','POST','PUT','DELETE'].indexOf(bm);
});
for (const folder of suite.item) folder.item.sort((a,b) => parseInt(a.name) - parseInt(b.name));
if (suite.item[0].item[0] !== executionOrder[0]) throw new Error('The sorted suite must start with administrator login.');
c.item = c.item.filter(f => f.name !== suite.name);
c.item.push(suite);
const editorRequestCount = require('./editor-postman.cjs')(c);
const excluded = (method, url, code) => {
  if (code === '403' && method === 'GET') return 'All issued roles (USER, EDITOR and ADMIN) can read. A valid JWT with another role cannot be obtained through this API.';
  if (url === '/api/organizations' && code === '404') return 'No path resource is looked up; listing returns an array and creation generates an ID.';
  if (url === '/api/organizations' && method === 'GET' && code === '400') return 'No typed input or path parameters to invalidate.';
  if (code === '409' && method === 'DELETE' && url.includes('/notes/')) return 'Notes have no dependent records in the schema.';
  if (code === '409' && ['POST','PUT'].includes(method)) return 'Valid payloads and checked, fixed parents do not expose a deterministic relationship conflict. Generic database/concurrency failures are not induced by this suite.';
  throw new Error(`Unaccounted documented response: ${method} ${url} ${code}`);
};
const rows = [], gaps = []; let pairs = 0;
for (const [url, methods] of Object.entries(spec.paths)) for (const [verb, op] of Object.entries(methods)) {
  const method = verb.toUpperCase();
  const tested = coverage.get(method + ' ' + normalize(url)) || new Set();
  const missing = Object.keys(op.responses).filter(code => !tested.has(code));
  for (const code of missing) gaps.push(`| ${method} \`${url}\` | ${code} | ${excluded(method,url,code)} |`);
  pairs += tested.size;
  rows.push(`| ${method} \`${url}\` | ${[...tested].sort().join(', ')} | ${missing.join(', ') || 'None'} |`);
}
const matrix = `# Response-code coverage\n\nRun **09 - Full response-code suite** alone. It contains ${requestCount} requests and checks ${pairs} distinct endpoint/status pairs across all 19 operations. The requests are grouped into 19 endpoint folders, sorted by URL and HTTP method, with response codes sorted inside each folder. Run the whole 09 folder: unique-request-name routing preserves fixture and cleanup dependencies while keeping all endpoint cases together in the sidebar. Do not run a single endpoint folder as a standalone suite. Each negative case checks the exact status, problem content type, and ProblemDetail status/title. Success requests check status and resource/token data; cleanup checks deletion.\n\nOne persistent reader account is registered per run (name saved as \`suiteReaderUsername\`) to cover registration 201/409 and USER authorization. Its password is generated at runtime. Library test records are deleted in child-first order; both sessions are logged out. Failed setup stops the run and may leave data for manual cleanup. Reuse folder 06 to clean captured library IDs before starting another run.\n\n## Endpoint matrix\n\n| Endpoint | Tested codes | Documented but not reproducible |\n| --- | --- | --- |\n${rows.join('\n')}\n\n## Documentation limitations\n\nThese are inherited annotations rather than reproducible public-API cases. They are explicitly excluded, not silently counted as passing. No server code or OpenAPI responses were changed.\n\n| Endpoint | Code | Reason |\n| --- | --- | --- |\n${gaps.join('\n')}\n\nAuth 415 is also tested even though it is omitted from the exported specification. General protocol errors such as 405/406 and unexpected 5xx outages are outside the documented operation response contract; the suite does not deliberately crash or corrupt the server.\n`;
fs.writeFileSync(path.join(root, 'docs/postman/response-code-coverage.md'), matrix);
const guide = fs.readFileSync(path.join(root, 'docs/postman/README.md'), 'utf8');
c.info.description = guide + '\n\n' + matrix;
fs.writeFileSync(file, JSON.stringify(c, null, 2) + '\n');
console.log(`Generated ${requestCount} suite requests; ${pairs} endpoint/status pairs; ${gaps.length} explicit documentation gaps.`);
console.log(`Generated ${editorRequestCount} editor ownership requests in folder 10.`);
