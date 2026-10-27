const SESSION_KEY = "sharenote-session";
const sessionError = () => Object.assign(new Error("Sesija pasibaigė. Prisijunkite iš naujo."), { status: 401 });
const cancelled = () => Object.assign(new Error("Session changed."), { name: "AbortError" });

function readStored() {
  try {
    sessionStorage.removeItem(SESSION_KEY);
    const raw = localStorage.getItem(SESSION_KEY);
    if (!raw) return null;
    const session = JSON.parse(raw);
    if (!session || typeof session.token !== "string" || !session.token ||
        !Number.isFinite(session.expiresAt) || !["USER", "EDITOR", "ADMIN"].includes(session.role) ||
        (session.refreshToken != null && (typeof session.refreshToken !== "string" || !Number.isFinite(session.refreshExpiresAt)))) {
      localStorage.removeItem(SESSION_KEY);
      return null;
    }
    return session;
  } catch {
    try { localStorage.removeItem(SESSION_KEY); } catch { /* Storage unavailable. */ }
    return null;
  }
}
export function readSession() {
  const session = readStored();
  if (!session) return null;
  if (session.expiresAt > Date.now() || (session.refreshToken && session.refreshExpiresAt > Date.now())) return session;
  try { localStorage.removeItem(SESSION_KEY); } catch { /* Already signed out. */ }
  return null;
}
let renewal;
export function saveSession(session) {
  const next = { ...session, sessionId: session.sessionId || crypto.randomUUID() };
  try { localStorage.setItem(SESSION_KEY, JSON.stringify(next)); }
  catch { throw new Error("Naršyklė neleidžia išsaugoti prisijungimo. Įjunkite svetainės saugyklą."); }
  window.dispatchEvent(new Event("session-renewed"));
  return next;
}
async function revoke(refreshToken) {
  if (refreshToken) await request("auth/logout", { method: "POST", body: { refreshToken } });
}
export function renewSession() {
  if (renewal) return renewal;
  const initial = readSession();
  if (!initial?.refreshToken || initial.refreshExpiresAt <= Date.now()) return Promise.reject(sessionError());
  // Without Web Locks, require login rather than race single-use tokens across tabs.
  if (!navigator.locks) return Promise.reject(sessionError());
  renewal = navigator.locks.request("sharenote-session-refresh", async () => {
    const session = readSession();
    if (!session || session.sessionId !== initial.sessionId) throw cancelled();
    if (session.refreshToken !== initial.refreshToken) return session;
    let result;
    try { result = await request("auth/refresh", { method: "POST", body: { refreshToken: session.refreshToken } }); }
    catch (error) {
      if (readStored()?.sessionId !== session.sessionId) throw cancelled();
      throw error;
    }
    const current = readStored();
    if (!current || current.sessionId !== session.sessionId || current.refreshToken !== session.refreshToken) {
      try { await revoke(result.refreshToken); } finally { throw cancelled(); }
    }
    const payload = JSON.parse(atob(result.accessToken.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")));
    const next = { ...session, token: result.accessToken, role: payload.role, userId: payload.sub,
      expiresAt: Date.now() + result.expiresIn * 1000,
      refreshToken: result.refreshToken, refreshExpiresAt: result.refreshExpiresAt };
    try { return saveSession(next); }
    catch (error) { await revoke(result.refreshToken); throw error; }
  }).finally(() => { renewal = undefined; });
  return renewal;
}
export async function endSession() {
  const session = readStored();
  localStorage.removeItem(SESSION_KEY);
  window.dispatchEvent(new Event("session-renewed"));
  // In-flight refreshes check storage before saving and revoke their rotated tokens.
  await revoke(session?.refreshToken);
}
export async function request(path, { token, method = "GET", body, signal, retried = false } = {}) {
  const authenticated = token && !path.startsWith("auth/");
  const originalSession = authenticated ? readSession() : null;
  if (authenticated && !retried) {
    const session = readSession();
    if (!session) throw sessionError();
    token = session.expiresAt <= Date.now() + 30000 ? (await renewSession()).token : session.token;
  }
  let response;
  try {
    response = await fetch(path.startsWith("/api/") ? path : `/api/${path}`, {
      method, signal,
      headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(body ? { "Content-Type": "application/json" } : {}) },
      ...(body ? { body: JSON.stringify(body) } : {}),
    });
  } catch (error) {
    if (error.name === "AbortError") throw error;
    throw new Error("Nepavyko pasiekti serverio. Patikrinkite ryšį ir bandykite dar kartą.");
  }
  if (authenticated && readSession()?.sessionId !== originalSession?.sessionId) throw cancelled();
  if (response.status === 401 && authenticated && !retried) {
    const current = readSession();
    const next = current && current.token !== token ? current : await renewSession();
    return request(path, { token: next.token, method, body, signal, retried: true });
  }
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}));
    const messages = {
      401: path.startsWith("auth/") ? "Neteisingas naudotojo vardas arba slaptažodis." : "Sesija pasibaigė. Prisijunkite iš naujo.",
      403: "Neturite teisių atlikti šio veiksmo. Redaktorius gali keisti tik savo užrašus.",
      404: "Įrašas neberastas. Atnaujinkite sąrašą.",
      409: path.startsWith("auth/") ? "Šis naudotojo vardas jau užimtas." : "Veiksmas negalimas: patikrinkite susijusias grupes ir užrašus.",
      422: "Patikrinkite laukų reikšmes ir jų ilgį.",
    };
    throw Object.assign(new Error(messages[response.status] || "Serverio klaida. Bandykite dar kartą."), { status: response.status, fields: problem.errors });
  }
  return response.status === 204 ? null : response.json();
}
// The organization collection is the API entry point; discover children via links.
export const libraryEntry = "/api/organizations";
export function resourceLink(resource, relation = "self") {
  const href = resource?._links?.[relation]?.href;
  // Only follow the API's same-origin path contract when sending Bearer tokens.
  if (typeof href !== "string" || !href.startsWith("/api/") || /[\\\s#]/.test(href) ||
      new URL(href, "https://sharenote.invalid").pathname !== href.split("?")[0]) {
    throw new Error("Serveris nepateikė tinkamos ištekliaus nuorodos. Atnaujinkite sąrašą.");
  }
  return href;
}
export function resourceWrite(section, item, values, data) {
  const parentKey = section === "notes" ? "groupId" : section === "groups" ? "organizationId" : null;
  if (item && parentKey && item[parentKey] !== values[parentKey]) throw new Error("Įrašo tėvinio ištekliaus keisti negalima.");
  const body = { ...values };
  const path = item ? resourceLink(item)
    : section === "notes" ? resourceLink(data.groups.find((group) => group.id === values.groupId), "notes")
      : section === "groups" ? resourceLink(data.organizations.find((organization) => organization.id === values.organizationId), "groups") : libraryEntry;
  if (section === "notes" || section === "groups") delete body[parentKey];
  return { path, body };
}
