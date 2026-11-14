# Balloon Price Finder v2

Android app with in-app multi-supplier product search and a companion FastAPI service.

- Search A2Z Balloons, Bargain Balloons, Balloons Online, LA Balloons and Burton + Burton.
- Show price and stock **only when public product structured data provides them**; otherwise show price unavailable / stock unknown.
- Tap to open original supplier product page to order.
- Import CSV catalogs for offline price comparison (imported offers are not live).
- The Android app requires a separately deployed HTTPS API; it is **not preconfigured or hosted** by GitHub.

## Getting live search working
See [API setup](api/README.md). Google API credentials and an HTTPS host are required. The Android APK alone cannot provide the live multi-store feed.

## APK
GitHub Actions > Build Android APK, or Android Studio JDK 17 / SDK 35.
