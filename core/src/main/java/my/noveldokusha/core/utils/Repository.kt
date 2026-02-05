package my.noveldokusha.core.utils

import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

suspend fun <T> getOrFetch(cached: suspend () -> T?, server: suspend () -> T?) : T? {
    return cached().takeIf { it != null } ?: server()
}

suspend fun <T> getThenUpdateLiveData(cached: suspend () -> T?, server: suspend () -> T?) : LiveData<T?> {
    return liveData(Dispatchers.IO) {
        emit(cached())
        emit(server())
    }
}

/**
 * Creates a flow that emits cached data first, then updated data from server.
 * Both operations are automatically executed on Dispatchers.IO for optimal performance
 * with database queries and network operations.
 *
 * @param cached Lambda to retrieve cached data (e.g., from database)
 * @param server Lambda to fetch fresh data (e.g., from network/file system)
 * @return Flow that emits cached data first (if available), then fresh data
 */
fun <T> getThenUpdateFlow(cached: suspend () -> T?, server: suspend () -> T?) : Flow<T?> {
    return flow {
        emit(cached())
        emit(server())
    }.flowOn(Dispatchers.IO)
}