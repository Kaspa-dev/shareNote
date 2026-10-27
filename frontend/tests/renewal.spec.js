import { test, expect } from "@playwright/test";

for (const temporaryFailure of [false, true]) {
  test(`scheduled renewal keeps the user signed in${temporaryFailure ? " after a temporary failure" : ""}`, async ({ page }) => {
    await page.clock.install();
    let refreshes = 0;
    const token = `header.${Buffer.from(JSON.stringify({ role: "USER" })).toString("base64url")}.renewed`;
    await page.addInitScript(() => localStorage.setItem("sharenote-session", JSON.stringify({
      sessionId: "test-session", token: "initial-access", username: "reader", role: "USER", expiresAt: Date.now() + 180000,
      refreshToken: "initial-refresh", refreshExpiresAt: Date.now() + 2 * 86400000,
    })));
    await page.route("**/api/**", async (route) => {
      if (new URL(route.request().url()).pathname === "/api/auth/refresh") {
        refreshes++;
        expect(route.request().headers().authorization).toBeUndefined();
        expect(route.request().postDataJSON()).toEqual({ refreshToken: "initial-refresh" });
        if (temporaryFailure && refreshes === 1)
          return route.fulfill({ status: 503, json: {} });
        return route.fulfill({ json: { accessToken: token, expiresIn: 180,
          refreshToken: "rotated-refresh", refreshExpiresAt: Date.now() + 1 * 86400000 } });
      }
      return route.fulfill({ json: [] });
    });
    await page.goto("/");
    await expect(page.getByRole("heading", { name: /^Užrašai/, level: 1 })).toBeVisible();
    expect(refreshes).toBe(0);
    const renewalFinished = page.waitForEvent("requestfinished", {
      predicate: (request) => request.url().endsWith("/auth/refresh"),
    });
    await page.clock.fastForward(2 * 60000);
    await renewalFinished;
    await expect.poll(() => refreshes).toBe(1);
    if (temporaryFailure) {
      await expect(page.getByRole("heading", { name: /^Užrašai/, level: 1 })).toBeVisible();
      await page.clock.runFor(1000);
      await page.clock.fastForward(30000);
    }
    await expect.poll(() => page.evaluate(() => JSON.parse(localStorage.getItem("sharenote-session"))?.token)).toBe(token);
    expect(refreshes).toBe(temporaryFailure ? 2 : 1);
    await expect(page.getByRole("heading", { name: /^Užrašai/, level: 1 })).toBeVisible();
  });
}

for (const rejected of [false, true]) {
  test(`401 renewal ${rejected ? "rejects invalid refresh tokens" : "retries with the new token"}`, async ({ page }) => {
    let refreshes = 0;
    const token = `header.${Buffer.from(JSON.stringify({ role: "USER" })).toString("base64url")}.new`;
    await page.addInitScript(() => localStorage.setItem("sharenote-session", JSON.stringify({
      sessionId: "test-session", token: "rejected-access", username: "reader", role: "USER", expiresAt: Date.now() + 3600000,
      refreshToken: "refresh", refreshExpiresAt: Date.now() + 86400000,
    })));
    await page.route("**/api/**", async (route) => {
      const path = new URL(route.request().url()).pathname;
      if (path.endsWith("/refresh")) {
        refreshes++;
        return route.fulfill(rejected ? { status: 401, json: {} } : { json: {
          accessToken: token, expiresIn: 180, refreshToken: "new-refresh", refreshExpiresAt: Date.now() + 86400000,
        } });
      }
      if (path.endsWith("/logout")) return route.fulfill({ status: 204 });
      return route.fulfill(route.request().headers().authorization === `Bearer ${token}`
        ? { json: [] } : { status: 401, json: {} });
    });
    await page.goto("/");
    await expect.poll(() => page.evaluate(() => JSON.parse(localStorage.getItem("sharenote-session"))?.token ?? null))
      .toBe(rejected ? null : token);
    expect(refreshes).toBe(1);
  });
}

test("expired session renews once for concurrent requests, rotates storage and revokes on logout", async ({ page }) => {
  let refreshes = 0;
  let revoked;
  const token = `header.${Buffer.from(JSON.stringify({ role: "USER" })).toString("base64url")}.new`;
  await page.addInitScript(() => localStorage.setItem("sharenote-session", JSON.stringify({
    sessionId: "test-session", token: "old", username: "reader", role: "USER", expiresAt: Date.now() - 1000,
    refreshToken: "refresh-old", refreshExpiresAt: Date.now() + 86400000,
  })));
  await page.route("**/api/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith("/refresh")) {
      refreshes++;
      expect(route.request().postDataJSON().refreshToken).toBe("refresh-old");
      return route.fulfill({ json: { accessToken: token, expiresIn: 180,
        refreshToken: "refresh-new", refreshExpiresAt: Date.now() + 86400000 } });
    }
    if (path.endsWith("/logout")) {
      revoked = route.request().postDataJSON().refreshToken;
      return route.fulfill({ status: 204 });
    }
    expect(route.request().headers().authorization).toBe(`Bearer ${token}`);
    return route.fulfill({ json: [] });
  });
  await page.goto("/");
  await expect.poll(() => page.evaluate(() => JSON.parse(localStorage.getItem("sharenote-session"))?.refreshToken)).toBe("refresh-new");
  expect(refreshes).toBe(1);
  await page.getByRole("button", { name: /Atsijungti/ }).last().click();
  await expect.poll(() => revoked).toBe("refresh-new");
  expect(await page.evaluate(() => localStorage.getItem("sharenote-session"))).toBeNull();
});
