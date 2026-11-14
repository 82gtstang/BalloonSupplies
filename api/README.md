# Balloon Price Finder Search API

This is a separate **server**; it cannot be hosted by GitHub Actions. Deploy it to a Python web host (Render, Railway, Fly.io, VPS, etc.). No supplier partnerships or private supplier APIs are implied.

## Setup

1. Create a Google Programmable Search Engine configured to search the entire web, and enable the **Custom Search JSON API** in Google Cloud.
2. Set environment variables `GOOGLE_SEARCH_API_KEY` and `GOOGLE_SEARCH_ENGINE_ID` (the `cx` identifier) on the host. Never put the API key in the Android app or GitHub source.
3. Install dependencies: `pip install -r api/requirements.txt`.
4. Start from repository root: `uvicorn api.main:app --host 0.0.0.0 --port 8000` (or the host's `PORT`).
5. Open `https://YOUR-HOST/health` and verify `search_configured: true`.
6. Paste `https://YOUR-HOST` into the Android app's API URL field.

API: `GET /search?q=Sempertex%20260%20red`, `GET /suppliers`, `GET /health`.

Search uses Google Programmable Search results restricted to the five supplier domains, then reads public Product JSON-LD when site crawling policies permit. It does **not** guarantee a live inventory feed: prices are null and stock is unknown when structured data is missing, stale, blocked, or incomplete. Some suppliers may block automated access. Search API quotas/charges and supplier website terms apply. The current implementation performs up to five Google queries per search and should be protected by server-side rate limits and authentication before public deployment.

Do not treat search result snippets as proof of price or stock. Checkout is on the original supplier's site.
