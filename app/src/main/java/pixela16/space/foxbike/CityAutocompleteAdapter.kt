package pixela16.space.foxbike

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.Filterable
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

class CityAutocompleteAdapter(context: Context) : ArrayAdapter<String>(context, android.R.layout.simple_dropdown_item_1line), Filterable {
    private var results: List<String> = arrayListOf()

    override fun getCount(): Int = results.size
    override fun getItem(index: Int): String? = if (index < results.size) results[index] else null

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val filterResults = FilterResults()
                if (constraint != null && constraint.length >= 2) {
                    val suggestions = fetchCitySuggestions(constraint.toString())
                    filterResults.values = suggestions
                    filterResults.count = suggestions.size
                }
                return filterResults
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, filterResults: FilterResults?) {
                if (filterResults != null && filterResults.count > 0) {
                    results = filterResults.values as List<String>
                    notifyDataSetChanged()
                } else {
                    notifyDataSetInvalidated()
                }
            }
        }
    }

    private fun fetchCitySuggestions(query: String): List<String> {
        val list = mutableListOf<String>()
        try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val url = URL("https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&addressdetails=1&limit=5")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "FoxBike-Android-App-v3")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            
            val response = conn.inputStream.bufferedReader().readText()
            val jsonArray = JSONArray(response)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(obj.getString("display_name"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
