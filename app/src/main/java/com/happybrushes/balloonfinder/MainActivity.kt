package com.happybrushes.balloonfinder

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.view.View
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

class MainActivity : Activity() {
    data class Offer(val supplier: String, val name: String, val brand: String, val size: String, val color: String, val quantity: Int, val price: Double, val url: String) {
        val unitPrice get() = price / quantity
    }
    private val suppliers = linkedMapOf(
        "A2Z Balloons" to "a2zballoons.com",
        "Bargain Balloons" to "bargainballoons.com",
        "Balloons Online" to "balloonsonline.com",
        "LA Balloons" to "laballoons.com",
        "Burton + Burton" to "burtonandburton.com"
    )
    private val offers = mutableListOf<Offer>()
    private lateinit var search: EditText
    private lateinit var apiUrl: EditText
    private lateinit var results: LinearLayout
    private lateinit var summary: TextView
    private val blue = Color.rgb(24, 92, 182)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 24, 20, 12); setBackgroundColor(Color.rgb(247,249,253)) }
        root.addView(TextView(this).apply { text = "🎈 Balloon Price Finder v3"; textSize = 24f; setTextColor(blue); setPadding(0,0,0,8) })
        root.addView(label("Free balloon searches open supplier results in your browser. In-app prices require a connected server.", 13f))
        search = EditText(this).apply { hint = "Sempertex 260 red"; setSingleLine(true) }
        root.addView(search)
        apiUrl = EditText(this).apply {
            hint = "API server URL (https://your-server.example)"
            setSingleLine(true)
            setText(getPreferences(MODE_PRIVATE).getString("api_url", ""))
            textSize = 13f
        }
        root.addView(apiUrl)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun action(label: String, click: () -> Unit) {
            actions.addView(Button(this).apply { text = label; textSize = 11f; isAllCaps = false; setOnClickListener { click() } },
                LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        action("Search balloons FREE (Google)") { googleSearch() }
        action("Search all suppliers (browser)") { supplierBrowserSearch() }
        action("Search in app (server required)") { searchWeb() }
        action("CSV prices") { renderOffers() }
        action("Import CSV") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }, 42) }
        root.addView(actions)
        summary = label("", 13f)
        root.addView(summary)
        val scroll = ScrollView(this)
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(results)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        renderOffers()
    }
    private fun label(value: String, size: Float = 16f): TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(Color.rgb(30,40,60)); setPadding(6,10,6,10)
    }
    private fun open(url: String) {
        if (!url.startsWith("https://")) return
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: Exception) { Toast.makeText(this, "Cannot open product", Toast.LENGTH_SHORT).show() }
    }
    private fun supplierBrowserSearch() {
        val q = search.text.toString().trim()
        if (q.length < 2) { Toast.makeText(this, "Enter a balloon name", Toast.LENGTH_SHORT).show(); return }
        val sites = suppliers.values.joinToString(" OR ") { "site:" + it }
        summary.text = "Opening supplier results in your browser. Prices and stock must be checked on each store page."
        open("https://www.google.com/search?q=" + URLEncoder.encode("(" + sites + ") " + q, "UTF-8"))
    }
    private fun googleSearch() {
        val q = search.text.toString().trim()
        if (q.length < 2) { Toast.makeText(this, "Enter a balloon name", Toast.LENGTH_SHORT).show(); return }
        summary.text = "Opening your custom Google search engine in the browser. If it shows no results, use Search all suppliers (browser)."
        open("https://cse.google.com/cse?cx=263954e4ff7544c90&q=" + URLEncoder.encode(q, "UTF-8"))
    }
    private fun searchWeb() {
        val q = search.text.toString().trim()
        if (q.length < 2) { Toast.makeText(this, "Enter a balloon name", Toast.LENGTH_SHORT).show(); return }
        val base = apiUrl.text.toString().trim().trimEnd('/')
        if (!base.startsWith("https://")) {
            summary.text = "In-app search needs a deployed HTTPS server. Use Search balloons FREE or Search all suppliers to search without a server."
            return
        }
        getPreferences(MODE_PRIVATE).edit().putString("api_url", base).apply()
        summary.text = "Searching supplier websites…"
        results.removeAllViews()
        Thread {
            try {
                val endpoint = URL(base + "/search?q=" + URLEncoder.encode(q, "UTF-8"))
                val connection = endpoint.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 45000
                try {
                    val status = connection.responseCode
                    val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                    val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
                    if (status !in 200..299) throw IllegalStateException("API HTTP " + status + ": " + body.take(220))
                    val data = JSONObject(body)
                    runOnUiThread { renderWebResults(data) }
                } finally { connection.disconnect() }
            } catch (e: Exception) {
                runOnUiThread { summary.text = "Search failed: " + (e.message ?: "Network error"); results.addView(label("Check the API URL, server configuration, and internet connection.", 13f)) }
            }
        }.start()
    }
    private fun renderWebResults(data: JSONObject) {
        results.removeAllViews()
        val items = data.optJSONArray("results")
        val count = items?.length() ?: 0
        summary.text = count.toString() + " web matches. Prices and stock shown only when verified on product pages."
        if (count == 0) results.addView(label("No products found. Try another balloon description.", 14f))
        for (i in 0 until count) {
            val item = items!!.optJSONObject(i) ?: continue
            val supplier = item.optString("supplier")
            val name = item.optString("name")
            val price = if (item.isNull("price")) null else item.optString("price").takeIf { it.isNotBlank() }
            val currency = item.optString("currency", "USD")
            val stock = when (item.optString("stock")) {
                "in_stock" -> "In stock"
                "out_of_stock" -> "Out of stock"
                else -> "Stock unknown"
            }
            val priceText = if (price == null) "Price unavailable" else price + " " + currency
            val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(14,12,14,12); setBackgroundColor(Color.WHITE) }
            card.addView(label(name, 17f))
            card.addView(label(supplier + " • " + priceText + " • " + stock, 14f))
            card.addView(Button(this).apply { text = "View product / order"; isAllCaps = false; setOnClickListener { open(item.optString("url")) } })
            results.addView(card)
            results.addView(View(this).apply { setBackgroundColor(Color.rgb(230,234,242)) }, LinearLayout.LayoutParams(-1, 6))
        }
        val errors = data.optJSONArray("errors")
        if (errors != null && errors.length() > 0) results.addView(label("Some suppliers could not be searched. Results may be incomplete.", 13f))
    }
    private fun renderOffers() {
        results.removeAllViews()
        val terms = search.text.toString().trim().lowercase(Locale.US).split(Regex("\\s+")).filter { it.isNotBlank() }
        val matched = offers.filter { o ->
            val hay = (o.supplier + " " + o.name + " " + o.brand + " " + o.size + " " + o.color).lowercase(Locale.US)
            terms.all { hay.contains(it) }
        }.sortedBy { it.unitPrice }
        summary.text = matched.size.toString() + " imported catalog matches. Use Search web for live product metadata."
        if (offers.isEmpty()) results.addView(label("No CSV catalog imported. Use the free browser search buttons above. In-app price/stock results require an API server.", 14f))
        matched.forEach { o ->
            val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(14,12,14,12); setBackgroundColor(Color.WHITE) }
            card.addView(label(o.name + " — " + o.supplier, 17f))
            card.addView(label(o.brand + " • " + o.size + " • " + o.color + "\n" + o.quantity + " pcs • $" + "%.2f".format(Locale.US,o.price) + " / bag • $" + "%.4f".format(Locale.US,o.unitPrice) + " each", 14f))
            if (o.url.startsWith("https://")) card.addView(Button(this).apply { text = "View product / order"; setOnClickListener { open(o.url) } })
            results.addView(card)
        }
        results.addView(label("DIRECT SUPPLIER SEARCH", 17f))
        suppliers.forEach { (name, domain) ->
            results.addView(Button(this).apply { text = "Search " + name; isAllCaps = false; setOnClickListener {
                open("https://www.google.com/search?q=" + URLEncoder.encode("site:" + domain + " " + search.text.toString() + " balloons", "UTF-8"))
            } })
        }
    }
    @Deprecated("Legacy activity result used to keep project dependency-free")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 42 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val csv = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return
            val lines = csv.lines().filter { it.isNotBlank() }
            require(lines.isNotEmpty()) { "CSV is empty" }
            val headers = parseCsv(lines.first()).map { it.trim().lowercase(Locale.US).removePrefix("\uFEFF") }
            val required = listOf("supplier", "name", "brand", "size", "color", "quantity", "price", "url")
            require(required.all { it in headers }) { "Missing headers" }
            var count = 0
            lines.drop(1).forEach { line ->
                try {
                    val cols = parseCsv(line)
                    fun col(key: String) = cols[headers.indexOf(key)].trim()
                    val quantity = col("quantity").toInt()
                    val price = col("price").removePrefix("$").toDouble()
                    require(quantity > 0 && price >= 0 && price.isFinite())
                    val url = col("url")
                    require(url.isEmpty() || url.startsWith("https://"))
                    offers.add(Offer(col("supplier"), col("name"), col("brand"), col("size"), col("color"), quantity, price, url))
                    count++
                } catch (_: Exception) {}
            }
            Toast.makeText(this, "Imported " + count + " offers", Toast.LENGTH_LONG).show()
            renderOffers()
        } catch (e: Exception) { Toast.makeText(this, "CSV import failed: " + e.message, Toast.LENGTH_LONG).show() }
    }
    private fun parseCsv(line: String): List<String> {
        val fields = mutableListOf<String>(); val value = StringBuilder(); var quoted = false; var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && i+1 < line.length && line[i+1] == '"' -> { value.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { fields.add(value.toString()); value.clear() }
                else -> value.append(c)
            }
            i++
        }
        fields.add(value.toString()); return fields
    }
}
