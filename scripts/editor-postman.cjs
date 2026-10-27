// Build a standalone, ordered authorization suite using externally provisioned editors.
module.exports = function editorSuite(collection) {
  const folder = { name: '10 - Editor ownership suite', description: 'Run this folder alone with one ADMIN and two distinct EDITOR accounts. See the collection guide for provisioning and cleanup. No accounts are created by this suite.', item: [] };
  const event = (listen, exec) => ({ listen, script: { type: 'text/javascript', exec } });
  const variable = key => `{{${key}}}`;
  const orgs = '{{baseUrl}}/api/organizations';
  const org = orgs + '/{{editorOrgId}}';
  const groups = org + '/groups';
  const group = groups + '/{{editorGroupId}}';
  const notes = group + '/notes';
  const note = key => notes + '/' + variable(key);
  const input = { title: 'Editor ownership test', content: 'Original content', category: 'Postman' };
  const updated = { ...input, content: 'Updated content' };
  function add(name, method, url, status, actor, body, extra = [], pre = []) {
    const request = { method, url, header: [], auth: actor ? { type: 'bearer', bearer: [{ key: 'token', value: variable(actor + 'AccessToken'), type: 'string' }] } : { type: 'noauth' } };
    if (body !== undefined) {
      request.header.push({ key: 'Content-Type', value: 'application/json' });
      request.body = { mode: 'raw', raw: typeof body === 'string' ? body : JSON.stringify(body), options: { raw: { language: 'json' } } };
    }
    const checks = [
      `pm.test('HTTP ${status}', () => pm.response.to.have.status(${status}));`,
      // Stop before a failed setup or destructive negative case can cascade.
      `if (pm.response.code !== ${status}) { pm.execution.setNextRequest(null); throw new Error('Unexpected response; inspect editor fixture IDs before rerunning.'); }`,
      ...(status === 204 ? ["pm.test('Empty body', () => pm.expect(pm.response.text()).to.eql(''));"] : [
        `pm.test('Content type', () => pm.expect(pm.response.headers.get('Content-Type')).to.include('${status >= 400 ? 'application/problem+json' : 'application/json'}'));`,
        ...(status >= 400 ? [`pm.test('Problem details', () => { pm.expect(pm.response.json().status).to.eql(${status}); pm.expect(pm.response.json().title).to.be.a('string').and.not.empty; });`] : [])
      ]), ...extra
    ];
    const item = { name: `${String(folder.item.length + 1).padStart(2, '0')} - ${name} [10]`, request, event: [...(pre.length ? [event('prerequest', pre)] : []), event('test', checks)] };
    folder.item.push(item);
    return item;
  }
  const owner = actor => `pm.test('Owner matches authenticated subject', () => pm.expect(String(pm.response.json().ownerId)).to.eql(String(pm.collectionVariables.get('${actor}UserId'))));`;
  const fields = body => `pm.test('Note fields persisted', () => { const expected = ${JSON.stringify(body)}; for (const [key, value] of Object.entries(expected)) pm.expect(pm.response.json()[key]).to.eql(value); });`;
  function create(name, url, actor, body, key, extra = []) {
    return add(name, 'POST', url, 201, actor, body, [
      `pm.collectionVariables.set('${key}', pm.response.json().id);`,
      "pm.test('Positive ID and Location', () => { pm.expect(pm.response.json().id).to.be.a('number').and.above(0); pm.expect(pm.response.headers.get('Location')).to.eql(pm.request.url.getPath() + '/' + pm.response.json().id); });", ...extra
    ]);
  }
  for (const [actor, credentials, role] of [['editorAdmin', 'admin', 'ADMIN'], ['editorOne', 'editorOne', 'EDITOR'], ['editorTwo', 'editorTwo', 'EDITOR']]) {
    add(`Login ${role} ${actor}`, 'POST', '{{baseUrl}}/api/auth/login', 200, null, '{{editorCredentialsJson}}', [
      'const tokens = pm.response.json();',
      `pm.collectionVariables.set('${actor}AccessToken', tokens.accessToken);`,
      `pm.collectionVariables.set('${actor}RefreshToken', tokens.refreshToken);`,
      "const encoded = tokens.accessToken.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');",
      "const claims = JSON.parse(atob(encoded.padEnd(Math.ceil(encoded.length / 4) * 4, '=')));",
      `pm.test('Required ${role} role', () => pm.expect(claims.role).to.eql('${role}'));`,
      `if (claims.role !== '${role}') { pm.execution.setNextRequest(null); throw new Error('Account must have ${role} role.'); }`,
      `pm.collectionVariables.set('${actor}UserId', claims.sub);`,
      ...(actor === 'editorTwo' ? [
        "const distinct = claims.sub !== pm.collectionVariables.get('editorOneUserId');",
        "pm.test('Two distinct editors', () => pm.expect(distinct).to.eql(true));",
        "if (!distinct) { pm.execution.setNextRequest(null); throw new Error('Configure two different EDITOR accounts.'); }"
      ] : [])
    ], [
      ...(actor === 'editorAdmin' ? [
        "const required = ['adminUsername', 'adminPassword', 'editorOneUsername', 'editorOnePassword', 'editorTwoUsername', 'editorTwoPassword'];",
        "if (required.some(key => !pm.variables.get(key))) { pm.execution.setNextRequest(null); pm.execution.skipRequest(); throw new Error('Set admin and both editor credentials before running folder 10.'); }",
        "if (['editorOrgId', 'editorGroupId', 'editorNoteOneId', 'editorNoteTwoId', 'editorAdminNoteId'].some(key => pm.collectionVariables.get(key))) { pm.execution.setNextRequest(null); pm.execution.skipRequest(); throw new Error('Previous editor fixtures remain; clean up using saved editor IDs and clear them before rerunning.'); }"
      ] : []),
      `pm.variables.set('editorCredentialsJson', JSON.stringify({ username: pm.variables.get('${credentials}Username'), password: pm.variables.get('${credentials}Password') }));`
    ]);
  }
  create('Create isolated organization', orgs, 'editorAdmin', { name: 'Postman editor tests', description: 'Temporary authorization fixtures' }, 'editorOrgId');
  create('Create isolated group', groups, 'editorAdmin', { name: 'Editor tests', description: 'Temporary authorization fixtures' }, 'editorGroupId');
  create('Editor one creates owned note', notes, 'editorOne', input, 'editorNoteOneId', [owner('editorOne'), fields(input)]);
  create('Editor two creates owned note', notes, 'editorTwo', input, 'editorNoteTwoId', [owner('editorTwo'), fields(input)]);
  create('Admin creates owned note', notes, 'editorAdmin', input, 'editorAdminNoteId', [owner('editorAdmin')]);
  for (const url of [orgs, org, groups, group, notes]) add('Editor can read ' + url.replace('{{baseUrl}}', ''), 'GET', url, 200, 'editorOne');
  add('Editor can read another editor note', 'GET', note('editorNoteOneId'), 200, 'editorTwo', undefined, [owner('editorOne'), fields(input)]);
  for (const [url, itemUrl] of [[orgs, org], [groups, group]]) {
    for (const method of ['POST', 'PUT', 'DELETE']) add(`Editor forbidden ${method} ${url.replace('{{baseUrl}}', '')}`, method, method === 'POST' ? url : itemUrl, 403, 'editorOne', method === 'DELETE' ? undefined : { name: 'Forbidden edit', description: 'Must not persist' });
  }
  add('Editor updates own note', 'PUT', note('editorNoteOneId'), 200, 'editorOne', updated, [owner('editorOne'), fields(updated)]);
  for (const method of ['POST', 'PUT']) add('Reject ownerId injection on ' + method, method, method === 'POST' ? notes : note('editorNoteOneId'), 400, 'editorOne', { ...input, ownerId: 1 });
  for (const [actor, key] of [['editorTwo', 'editorNoteOneId'], ['editorOne', 'editorNoteTwoId'], ['editorOne', 'editorAdminNoteId']]) {
    for (const method of ['PUT', 'DELETE']) add(`${actor} cannot ${method} another owner note`, method, note(key), 403, actor, method === 'PUT' ? input : undefined);
  }
  add('Denied writes preserve note and owner', 'GET', note('editorNoteOneId'), 200, 'editorOne', undefined, [owner('editorOne'), fields(updated)]);
  add('Wrong parent cannot address owned note', 'PUT', notes.replace('{{editorGroupId}}', '9223372036854775807') + '/{{editorNoteOneId}}', 404, 'editorOne', input);
  add('Admin edits editor note without taking ownership', 'PUT', note('editorNoteOneId'), 200, 'editorAdmin', input, [owner('editorOne'), fields(input)]);
  add('Admin edit persisted with original owner', 'GET', note('editorNoteOneId'), 200, 'editorOne', undefined, [owner('editorOne'), fields(input)]);
  for (const [key, actor] of [['editorNoteTwoId', 'editorTwo'], ['editorNoteOneId', 'editorAdmin'], ['editorAdminNoteId', 'editorAdmin']]) {
    add(actor + ' deletes ' + key, 'DELETE', note(key), 204, actor);
    add('Verify deletion of ' + key, 'GET', note(key), 404, 'editorAdmin', undefined, [`pm.collectionVariables.unset('${key}');`]);
  }
  for (const [url, key] of [[group, 'editorGroupId'], [org, 'editorOrgId']]) {
    add('Clean up ' + key, 'DELETE', url, 204, 'editorAdmin');
    add('Verify deletion of ' + key, 'GET', url, 404, 'editorAdmin', undefined, [`pm.collectionVariables.unset('${key}');`]);
  }
  for (const actor of ['editorOne', 'editorTwo', 'editorAdmin']) add('Logout ' + actor, 'POST', '{{baseUrl}}/api/auth/logout', 204, null, { refreshToken: variable(actor + 'RefreshToken') }, [`['${actor}AccessToken', '${actor}RefreshToken', '${actor}UserId'].forEach(key => pm.collectionVariables.unset(key));`]);
  folder.item.at(-1).event.at(-1).script.exec.push('pm.execution.setNextRequest(null);');
  for (const key of ['editorOneUsername', 'editorOnePassword', 'editorTwoUsername', 'editorTwoPassword']) {
    if (!collection.variable.some(v => v.key === key)) collection.variable.push({ key, value: '', type: 'string' });
  }
  collection.item = collection.item.filter(f => f.name !== folder.name);
  collection.item.push(folder);
  return folder.item.length;
};
