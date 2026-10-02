import { test, expect } from "@playwright/test";

async function mockApi(page, role = "ADMIN") {
  const data = {
    organizations: [{ id: 1, name: "Universitetas", description: "Studijos" }],
    groups: [
      {
        id: 1,
        name: "Saityno technologijos",
        description: "Grupė",
        organizationId: 1,
      },
    ],
    notes: [
      {
        id: 1,
        title: "HTTP metodai",
        content: "GET skaito, POST sukuria.",
        category: "REST",
        groupId: 1,
      },
    ],
  };
  await page.route("**/api/**", async (route) => {
    const req = route.request();
    const [resource, id] = new URL(req.url()).pathname
      .replace("/api/", "")
      .split("/");
    if (resource === "auth")
      return route.fulfill({
        json: {
          accessToken: `header.${Buffer.from(JSON.stringify({ role })).toString("base64url")}.signature`,
          expiresIn: 3600,
        },
      });
    if (!req.headers().authorization)
      return route.fulfill({ status: 401, json: {} });
    const recordId = Number(id);
    if (req.method() === "GET")
      return route.fulfill({
        json: id
          ? data[resource].find((i) => i.id === recordId)
          : data[resource],
      });
    if (role !== "ADMIN") return route.fulfill({ status: 403, json: {} });
    if (req.method() === "DELETE") {
      if (
        resource === "groups" &&
        data.notes.some((n) => n.groupId === recordId)
      )
        return route.fulfill({ status: 409, json: {} });
      data[resource] = data[resource].filter((i) => i.id !== recordId);
      return route.fulfill({ status: 204 });
    }
    const item = { ...req.postDataJSON(), id: recordId || 2 };
    data[resource] =
      req.method() === "POST"
        ? [...data[resource], item]
        : data[resource].map((i) => (i.id === recordId ? item : i));
    return route.fulfill({
      status: req.method() === "POST" ? 201 : 200,
      json: item,
    });
  });
}
async function login(page) {
  await page.goto("/");
  await page.getByLabel("Naudotojo vardas", { exact: true }).fill("test.admin");
  await page
    .getByLabel("Slaptažodis", { exact: true })
    .fill("test-password-123");
  await page.getByRole("button", { name: "Prisijungti", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Užrašai" })).toBeVisible();
}
test("administrator creates, reads, edits and deletes a note", async ({
  page,
}) => {
  await mockApi(page);
  await login(page);
  await page.getByRole("button", { name: "Sukurti užrašą" }).click();
  await page.getByLabel("Antraštė").fill("Naujas užrašas");
  await page.getByLabel("Kategorija").fill("Studijos");
  await page.getByLabel("Grupė", { exact: true }).selectOption("1");
  await page.getByLabel("Užrašo turinys").fill("Svarbios žinios.");
  await page.getByRole("button", { name: "Išsaugoti" }).click();
  await page.getByRole("button", { name: /Naujas užrašas/ }).click();
  await expect(page.getByRole("dialog")).toContainText("Svarbios žinios.");
  await page.getByRole("button", { name: "Redaguoti" }).click();
  await page.getByLabel("Antraštė").fill("Atnaujintas užrašas");
  await page.getByRole("button", { name: "Išsaugoti" }).click();
  await page.getByRole("button", { name: /Atnaujintas užrašas/ }).click();
  await page.getByRole("button", { name: "Ištrinti", exact: true }).click();
  await page.getByRole("button", { name: "Taip, ištrinti" }).click();
  await expect(
    page.getByRole("button", { name: /Atnaujintas užrašas/ }),
  ).toHaveCount(0);
});
test("reader search, modal keyboard dismissal and mobile navigation", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await mockApi(page, "USER");
  await login(page);
  await expect(
    page.getByRole("button", { name: "Sukurti užrašą" }),
  ).toHaveCount(0);
  await page.getByRole("button", { name: /HTTP metodai/ }).click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(
    page.getByRole("button", { name: /HTTP metodai/ }),
  ).toBeFocused();
  await page.getByRole("searchbox").fill("nerasime");
  await expect(page.getByText("Įrašų nerasta")).toBeVisible();
  await page.getByRole("button", { name: "Atidaryti meniu" }).click();
  await page
    .getByRole("navigation")
    .getByRole("button", { name: "Grupės", exact: true })
    .click();
  await expect(page.getByRole("heading", { name: "Grupės" })).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await page.screenshot({ path: "test-results/mobile.png", fullPage: true });
});
test("relationship conflicts stay visible and expired sessions return to login", async ({
  page,
}) => {
  await mockApi(page);
  await login(page);
  await page
    .getByRole("navigation")
    .getByRole("button", { name: "Grupės", exact: true })
    .click();
  await page
    .getByRole("button", { name: /Saityno technologijos Grupė/ })
    .click();
  await page.getByRole("button", { name: "Ištrinti", exact: true }).click();
  await page.getByRole("button", { name: "Taip, ištrinti" }).click();
  await expect(page.getByRole("alert")).toContainText("Veiksmas negalimas");
  await page.getByRole("button", { name: "Atšaukti" }).click();
  await page.route("**/api/groups", (route) =>
    route.fulfill({ status: 401, json: {} }),
  );
  await page.getByRole("button", { name: "Atnaujinti sąrašą" }).click();
  await expect(
    page.getByRole("heading", { name: "Jūsų užrašai laukia" }),
  ).toBeVisible();
  await expect(page.getByRole("status")).toContainText("Sesija pasibaigė");
});
test("desktop workspace and responsive image fit their containers", async ({
  page,
}) => {
  await page.setViewportSize({ width: 1440, height: 1000 });
  await mockApi(page);
  await page.goto("/");
  await expect(page.locator(".auth-story img")).toBeVisible();
  await page.screenshot({ path: "test-results/login.png", fullPage: true });
  await login(page);
  await expect(
    page.getByRole("button", { name: /HTTP metodai/ }),
  ).toBeVisible();
  await page.screenshot({ path: "test-results/desktop.png", fullPage: true });
  for (const width of [320, 768, 1024, 1440]) {
    await page.setViewportSize({ width, height: 900 });
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
  }
});
