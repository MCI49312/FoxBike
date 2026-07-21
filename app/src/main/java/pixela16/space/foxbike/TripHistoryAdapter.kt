package pixela16.space.foxbike

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.json.JSONArray
import java.util.Locale

class TripHistoryAdapter(
    context: Context,
    private val tripsArray: JSONArray,
    private val onDelete: (Int) -> Unit,
    private val onClick: (Int) -> Unit
) : ArrayAdapter<String>(context, R.layout.item_trip_history) {

    override fun getCount(): Int = tripsArray.length()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_trip_history, parent, false)
        
        // Items are in reverse chronological order (latest first)
        val index = tripsArray.length() - 1 - position
        val trip = tripsArray.getJSONObject(index)
        
        val sdf = java.text.SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
        val date = sdf.format(java.util.Date(trip.getLong("date")))
        
        val prefs = context.getSharedPreferences("FoxBikePrefs", Context.MODE_PRIVATE)
        val isKmh = prefs.getBoolean("isKmh", true)
        val factor = if (isKmh) 0.001f else 0.000621371f
        val unit = if (isKmh) "km" else "mi"
        val dist = trip.getDouble("distance").toFloat()
        
        val tvInfo = view.findViewById<TextView>(R.id.tvTripInfo)
        tvInfo.text = "$date - ${String.format(Locale.getDefault(), "%.2f %s", dist * factor, unit)}"
        
        view.setOnClickListener { onClick(index) }
        
        view.findViewById<ImageButton>(R.id.btnDeleteTrip).setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle(R.string.delete_trip)
                .setMessage(R.string.confirm_delete_trip)
                .setPositiveButton(R.string.yes) { _, _ -> onDelete(index) }
                .setNegativeButton(R.string.no, null)
                .show()
        }
        
        return view
    }
}
