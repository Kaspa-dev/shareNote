import { test, expect } from "@playwright/test";

for (const rejected of [false, true]) {
  test(`401 renewal ${rejected ? "rejects invalid refresh tokens" : "retries with the new token"}`, async ({ page }) => {
    let refreshes = 0;
    const token = `header.${Buffer.from(JSON.stringify({ role: "USER" })).toString("base64url")}.new`;
    await page.addInitScript(() => sessionStorage.setItem("sharenote-session", JSON.stringify({
      token: "rejected-access", username: "reader", role: "USER", expiresAt: Date.now() + 3600000,
      refreshToken: "refresh", refreshExpiresAt: Date.now() + 86400000,
    })));
    await page.route("**/api/**", async (route) => {
      const path = new URL(route.request().url()).pathname;
      if (path.endsWith("/refresh")) {
        refreshes++;
        return route.fulfill(rejected ? { status: 401, json: {} } : { json: {
          accessToken: token, expiresIn: 3600, refreshToken: "new-refresh", refreshExpiresAt: Date.now() + 86400000,
        } });
      }
      if (path.endsWith("/logout")) return route.fulfill({ status: 204 });
      return route.fulfill(route.request().headers().authorization === `Bearer ${token}`
        ? { json: [] } : { status: 401, json: {} });
    });
    await page.goto("/");
    await expect.poll(() => page.evaluate(() => JSON.parse(sessionStorage.getItem("sharenote-session"))?.token ?? null))
      .toBe(rejected ? null : token);
    expect(refreshes).toBe(1);
  });
}

test("expired session renews once for concurrent requests, rotates storage and revokes on logout", async ({ page }) => {
  let refreshes = 0;
  let revoked;
  const token = `header.${Buffer.from(JSON.stringify({ role: "USER" })).toString("base64url")}.new`;
  await page.addInitScript(() => sessionStorage.setItem("sharenote-session", JSON.stringify({
    token: "old", username: "reader", role: "USER", expiresAt: Date.now() - 1000,
    refreshToken: "refresh-old", refreshExpiresAt: Date.now() + 86400000,
  })));
  await page.route("**/api/**", async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith("/refresh")) {
      refreshes++;
      expect(route.request().postDataJSON().refreshToken).toBe("refresh-old");
      return route.fulfill({ json: { accessToken: token, expiresIn: 3600,
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
  await expect.poll(() => page.evaluate(() => JSON.parse(sessionStorage.getItem("sharenote-session"))?.refreshToken)).toBe("refresh-new");
  expect(refreshes).toBe(1);
  await page.getByRole("button", { name: /Atsijungti/ }).last().click();
  await expect.poll(() => revoked).toBe("refresh-new");
  expect(await page.evaluate(() => sessionStorage.getItem("sharenote-session"))).toBeNull();
});
