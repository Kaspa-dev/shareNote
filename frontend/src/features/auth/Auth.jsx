import { useState } from "react";
import { ArrowRight, BookOpen, Check } from "lucide-react";
import { request } from "../../services/api.js";

export default function Auth({ onLogin }) {
  const [register, setRegister] = useState(false);
  const [show, setShow] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function submit(event) {
    event.preventDefault();
    setError("");
    setBusy(true);
    const body = Object.fromEntries(new FormData(event.currentTarget));
    try {
      if (new TextEncoder().encode(body.password).length > 72)
        throw new Error("Slaptažodis negali viršyti 72 UTF-8 baitų.");
      const result = await request(`auth/${register ? "register" : "login"}`, {
        method: "POST",
        body,
      });
      const payload = JSON.parse(
        atob(
          result.accessToken
            .split(".")[1]
            .replace(/-/g, "+")
            .replace(/_/g, "/"),
        ),
      );
      onLogin({
        token: result.accessToken,
        username: body.username,
        role: payload.role,
        expiresAt: Date.now() + result.expiresIn * 1000,
        refreshToken: result.refreshToken,
        refreshExpiresAt: result.refreshExpiresAt,
      });
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <main id="main" className="auth-layout">
      <section className="auth-story">
        <span className="eyebrow">BENDRA ŽINIŲ ERDVĖ</span>
        <h1>
          Geros idėjos
          <br />
          vertos užrašo.
        </h1>
        <p>
          Organizuokite grupes, atraskite komandos užrašus ir laikykite žinias
          vienoje vietoje.
        </p>
        <img
          src="/notebook.svg"
          alt="Atversta užrašinė su žymekliu ir pieštuku"
        />
        <span className="image-caption">
          Mažiau ieškojimo. Daugiau aiškumo.
        </span>
      </section>
      <section className="auth-panel">
        <BookOpen className="auth-symbol" />
        <span className="eyebrow">SVEIKI ATVYKĘ Į SHARENOTE</span>
        <h2>{register ? "Sukurkite paskyrą" : "Jūsų užrašai laukia"}</h2>
        <p>
          {register
            ? "Nauja paskyra suteikia prieigą prie bendrų užrašų peržiūros."
            : "Prisijunkite prie savo bendros darbo erdvės."}
        </p>
        <form onSubmit={submit}>
          <label>
            Naudotojo vardas
            <input
              name="username"
              required
              minLength={3}
              maxLength={80}
              pattern="[A-Za-z0-9_.\-]{3,80}"
              autoComplete="username"
              placeholder="pvz., vardas.pavarde"
              aria-describedby="username-hint"
            />
          </label>
          <small id="username-hint">
            3–80 simbolių: lotyniškos raidės, skaičiai, taškas, _ arba -.
          </small>
          <label>
            Slaptažodis
            <input
              name="password"
              type={show ? "text" : "password"}
              required
              minLength={12}
              maxLength={72}
              autoComplete={register ? "new-password" : "current-password"}
              placeholder="Bent 12 simbolių"
            />
          </label>
          <label className="checkbox">
            <input
              type="checkbox"
              checked={show}
              onChange={(e) => setShow(e.target.checked)}
            />
            Rodyti slaptažodį
          </label>
          {error && (
            <p role="alert" className="error">
              {error}
            </p>
          )}
          <button className="primary" disabled={busy}>
            {busy ? "Palaukite…" : register ? "Sukurti paskyrą" : "Prisijungti"}
            <ArrowRight size={18} />
          </button>
        </form>
        <p className="auth-switch">
          {register ? "Jau turite paskyrą?" : "Pirmą kartą čia?"}{" "}
          <button
            className="text-button"
            disabled={busy}
            onClick={() => {
              setRegister(!register);
              setError("");
            }}
          >
            {register ? "Prisijunkite" : "Registruokitės"}
          </button>
        </p>
        <div className="auth-note">
          <Check size={16} />
          <span>Viena erdvė organizacijoms, grupėms ir užrašams.</span>
        </div>
      </section>
    </main>
  );
}
