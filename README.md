# Balloon Price Finder v1.0

Native Kotlin Android app for balloon supplier discovery and comparison of **imported** catalog prices.

## Features
- Search supplier websites: A2Z Balloons, Bargain Balloons, Balloons Online, LA Balloons, Burton + Burton.
- Import supplier offers from CSV using Android's file picker.
- Filter offers by all search words and sort by lowest **price per balloon**.
- Display bag price, quantity, brand, size, color, and supplier.
- Open product links in the browser for ordering.

## Important limitations
- The app **does not scrape supplier websites**, query live prices, or automatically aggregate web search results.
- The supplier buttons use Google `site:` searches and may show incomplete or stale results.
- CSV prices are user-provided and may be outdated. Shipping, taxes, discounts, stock, and minimum orders are not included.
- Imported catalog data is kept in memory only and is lost when the app closes. Persistent catalogs are a future enhancement.
- The CSV parser supports quoted commas and escaped quotes but not multiline quoted fields.

## CSV format
Use `sample_catalog.csv` as a template. Required columns: `supplier,name,brand,size,color,quantity,price,url`. Quantity must be positive; price is a nonnegative USD bag price; URL must be HTTPS or blank. Add a separate row per supplier product. Example format (illustrative, **not a real offer**):

```csv
supplier,name,brand,size,color,quantity,price,url
Example Supplier,260 Red Balloons,Example Brand,260,Red,100,12.99,https://example.com/product
```

## Build
1. Open this folder in Android Studio (JDK 17, Android SDK 35).
2. Wait for Gradle sync; use **Build > Build APK(s)**.
3. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub Actions
Push the **contents of this folder** to a GitHub repository's root (not a nested folder). Go to **Actions > Build Android APK > Run workflow** or push to `main`/`master`. Download the `BalloonPriceFinder-v1-debug-apk` artifact. The APK is debug-signed for testing, not Play Store release.

## Next steps for live comparison
Obtain authorized product feeds/APIs from suppliers, normalize UPC/SKU, color, size, count, and brand, refresh prices with timestamps, and include shipping/discounts for total-cost comparison.
