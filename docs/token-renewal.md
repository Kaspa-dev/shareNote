# Token renewal

Login and registration return `accessToken`, `tokenType`, `expiresIn` (3600 seconds),
`refreshToken` and `refreshExpiresAt` (Unix milliseconds).

POST `/api/auth/refresh` with JSON `{ "refreshToken": "..." }` to receive a new
token pair. No Authorization header is required. Each refresh token is single-use;
rotation is atomic. The seven-day deadline starts at login and is not extended by
rotation. Invalid, consumed and expired refresh tokens return 401.

POST `/api/auth/logout` with the same JSON shape to revoke the refresh token.
This endpoint is idempotent and returns 204. Already issued access tokens remain
valid until expiry (or an API restart). Refresh tokens survive API restarts and
can obtain access tokens signed with the new key.

The browser stores both tokens in sessionStorage, renews one minute before access
expiry, and retries authenticated requests once after a 401. Concurrent renewals
within a tab share a single request. Temporary renewal failures retry after 30
seconds; invalid refresh tokens require signing in again. Logout clears local
state immediately and revokes the latest refresh token, including an in-flight
rotation. If the server is unreachable, only local logout can be guaranteed.

Migration V4 stores only SHA-256 hashes of cryptographically random 256-bit refresh
tokens. Expired rows are removed when a new token is issued. Treat refresh tokens
as credentials; use HTTPS and do not log them. sessionStorage tokens are accessible
to JavaScript, so preventing XSS remains essential. Tokens copied to another tab
compete for the same single-use refresh token; that tab may need to sign in again.
