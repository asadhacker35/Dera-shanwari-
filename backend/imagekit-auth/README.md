# Dera Shanwari ImageKit Upload Auth Backend

Generates short-lived ImageKit client upload signatures (`token`, `expire`, `signature`) and performs authenticated file deletion so `IMAGEKIT_PRIVATE_KEY` never ships inside Android APKs.

## Endpoints
- `GET /health` -> `{ "ok": true }`
- `GET /auth` (Bearer Firebase ID Token required) -> `{ "token", "expire", "signature", "publicKey", "uid" }`
- `DELETE /file/:fileId` or `POST /delete` `{ "fileId": "..." }` (Bearer Firebase ID Token required) -> `{ "deleted": true, "fileId" }`

## Deploy / Run
```bash
export IMAGEKIT_PUBLIC_KEY="public_..."
export IMAGEKIT_PRIVATE_KEY="private_..."
npm start
```
Then configure the HTTPS base URL and Public Key inside **Admin App -> More -> Settings -> ImageKit Upload Backend**.
