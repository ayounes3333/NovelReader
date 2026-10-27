package my.noveldoksuha.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import my.noveldokusha.tooling.ai_engine.domain.AiUsage
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class AiUsageDayEntry(
    val dayEpoch: Long = 0,
    val requests: Int = 0,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
) {
    operator fun plus(usage: AiUsage) = AiUsageDayEntry(
        dayEpoch = dayEpoch,
        requests = requests + 1,
        promptTokens = promptTokens + usage.promptTokens,
        completionTokens = completionTokens + usage.completionTokens,
        totalTokens = totalTokens + usage.totalTokens,
    )
}

data class AiUsageSummary(
    val requests: Int,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
)

/**
 * Local token accounting from provider-reported usage. Entries are kept per
 * day (pruned to the last [MAX_DAYS]); API keys and contents are never stored.
 */
@Singleton
class AiUsageStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val preferences by lazy {
        context.getSharedPreferences("ai_usage", Context.MODE_PRIVATE)
    }

    private val gson = Gson()
    private val type = object : TypeToken<Map<Long, AiUsageDayEntry>>() {}.type

    @Synchronized
    fun add(usage: AiUsage?) {
        val key = todayKey()
        val entries = load().toMutableMap()
        val entry = entries[key] ?: AiUsageDayEntry(dayEpoch = key)
        entries[key] = entry.plus(usage ?: AiUsage(0, 0, 0))
        prune(entries)
        preferences.edit().putString(KEY, gson.toJson(entries)).apply()
    }

    fun summary(lastDays: Int = 30): AiUsageSummary {
        val from = todayKey() - lastDays + 1
        val relevant = load().values.filter { it.dayEpoch >= from }
        return AiUsageSummary(
            requests = relevant.sumOf { it.requests },
            promptTokens = relevant.sumOf { it.promptTokens },
            completionTokens = relevant.sumOf { it.completionTokens },
            totalTokens = relevant.sumOf { it.totalTokens },
        )
    }

    private fun load(): Map<Long, AiUsageDayEntry> = runCatching {
        gson.fromJson<Map<Long, AiUsageDayEntry>>(
            preferences.getString(KEY, null),
            type
        )
    }.getOrNull().orEmpty()

    private fun prune(entries: MutableMap<Long, AiUsageDayEntry>) {
        val threshold = todayKey() - MAX_DAYS
        entries.keys.removeAll { it < threshold }
    }

    private fun todayKey(): Long = LocalDate.now().toEpochDay()

    private companion object {
        const val KEY = "daily_usage"
        const val MAX_DAYS = 90L
    }
}
