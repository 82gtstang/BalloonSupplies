"""Balloon Price Finder API backed by self-hosted SearXNG."""
import json
import os
import re
import time
from concurrent.futures import ThreadPoolExecutor
from html import unescape
from urllib.parse import urlencode, urlparse
from urllib.request import Request, urlopen
from urllib.robotparser import RobotFileParser
from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware

app = FastAPI(title="Balloon Price Finder API", version="2.0")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["GET"], allow_headers=["*"])
SUPPLIERS = {
    "A2Z Balloons": "a2zballoons.com",
    "Bargain Balloons": "bargainballoons.com",
    "Balloons Online": "balloonsonline.com",
    "LA Balloons": "laballoons.com",
    "Burton + Burton": "burtonandburton.com",
}
USER_AGENT = "BalloonPriceFinder/2.0 (product metadata reader)"
def get_json(url):
    with urlopen(Request(url, headers={"User-Agent": USER_AGENT}), timeout=12) as response:
        return json.loads(response.read(1024 * 1024).decode("utf-8"))

def allowed(url, domain):
    host = (urlparse(url).hostname or "").lower()
    return urlparse(url).scheme == "https" and (host == domain or host.endswith("." + domain))

def parse_product(node):
    if isinstance(node, list):
        for child in node:
            found = parse_product(child)
            if found: return found
        return None
    if not isinstance(node, dict): return None
    typ = node.get("@type", "")
    if typ == "Product" or (isinstance(typ, list) and "Product" in typ):
        return node
    for key in ("@graph", "mainEntity", "itemListElement"):
        found = parse_product(node.get(key))
        if found: return found
    return None

def product_metadata(url, domain):
    # Respect site crawling policies. No bypass of bot protections or logins.
    if not allowed(url, domain): return {}
    origin = "https://" + (urlparse(url).hostname or "")
    robots = RobotFileParser()
    try:
        robots.set_url(origin + "/robots.txt")
        robots.read()
        if not robots.can_fetch(USER_AGENT, url): return {}
        with urlopen(Request(url, headers={"User-Agent": USER_AGENT}), timeout=8) as response:
            html = response.read(500000).decode("utf-8", errors="replace")
        for raw in re.findall(r'<script[^>]*type=["\']application/ld\+json["\'][^>]*>(.*?)</script>', html, re.I | re.S):
            try:
                p = parse_product(json.loads(unescape(raw)))
                if not p: continue
                offers = p.get("offers") or {}
                if isinstance(offers, list): offers = next((x for x in offers if isinstance(x, dict)), {})
                price = offers.get("price") or offers.get("lowPrice")
                currency = offers.get("priceCurrency", "USD")
                availability = str(offers.get("availability", "")).split("/")[-1].lower()
                stock = ("in_stock" if availability == "instock" else "out_of_stock" if availability in ("outofstock", "soldout") else "unknown")
                return {"name": p.get("name"), "price": str(price) if price is not None else None,
                        "currency": currency, "stock": stock}
            except (ValueError, TypeError, AttributeError): continue
    except Exception: pass
    return {}

def search_supplier(name, domain, query, searxng_url):
    params = urlencode({"q": query + " site:" + domain, "format": "json", "categories": "general"})
    data = get_json(searxng_url.rstrip("/") + "/search?" + params)
    out = []
    for item in data.get("results", []):
        url = item.get("url", "")
        if not allowed(url, domain): continue
        metadata = product_metadata(url, domain)
        out.append({"supplier": name, "name": metadata.get("name") or item.get("title", "Product"),
                    "url": url, "price": metadata.get("price"), "currency": metadata.get("currency", "USD"),
                    "stock": metadata.get("stock", "unknown"), "source": "product_jsonld" if metadata else "search_result"})
    return out

@app.get("/health")
def health():
    return {"ok": True, "search_configured": bool(os.getenv("SEARXNG_URL")), "provider": "searxng"}

@app.get("/suppliers")
def suppliers():
    return [{"name": name, "domain": domain} for name, domain in SUPPLIERS.items()]

@app.get("/search")
def search(q: str = Query(min_length=2, max_length=120)):
    searxng_url = os.getenv("SEARXNG_URL", "").strip()
    if not searxng_url:
        raise HTTPException(503, "Set SEARXNG_URL to the internal SearXNG service address.")
    results, errors = [], []
    with ThreadPoolExecutor(max_workers=5) as pool:
        futures = {pool.submit(search_supplier, name, domain, q, searxng_url): name for name, domain in SUPPLIERS.items()}
        for future, name in futures.items():
            try: results.extend(future.result(timeout=30))
            except Exception as exc: errors.append(name + ": " + str(exc)[:120])
    def price_key(x):
        p = x.get("price")
        return (p is None or not re.fullmatch(r"\d+(\.\d+)?", str(p)), float(p) if p is not None and re.fullmatch(r"\d+(\.\d+)?", str(p)) else float("inf"))
    results.sort(key=price_key)
    return {"query": q, "results": results, "errors": errors, "updated_at": int(time.time()),
            "notice": "Prices and stock only appear when supplier product metadata provides them; unknown is not out of stock."}
