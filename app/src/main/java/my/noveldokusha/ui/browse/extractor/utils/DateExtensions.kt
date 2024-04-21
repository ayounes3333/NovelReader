package my.noveldokusha.ui.browse.extractor.utils

import java.text.SimpleDateFormat
import java.util.*

val Date.withinTheYear: Boolean
    get() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_YEAR, 1)
        calendar.clear(Calendar.HOUR_OF_DAY)
        calendar.clear(Calendar.MINUTE)
        calendar.clear(Calendar.SECOND)
        calendar.clear(Calendar.MILLISECOND)
        return this.before(calendar.time)
    }

val Date.withinTheMonth: Boolean
    get() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.clear(Calendar.HOUR_OF_DAY)
        calendar.clear(Calendar.MINUTE)
        calendar.clear(Calendar.SECOND)
        calendar.clear(Calendar.MILLISECOND)
        return this.before(calendar.time)
    }

val Date.withinTheWeek: Boolean
    get() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, 1)
        calendar.clear(Calendar.HOUR_OF_DAY)
        calendar.clear(Calendar.MINUTE)
        calendar.clear(Calendar.SECOND)
        calendar.clear(Calendar.MILLISECOND)
        return this.before(calendar.time)
    }

val Date.withinTheDay: Boolean
    get() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 1)
        calendar.clear(Calendar.MINUTE)
        calendar.clear(Calendar.SECOND)
        calendar.clear(Calendar.MILLISECOND)
        return this.before(calendar.time)
    }

val Date.shortFormatted: String
    get() = when {
        withinTheYear -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(this)
        else -> SimpleDateFormat("MMM dd yyyy", Locale.getDefault()).format(this)
    }