# Free SearXNG search server for Balloon Price Finder

The backend uses self-hosted **SearXNG** instead of Google. No paid search API key required.

## Docker Compose setup

1. On a computer or VPS with Docker Compose, clone this GitHub repository.
2. Copy `.env.example` to `.env`. Replace `SEARXNG_SECRET` with a long random secret; never commit `.env`.
3. Run `docker compose up -d --build`.
4. Test `http://127.0.0.1:8000/health` and `http://127.0.0.1:8000/search?q=Sempertex%20260%20red`.
5. Set up an HTTPS reverse proxy to `127.0.0.1:8000`, with authentication and rate limiting before public exposure.
6. Enter the public HTTPS URL in the Android app's API field, then tap **Search web**.

Only the API listens on localhost port 8000; SearXNG is internal to the Compose network. An HTTPS host/tunnel is required for your phone to connect. GitHub Actions builds the APK but cannot host a persistent search server.

The server searches five supplier domains and reads public Product JSON-LD metadata where allowed. It does not guarantee live prices or inventory. Unknown stock is not out of stock. Supplier robots policies, search engine limits and host expenses still apply.
