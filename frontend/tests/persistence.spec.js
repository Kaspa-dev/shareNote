import { test, expect } from "@playwright/test";
const key = "sharenote-session";
const session = () => ({ sessionId: "session-one", token: "old", username: "reader", userId: "1", role: "USER", expiresAt: Date.now() + 180000, refreshToken: "refresh-old", refreshExpiresAt: Date.now() + 86400000 });
const rotated = () => ({ accessToken: `header.${Buffer.from(JSON.stringify({ role: "USER", sub: "1" })).toString("base64url")}.new`, expiresIn: 180, refreshToken: "refresh-new", refreshExpiresAt: Date.now() + 86400000 });
async function setup(context, page) {
  await context.route("**/api/**", route => route.fulfill({ json: [] }));
  await page.goto("/");
  await page.evaluate(async value => (await import("/src/services/api.js")).saveSession(value), session());
  const other = await context.newPage();
  await other.goto("/");
  return other;
}
test("shared login survives closing all tabs and logout reaches every tab", async ({ context, page }) => {
  let other = await setup(context, page);
  await page.close();
  await other.close();
  page = await context.newPage();
  await page.goto("/");
  await expect(page.getByRole("heading", { name: /^Užrašai/, level: 1 })).toBeVisible();
  other = await context.newPage();
  await other.goto("/");
  await page.getByRole("button", { name: /Atsijungti/ }).last().click();
  await expect(other.getByRole("heading", { name: "Jūsų užrašai laukia" })).toBeVisible();
  expect(await other.evaluate(key => localStorage.getItem(key), key)).toBeNull();
});
test("two tabs consume a refresh token only once", async ({ context, page }) => {
  const other = await setup(context, page);
  let count = 0;
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  await context.route("**/api/auth/refresh", async route => { count++; await gate; await route.fulfill({ json: rotated() }); });
  const first = page.evaluate(async () => (await import("/src/services/api.js")).renewSession());
  await expect.poll(() => count).toBe(1);
  const second = other.evaluate(async () => (await import("/src/services/api.js")).renewSession());
  await expect.poll(() => other.evaluate(async () => (await navigator.locks.query()).pending.length)).toBe(1);
  release();
  await Promise.all([first, second]);
  expect(count).toBe(1);
  expect(await other.evaluate(key => JSON.parse(localStorage.getItem(key)).refreshToken, key)).toBe("refresh-new");
});
for (const switchAccount of [false, true]) {
  test(`pending refresh cannot undo ${switchAccount ? "account switch" : "logout"}`, async ({ context, page }) => {
    const other = await setup(context, page);
    let release;
    const gate = new Promise(resolve => { release = resolve; });
    const revoked = [];
    await context.route("**/api/auth/logout", async route => { revoked.push(route.request().postDataJSON().refreshToken); await route.fulfill({ status: 204 }); });
    await context.route("**/api/auth/refresh", async route => { await gate; await route.fulfill({ json: rotated() }); });
    const started = page.waitForRequest("**/api/auth/refresh");
    const pending = page.evaluate(async () => {
      try { await (await import("/src/services/api.js")).renewSession(); } catch (error) { return error.name; }
    });
    await started;
    if (switchAccount) await other.evaluate(async value => (await import("/src/services/api.js")).saveSession(value), { ...session(), sessionId: "new-account", userId: "2" });
    else await other.evaluate(async () => (await import("/src/services/api.js")).endSession());
    release();
    expect(await pending).toBe("AbortError");
    expect(revoked).toContain("refresh-new");
    expect(await page.evaluate(key => JSON.parse(localStorage.getItem(key))?.sessionId ?? null, key)).toBe(switchAccount ? "new-account" : null);
  });
}
test("malformed and legacy sessions require login", async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem("sharenote-session", "invalid json");
    sessionStorage.setItem("sharenote-session", "legacy credentials");
  });
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Jūsų užrašai laukia" })).toBeVisible();
  expect(await page.evaluate(() => [localStorage.getItem("sharenote-session"), sessionStorage.getItem("sharenote-session")])).toEqual([null, null]);
});

test("session survives a full browser restart", async ({ playwright }, testInfo) => {
  const profile = testInfo.outputPath("browser-profile");
  const options = { headless: true, channel: process.env.PLAYWRIGHT_CHANNEL || undefined };
  let browser = await playwright.chromium.launchPersistentContext(profile, options);
  try {
    let page = await browser.newPage();
    await browser.route("**/api/**", route => route.fulfill({ json: [] }));
    await page.goto("http://127.0.0.1:5173");
    await page.evaluate(async value => (await import("/src/services/api.js")).saveSession(value), session());
    await browser.close();
    browser = await playwright.chromium.launchPersistentContext(profile, options);
    await browser.route("**/api/**", route => route.fulfill({ json: [] }));
    page = await browser.newPage();
    await page.goto("http://127.0.0.1:5173");
    await expect(page.getByRole("heading", { name: /^Užrašai/, level: 1 })).toBeVisible();
  } finally { await browser.close(); }
});

test("unavailable storage reports a useful login error", async ({ page }) => {
  await page.goto("/");
  const message = await page.evaluate(async value => {
    const api = await import("/src/services/api.js");
    const original = Storage.prototype.setItem;
    Storage.prototype.setItem = () => { throw new DOMException("Blocked", "SecurityError"); };
    try { api.saveSession(value); } catch (error) { return error.message; }
    finally { Storage.prototype.setItem = original; }
  }, session());
  expect(message).toContain("saugyklą");
});

test("expired sessions are removed and unsupported locks require login", async ({ page }) => {
  await page.goto("/");
  const result = await page.evaluate(async value => {
    const api = await import("/src/services/api.js");
    api.saveSession({ ...value, expiresAt: 1, refreshExpiresAt: 1 });
    const expired = api.readSession();
    api.saveSession(value);
    Object.defineProperty(navigator, "locks", { value: undefined, configurable: true });
    try { await api.renewSession(); } catch (error) { return { expired, status: error.status }; }
  }, session());
  expect(result).toEqual({ expired: null, status: 401 });
});
