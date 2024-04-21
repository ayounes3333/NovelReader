package my.noveldokusha.utils

import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

suspend fun <T> getOrFetch(cached: suspend () -> T?, server: suspend () -> T?) : T? {
    return cached().takeIf { it != null } ?: server()
}

suspend fun <T> getThenUpdateLiveData(cached: suspend () -> T?, server: suspend () -> T?) : LiveData<T?> {
    return liveData {
        emit(cached())
        emit(server())
    }
}

suspend fun <T> getThenUpdateFlow(cached: suspend () -> T?, server: suspend () -> T?) : Flow<T?> {
    return flow {
        emit(cached())
        emit(server())
    }
}