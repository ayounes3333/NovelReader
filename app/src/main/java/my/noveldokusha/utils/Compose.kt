package my.noveldokusha.utils

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

@Composable
fun <T> rememberMutableStateOf(value: T) = remember { mutableStateOf(value) }

@Composable
fun OnLifecycleEvent(onEvent: (owner: LifecycleOwner, event: Lifecycle.Event) -> Unit) {
    val eventHandler = rememberUpdatedState(onEvent)
    val lifecycleOwner = rememberUpdatedState(LocalLifecycleOwner.current)

    DisposableEffect(lifecycleOwner.value) {
        val lifecycle = lifecycleOwner.value.lifecycle
        val observer = LifecycleEventObserver { owner, event ->
            eventHandler.value(owner, event)
        }

        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }
}

@Composable
fun LazyListState.isAtTop(threshold: Dp) = run {

    val density by rememberUpdatedState(LocalDensity.current)

    return@run remember {
        derivedStateOf {
            if (firstVisibleItemIndex > 0) return@derivedStateOf false
            val item = layoutInfo.visibleItemsInfo.firstOrNull()
                ?: return@derivedStateOf true
            with(density) { item.offset.toDp() } > -threshold
        }
    }
}

@Composable
fun ScrollState.isAtTop(threshold: Dp) = run {

    val density by rememberUpdatedState(LocalDensity.current)

    return@run remember(threshold) {
        derivedStateOf {
            val valueDp = with(density) { value.toDp() }
            valueDp < threshold
        }
    }
}



