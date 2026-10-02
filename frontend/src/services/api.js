export function readSession() {
  try {
    const session = JSON.parse(sessionStorage.getItem("sharenote-session"));
    return session && (session.expiresAt > Date.now() ||
      (session.refreshToken && session.refreshExpiresAt > Date.now())) ? session : null;
  } catch {
    return null;
  }
}

let renewal;
let sessionVersion = 0;

export function saveSession(session) {
  sessionVersion++;
  sessionStorage.setItem("sharenote-session", JSON.stringify(session));
  window.dispatchEvent(new Event("session-renewed"));
}

export function renewSession() {
  if (renewal) return renewal;
  const session = readSession();
  if (!session?.refreshToken || session.refreshExpiresAt <= Date.now()) {
    return Promise.reject(Object.assign(new Error("Sesija pasibaigė. Prisijunkite iš naujo."), { status: 401 }));
  }
  const version = sessionVersion;
  renewal = request("auth/refresh", {
    method: "POST", body: { refreshToken: session.refreshToken },
  }).then((result) => {
    const payload = JSON.parse(atob(result.accessToken.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")));
    const next = { ...session, token: result.accessToken, role: payload.role,
      expiresAt: Date.now() + result.expiresIn * 1000,
      refreshToken: result.refreshToken, refreshExpiresAt: result.refreshExpiresAt };
    if (version === sessionVersion) saveSession(next);
    return next;
  }).finally(() => { renewal = undefined; });
  return renewal;
}

export async function endSession() {
  const session = readSession();
  const pending = renewal;
  sessionVersion++;
  sessionStorage.removeItem("sharenote-session");
  const latest = pending ? await pending.catch(() => session) : session;
  if (latest?.refreshToken) await request("auth/logout", {
    method: "POST", body: { refreshToken: latest.refreshToken },
  });
}

export async function request(
  path,
  { token, method = "GET", body, signal, retried = false } = {},
) {
  const authenticated = token && !path.startsWith("auth/");
  if (authenticated && !retried) {
    const session = readSession();
    if (session) token = session.expiresAt <= Date.now() + 30000
      ? (await renewSession()).token : session.token;
  }
  let response;
  try {
    response = await fetch(`/api/${path}`, {
      method,
      signal,
      headers: {
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(body ? { "Content-Type": "application/json" } : {}),
      },
      ...(body ? { body: JSON.stringify(body) } : {}),
    });
  } catch (error) {
    if (error.name === "AbortError") throw error;
    throw new Error(
      "Nepavyko pasiekti serverio. Patikrinkite ryšį ir bandykite dar kartą.",
    );
  }
  if (response.status === 401 && authenticated && !retried) {
    const current = readSession();
    const next = current && current.token !== token ? current : await renewSession();
    return request(path, { token: next.token, method, body, signal, retried: true });
  }
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}));
    const messages = {
      401: path.startsWith("auth/")
        ? "Neteisingas naudotojo vardas arba slaptažodis."
        : "Sesija pasibaigė. Prisijunkite iš naujo.",
      403: "Šiam veiksmui reikia administratoriaus teisių.",
      404: "Įrašas neberastas. Atnaujinkite sąrašą.",
      409: path.startsWith("auth/")
        ? "Šis naudotojo vardas jau užimtas."
        : "Veiksmas negalimas: patikrinkite susijusias grupes ir užrašus.",
      422: "Patikrinkite laukų reikšmes ir jų ilgį.",
    };
    const error = new Error(
      messages[response.status] || "Serverio klaida. Bandykite dar kartą.",
    );
    error.status = response.status;
    error.fields = problem.errors;
    throw error;
  }
  return response.status === 204 ? null : response.json();
}
