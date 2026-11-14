package com.happybrushes.balloonfinder

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.view.View
import android.widget.*
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
    private lateinit var results: LinearLayout
    private lateinit var summary: TextView
    private val blue = Color.rgb(24, 92, 182)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 24, 20, 12); setBackgroundColor(Color.rgb(247,249,253)) }
        val title = TextView(this).apply { text = "🎈 Balloon Price Finder"; textSize = 25f; setTextColor(blue); setPadding(0,0,0,8) }
        root.addView(title)
        root.addView(TextView(this).apply { text = "Compare catalog prices • Search supplier websites"; textSize = 14f; setTextColor(Color.DKGRAY) })
        search = EditText(this).apply { hint = "Search: Sempertex 260 red, 5 inch..."; setSingleLine(true) }
        root.addView(search)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun action(label: String, click: () -> Unit) { val b = Button(this).apply { text = label; textSize = 12f; setOnClickListener { click() } }; actions.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)) }
        action("Compare prices") { renderOffers() }
        action("Import CSV") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }, 42) }
        root.addView(actions)
        summary = TextView(this).apply { textSize = 14f; setPadding(0,12,0,12); setTextColor(Color.DKGRAY) }
        root.addView(summary)
        val scroll = ScrollView(this)
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(results)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        renderOffers()
    }
    private fun label(text: String, size: Float = 16f): TextView = TextView(this).apply { this.text = text; textSize = size; setTextColor(Color.rgb(30,40,60)); setPadding(6,10,6,10) }
    private fun open(url: String) { try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) { Toast.makeText(this, "Cannot open link", Toast.LENGTH_SHORT).show() } }
    private fun renderOffers() {
        results.removeAllViews()
        val query = search.text.toString().trim()
        val terms = query.lowercase(Locale.US).split(Regex("\\s+")).filter { it.isNotBlank() }
        val matched = offers.filter { o -> val hay = "${o.supplier} ${o.name} ${o.brand} ${o.size} ${o.color}".lowercase(Locale.US); terms.all { hay.contains(it) } }.sortedBy { it.unitPrice }
        summary.text = "${matched.size} matching catalog offers • ${offers.size} imported total. Prices are from imported CSVs, not live quotes."
        if (offers.isEmpty()) results.addView(label("No supplier prices loaded yet. Import a CSV catalog to compare prices. You can search all five supplier websites below.", 14f))
        else if (matched.isEmpty()) results.addView(label("No matching imported offers. Try fewer keywords or search supplier websites below.", 14f))
        matched.forEach { o ->
            val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(14,12,14,12); setBackgroundColor(Color.WHITE) }
            card.addView(label("${o.name} — ${o.supplier}", 17f))
            card.addView(label("${o.brand} • ${o.size} • ${o.color}\n${o.quantity} pcs • $${"%.2f".format(Locale.US,o.price)} / bag • $${"%.4f".format(Locale.US,o.unitPrice)} each", 14f))
            if (o.url.startsWith("https://")) card.addView(Button(this).apply { text = "View product / order"; setOnClickListener { open(o.url) } })
            results.addView(card)
            results.addView(View(this).apply { setBackgroundColor(Color.rgb(230,234,242)) }, LinearLayout.LayoutParams(-1, 6))
        }
        results.addView(label("SEARCH SUPPLIER WEBSITES", 17f))
        suppliers.forEach { (name, domain) ->
            results.addView(Button(this).apply { text = "Search $name"; isAllCaps = false; setOnClickListener {
                val q = URLEncoder.encode("site:$domain $query balloons", "UTF-8")
                open("https://www.google.com/search?q=$q")
            } })
        }
        results.addView(label("Website searches open Google results restricted to each supplier. They are not automatically collected or price-ranked. Checkout happens on the supplier's site.", 12f))
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
            require(required.all { it in headers }) { "Missing headers. Required: ${required.joinToString()}" }
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
                    offers.add(Offer(col("supplier"),col("name"),col("brand"),col("size"),col("color"),quantity,price,url))
                    count++
                } catch (_: Exception) { }
            }
            Toast.makeText(this, "Imported $count offers", Toast.LENGTH_LONG).show()
            renderOffers()
        } catch (e: Exception) { Toast.makeText(this, "CSV import failed: ${e.message}", Toast.LENGTH_LONG).show() }
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
