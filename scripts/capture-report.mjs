// Run from the repository root while `npm run dev` runs in frontend/.
// Captures the real React UI using deterministic demonstration API responses.
import { chromium } from '../frontend/node_modules/playwright/index.mjs';
import { mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
const out = new URL('../docs/report/', import.meta.url);
await mkdir(out, { recursive: true });
const browser = await chromium.launch();
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 1 });
  const data = {
    organizations: [{ id: 1, name: 'Universitetas', description: 'Bendra studijų ir projektų erdvė.' }],
    groups: [{ id: 1, name: 'Saityno technologijos', description: 'Paskaitų medžiaga ir laboratoriniai darbai.', organizationId: 1 }],
    notes: [{ id: 1, title: 'HTTP metodai', content: 'GET skaito, POST sukuria, PUT pakeičia, DELETE pašalina.\n\nUžklausų duomenys perduodami JSON formatu.', category: 'REST', groupId: 1 }],
  };
  for (const [resource, items] of Object.entries(data)) {
    for (const item of items) {
      const collection = resource === 'organizations' ? '/api/organizations'
        : resource === 'groups' ? `/api/organizations/${item.organizationId}/groups`
          : `/api/organizations/${data.groups.find(group => group.id === item.groupId).organizationId}/groups/${item.groupId}/notes`;
      const self = `${collection}/${item.id}`;
      item._links = { self: { href: self }, collection: { href: collection },
        ...(resource === 'organizations' ? { groups: { href: `${self}/groups` } } : {}),
        ...(resource === 'groups' ? { notes: { href: `${self}/notes` } } : {}),
      };
    }
  }
  await page.route('**/api/**', async route => {
    const parts = new URL(route.request().url()).pathname.slice(5).split('/');
    const [resource, id] = parts.slice(parts.length >= 5 ? 4 : parts.length >= 3 ? 2 : 0);
    if (resource === 'auth') return route.fulfill({ json: {
      accessToken: `demo.${Buffer.from(JSON.stringify({role: 'ADMIN'})).toString('base64url')}.demo`, tokenType: 'Bearer', expiresIn: 180,
    }});
    if (route.request().method() !== 'GET') throw new Error('Report capture must not change data');
    await route.fulfill({ json: id ? data[resource].find(x => x.id === Number(id)) : data[resource] });
  });
  async function shot(name, dialog = false) {
    await page.evaluate(() => document.fonts.ready);
    await (dialog ? page.getByRole('dialog') : page.locator('body')).screenshot({ path: fileURLToPath(new URL(`${name}.png`, out)), animations: 'disabled' });
  }
  await page.goto('http://127.0.0.1:5173');
  await shot('login');
  await page.getByRole('button', {name: 'Registruokitės'}).click();
  await shot('register');
  await page.getByRole('button', {name: 'Prisijunkite', exact: true}).click();
  await page.getByLabel('Naudotojo vardas', {exact: true}).fill('demo.admin');
  await page.getByLabel('Slaptažodis', {exact: true}).fill('demo-password-123');
  await page.getByRole('button', {name: 'Prisijungti', exact: true}).click();
  await page.getByRole('button', {name: /HTTP metodai/}).waitFor();
  await shot('notes');
  for (const [section, singular, key] of [['Užrašai','užrašą','note'], ['Grupės','grupę','group'], ['Organizacijos','organizaciją','organization']]) {
    await page.getByRole('navigation').getByRole('button', {name: section, exact: true}).click();
    if (key !== 'note') await shot(`${key}s`);
    await page.getByRole('button', {name: `Sukurti ${singular}`, exact: true}).click();
    await shot(`${key}-create`, true);
    await page.getByRole('button', {name: 'Atšaukti', exact: true}).click();
  }
  await page.getByRole('navigation').getByRole('button', {name: 'Užrašai', exact: true}).click();
  await page.getByRole('button', {name: /HTTP metodai/}).click();
  await shot('detail', true);
  await page.getByRole('button', {name: 'Redaguoti', exact: true}).click();
  await shot('note-edit', true);
  await page.getByRole('button', {name: 'Atšaukti', exact: true}).click();
  await page.getByRole('button', {name: /HTTP metodai/}).click();
  await page.getByRole('button', {name: 'Ištrinti', exact: true}).click();
  await shot('delete', true);
  await page.getByRole('button', {name: 'Atšaukti', exact: true}).click();
  await page.getByRole('button', {name: 'Apie ir pagalba'}).click();
  await shot('help', true);
  await page.getByRole('button', {name: 'Supratau'}).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await shot('mobile');
  console.log('Captured 13 report screenshots with demonstration data.');
} finally { await browser.close(); }
