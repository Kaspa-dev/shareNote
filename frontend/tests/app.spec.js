import { test, expect } from "@playwright/test";

const discoveredPath = (path) => path.replace("/api/", "/api/discovered/");

async function mockApi(page, role = "ADMIN", organizations = [{ id: 1, name: "Universitetas", description: "Studijos" }], overrides = {}, discovered = false) {
  const data = {
    organizations,
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
  Object.assign(data, overrides);
  await page.route("**/api/**", async (route) => {
    const req = route.request();
    const pathname = new URL(req.url()).pathname;
    if (discovered && pathname !== "/api/organizations" && !pathname.startsWith("/api/auth/"))
      expect(pathname).toMatch(/^\/api\/discovered\//);
    const parts = pathname.replace("/api/discovered/", "/api/").replace("/api/", "").split("/");
    expect(["groups", "notes"]).not.toContain(parts[0]);
    const nested = parts.length >= 3;
    const notePath = parts.length >= 5;
    const [resource, id] = nested ? parts.slice(notePath ? 4 : 2) : parts;
    const parentKey = notePath ? "groupId" : "organizationId";
    const parentId = Number(parts[notePath ? 3 : 1]);
    if (notePath && !data.groups.some((group) => group.id === parentId && group.organizationId === Number(parts[1])))
      return route.fulfill({ status: 404, json: {} });
    if (nested && !data[notePath ? "groups" : parts[0]]?.some((i) => i.id === parentId))
      return route.fulfill({ status: 404, json: {} });
    if (nested && id && !data[resource]?.some((i) => i.id === Number(id) && i[parentKey] === parentId))
      return route.fulfill({ status: 404, json: {} });
    if (resource === "auth")
      return route.fulfill({
        json: {
          accessToken: `header.${Buffer.from(JSON.stringify({ role, sub: "42" })).toString("base64url")}.signature`,
          expiresIn: 180,
        },
      });
    if (!req.headers().authorization)
      return route.fulfill({ status: 401, json: {} });
    const recordId = Number(id);
    const represent = (item) => {
      const original = `/api/${parts.slice(0, notePath ? 5 : nested ? 3 : 1).join("/")}`;
      const collection = discovered ? discoveredPath(original) : original;
      const self = `${collection}/${item.id}`;
      return { ...item,
        ...(resource === "notes" ? { group: data.groups.find((group) => group.id === item.groupId) } : {}),
        _links: { self: { href: self }, collection: { href: collection },
          ...(resource === "organizations" ? { groups: { href: `${self}/groups` } } : {}),
          ...(resource === "groups" ? { notes: { href: `${self}/notes` } } : {}),
        },
      };
    };
    if (req.method() === "GET")
      return route.fulfill({
        json: id
          ? represent(data[resource].find((i) => i.id === recordId))
          : data[resource].filter((i) => !nested || i[parentKey] === parentId).map(represent),
      });
    if (role !== "ADMIN" && !(role === "EDITOR" && resource === "notes" &&
        (req.method() === "POST" || data.notes.find((note) => note.id === recordId)?.ownerId === 42)))
      return route.fulfill({ status: 403, json: {} });
    if (req.method() === "DELETE") {
      if (
        resource === "groups" &&
        data.notes.some((n) => n.groupId === recordId)
      )
        return route.fulfill({ status: 409, json: {} });
      data[resource] = data[resource].filter((i) => i.id !== recordId);
      return route.fulfill({ status: 204 });
    }
    const item = { ...req.postDataJSON(), ...(nested ? { [parentKey]: parentId } : {}), id: recordId || 2,
      ...(resource === "notes" ? { ownerId: req.method() === "POST" ? 42 : data.notes.find((note) => note.id === recordId)?.ownerId } : {}) };
    data[resource] =
      req.method() === "POST"
        ? [...data[resource], item]
        : data[resource].map((i) => (i.id === recordId ? item : i));
    return route.fulfill({
      status: req.method() === "POST" ? 201 : 200,
      json: represent(item),
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

test("note priority defaults, saves and survives reload and editing", async ({ page }) => {
  await mockApi(page);
  await login(page);
  await page.getByRole("button", { name: "Sukurti užrašą" }).click();
  await expect(page.getByRole("radio", { name: "Vidutinė" })).toBeChecked();
  await page.getByLabel("Antraštė").fill("Svarbus užrašas");
  await page.getByLabel("Kategorija").fill("Test");
  await page.getByLabel("Grupė", { exact: true }).selectOption("1");
  await page.getByLabel("Užrašo turinys").fill("Svarbus turinys");
  await page.getByRole("radio", { name: "Aukšta" }).check();
  await expect(page.getByRole("radio", { name: "Vidutinė" })).not.toBeChecked();
  const saved = page.waitForRequest(req => req.method() === "POST" && req.url().endsWith("/notes"));
  await page.getByRole("button", { name: "Išsaugoti" }).click();
  expect((await saved).postDataJSON().priority).toBe("HIGH");
  await expect(page.getByRole("button", { name: /Svarbus užrašas/ })).toContainText("Svarba: Aukšta");
  await page.reload();
  await page.getByRole("button", { name: /Svarbus užrašas/ }).click();
  await expect(page.getByRole("dialog")).toContainText("Svarba: Aukšta");
  await page.getByRole("button", { name: "Redaguoti", exact: true }).click();
  await expect(page.getByRole("radio", { name: "Aukšta" })).toBeChecked();
  await page.getByRole("radio", { name: "Žema" }).check();
  await page.getByRole("button", { name: "Išsaugoti" }).click();
  await expect(page.getByRole("button", { name: /Svarbus užrašas/ })).toContainText("Svarba: Žema");
  await page.reload();
  await page.getByRole("button", { name: /Svarbus užrašas/ }).click();
  await expect(page.getByRole("dialog")).toContainText("Svarba: Žema");
});

test("editor manages own notes while other authors and library structure stay read-only", async ({ page }) => {
  await mockApi(page, "EDITOR", undefined, { notes: [
    { id: 1, title: "Mano užrašas", content: "Turinys", category: "REST", groupId: 1, ownerId: 42 },
    { id: 3, title: "Svetimas užrašas", content: "Turinys", category: "REST", groupId: 1, ownerId: 43 },
    { id: 4, title: "Senas užrašas", content: "Turinys", category: "REST", groupId: 1, ownerId: null },
  ] });
  await login(page);
  await expect(page.getByText("Redaktorius", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Sukurti užrašą" }).click();
  await page.getByLabel("Antraštė").fill("Redaktoriaus naujas");
  await page.getByLabel("Kategorija").fill("Test");
  await page.getByLabel("Grupė", { exact: true }).selectOption("1");
  await page.getByLabel("Užrašo turinys").fill("Naujas turinys");
  await page.getByRole("button", { name: "Išsaugoti" }).click();
  await page.getByRole("button", { name: /Redaktoriaus naujas/ }).click();
  await page.getByRole("button", { name: "Redaguoti", exact: true }).click();
  await page.getByLabel("Antraštė").fill("Pakeistas mano užrašas");
  await page.getByRole("button", { name: "Išsaugoti" }).click();
  await page.getByRole("button", { name: /Pakeistas mano užrašas/ }).click();
  await page.getByRole("button", { name: "Ištrinti", exact: true }).click();
  await page.getByRole("button", { name: "Taip, ištrinti", exact: true }).click();
  await expect(page.getByRole("button", { name: /Pakeistas mano užrašas/ })).toHaveCount(0);
  for (const title of ["Svetimas užrašas", "Senas užrašas"]) {
    await page.getByRole("button", { name: new RegExp(title) }).click();
    await expect(page.getByRole("dialog").getByRole("button", { name: "Redaguoti", exact: true })).toHaveCount(0);
    await expect(page.getByRole("dialog").getByRole("button", { name: "Ištrinti", exact: true })).toHaveCount(0);
    await page.keyboard.press("Escape");
  }
  await page.getByRole("navigation").getByRole("button", { name: "Grupės", exact: true }).click();
  await expect(page.getByRole("button", { name: "Sukurti grupę" })).toHaveCount(0);
  await page.getByRole("button", { name: /Saityno technologijos Grupė/ }).click();
  await expect(page.getByRole("dialog").getByRole("button", { name: "Redaguoti", exact: true })).toHaveCount(0);
  await page.keyboard.press("Escape");
  await page.getByRole("navigation").getByRole("button", { name: "Organizacijos", exact: true }).click();
  await expect(page.getByRole("button", { name: "Sukurti organizaciją" })).toHaveCount(0);
});

test("pagination navigates, searches all records, changes size and resets on section change", async ({ page }) => {
  const notes = Array.from({ length: 26 }, (_, i) => ({
    id: i + 1, title: `Note ${i + 1}`, content: "Content", category: "REST", groupId: 1,
  }));
  await mockApi(page, "ADMIN", undefined, { notes });
  await login(page);
  const pagination = page.getByRole("navigation", { name: "Puslapiavimas" });
  await expect(page.locator(".record")).toHaveCount(10);
  await expect(pagination.getByRole("button", { name: "Ankstesnis" })).toBeDisabled();
  await pagination.getByRole("button", { name: "Kitas" }).click();
  await expect(page.locator(".record").first()).toContainText("Note 11");
  await pagination.getByRole("button", { name: "Kitas" }).click();
  await expect(page.locator(".record")).toHaveCount(6);
  await expect(pagination.getByRole("button", { name: "Kitas" })).toBeDisabled();
  await page.getByLabel("Filtruoti pagal grupę").selectOption("1");
  await expect(pagination).toContainText("1 / 3");
  await pagination.getByRole("button", { name: "Kitas" }).click();
  await page.getByRole("searchbox").fill("Note 2");
  await expect(page.locator(".record")).toHaveCount(8);
  await expect(pagination).toContainText("1 / 1");
  await page.getByRole("searchbox").fill("");
  await expect(page.locator(".record").first()).toContainText("Note 1");
  await page.getByLabel("Įrašų puslapyje", { exact: true }).selectOption("25");
  await expect(page.locator(".record")).toHaveCount(25);
  await pagination.getByRole("button", { name: "Kitas" }).click();
  await expect(page.locator(".record")).toHaveCount(1);
  await page.getByRole("button", { name: "Grupės", exact: false }).first().click();
  await expect(pagination).toContainText("1 / 1");
});

test("deleting the only record on the last page returns to the previous page", async ({ page }) => {
  const notes = Array.from({ length: 11 }, (_, i) => ({
    id: i + 1, title: `Note ${i + 1}`, content: "Content", category: "REST", groupId: 1,
  }));
  await mockApi(page, "ADMIN", undefined, { notes });
  await login(page);
  await page.getByRole("button", { name: "Kitas", exact: true }).click();
  await page.locator(".record").click();
  await page.getByRole("button", { name: "Ištrinti", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Taip, ištrinti", exact: true }).click();
  await expect(page.locator(".record")).toHaveCount(10);
  await expect(page.getByRole("navigation", { name: "Puslapiavimas" })).toContainText("1 / 1");
});
for (const discovered of [false, true]) {
test(`administrator creates, reads, edits and deletes a note${discovered ? " using discovered URLs" : ""}`, async ({
  page,
}) => {
  await mockApi(page, "ADMIN", undefined, {}, discovered);
  const noteRequests = [];
  page.on("request", (request) => {
    if (new URL(request.url()).pathname.includes("/notes") && request.method() !== "GET")
      noteRequests.push([request.method(), new URL(request.url()).pathname]);
  });
  await login(page);
  await page.getByRole("button", { name: "Sukurti užrašą" }).click();
  await page.getByLabel("Antraštė").fill("Naujas užrašas");
  await page.getByLabel("Kategorija").fill("Studijos");
  await expect(page.getByLabel("Grupė", { exact: true })).toHaveAttribute("required", "");
  await expect(page.getByLabel("Grupė", { exact: true })).toHaveValue("");
  await page.getByLabel("Užrašo turinys").fill("Svarbios žinios.");
  await page.getByRole("button", { name: "Išsaugoti" }).click();
  await expect(page.getByRole("dialog")).toBeVisible();
  expect(noteRequests).toEqual([]);
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
  expect(noteRequests).toEqual([
    ["POST", "/api/organizations/1/groups/1/notes"],
    ["PUT", "/api/organizations/1/groups/1/notes/2"],
    ["DELETE", "/api/organizations/1/groups/1/notes/2"],
  ].map(([method, path]) => [method, discovered ? discoveredPath(path) : path]));
});
}
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
  await expect(page.getByRole("dialog")).toContainText("Grupė: Saityno technologijos");
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
  await page.route("**/api/organizations/1/groups", (route) =>
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

test("group creation, editing and deletion follow discovered URLs", async ({ page }) => {
  await mockApi(page, "ADMIN", undefined, {}, true);
  await login(page);
  await page.getByRole("navigation").getByRole("button", { name: "Grupės", exact: true }).click();
  await page.getByRole("button", { name: "Sukurti grupę" }).click();
  await page.getByLabel("Pavadinimas").fill("Nauja grupė");
  const organization = page.getByLabel("Organizacija", { exact: true });
  await expect(organization).toHaveAttribute("required", "");
  await expect(organization).toHaveValue("");
  await page.getByRole("button", { name: "Išsaugoti" }).click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await organization.selectOption("1");
  const created = page.waitForRequest((request) => request.method() === "POST" && request.url().endsWith("/api/discovered/organizations/1/groups"));
  await page.getByRole("button", { name: "Išsaugoti" }).click();
  expect((await created).postDataJSON()).not.toHaveProperty("organizationId");
  const opened = page.waitForRequest((request) => request.method() === "GET" && request.url().endsWith("/api/discovered/organizations/1/groups/2"));
  await page.getByRole("button", { name: /Nauja grupė/ }).click();
  await opened;
  await expect(page.getByRole("dialog")).toContainText("Universitetas");
  await page.getByRole("button", { name: "Redaguoti" }).click();
  await page.getByLabel("Pavadinimas").fill("Atnaujinta grupė");
  const updated = page.waitForRequest((request) => request.method() === "PUT" && request.url().endsWith("/api/discovered/organizations/1/groups/2"));
  await page.getByRole("button", { name: "Išsaugoti" }).click();
  expect((await updated).postDataJSON()).not.toHaveProperty("organizationId");
  await page.getByRole("button", { name: /Atnaujinta grupė/ }).click();
  await page.getByRole("button", { name: "Ištrinti", exact: true }).click();
  const deleted = page.waitForRequest((request) => request.method() === "DELETE" && request.url().endsWith("/api/discovered/organizations/1/groups/2"));
  await page.getByRole("button", { name: "Taip, ištrinti" }).click();
  await deleted;
  await expect(page.getByRole("button", { name: /Atnaujinta grupė/ })).toHaveCount(0);
});

test("group creation explains when an organization must be created first", async ({ page }) => {
  await mockApi(page, "ADMIN", []);
  await login(page);
  await page.getByRole("navigation").getByRole("button", { name: "Grupės", exact: true }).click();
  await page.getByRole("button", { name: "Sukurti grupę" }).click();
  await expect(page.getByText("Pirmiausia sukurkite organizaciją.")).toBeVisible();
  await expect(page.getByRole("button", { name: "Išsaugoti" })).toBeDisabled();
});

test("note creation requires creating a group first when none exist", async ({ page }) => {
  await mockApi(page, "ADMIN", undefined, { groups: [], notes: [] });
  await login(page);
  await page.getByRole("button", { name: "Sukurti užrašą" }).click();
  await expect(page.getByText("Pirmiausia sukurkite grupę.")).toBeVisible();
  await expect(page.getByRole("button", { name: "Išsaugoti" })).toBeDisabled();
});

test("missing navigation links show an error without guessing a child URL", async ({ page }) => {
  await mockApi(page);
  const childRequests = [];
  page.on("request", (request) => {
    if (request.url().includes("/groups")) childRequests.push(request.url());
  });
  await page.route("**/api/organizations", (route) => route.fulfill({
    json: [{ id: 1, name: "Universitetas", description: "Studijos" }],
  }));
  await login(page);
  await expect(page.getByRole("alert")).toContainText("Serveris nepateikė tinkamos ištekliaus nuorodos");
  expect(childRequests).toEqual([]);
});

test("an empty library can create its first organization at the entry point", async ({ page }) => {
  await mockApi(page, "ADMIN", [], { groups: [], notes: [] }, true);
  await login(page);
  await page.getByRole("navigation").getByRole("button", { name: "Organizacijos", exact: true }).click();
  await page.getByRole("button", { name: "Sukurti organizaciją" }).click();
  await page.getByLabel("Pavadinimas").fill("Pirmoji organizacija");
  await page.getByRole("button", { name: "Išsaugoti" }).click();
  await page.getByRole("button", { name: /Pirmoji organizacija/ }).click();
  await expect(page.getByRole("dialog")).toContainText("Pirmoji organizacija");
});
