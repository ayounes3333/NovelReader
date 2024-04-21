package my.noveldokusha.features.reader.settingDialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusWeak
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.NavigateBefore
import androidx.compose.material.icons.rounded.NavigateNext
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayForWork
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import my.noveldokusha.composableActions.debouncedAction
import my.noveldokusha.features.reader.features.TextToSpeechSettingData
import my.noveldokusha.ui.theme.ColorAccent


@OptIn(ExperimentalAnimationApi::class, ExperimentalLayoutApi::class)
@Composable
fun VoiceReaderDialog(
    state: TextToSpeechSettingData
) {
    var openSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var openReplacementsDialog by rememberSaveable { mutableStateOf(false) }

    Column {
        AnimatedVisibility(visible = state.isLoadingChapter.value) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    strokeWidth = 6.dp,
                    color = ColorAccent,
                    modifier = Modifier.background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        CircleShape
                    )
                )
            }
        }
        ElevatedCard(
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(16.dp)
            ) {

                // Player playback buttons
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val alpha by animateFloatAsState(
                        targetValue = if (state.isThereActiveItem.value) 1f else 0.5f,
                        label = ""
                    )
                    IconButton(
                        onClick = debouncedAction(waitMillis = 100) { state.playFirstVisibleItem() },
                        enabled = state.isThereActiveItem.value,
                        modifier = Modifier.alpha(alpha).padding(end = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayForWork,
                            contentDescription = null,
                            modifier = Modifier
                                .size(36.dp)
                                .background(ColorAccent, CircleShape),
                            tint = Color.White,
                        )
                    }
                    IconButton(
                        onClick = debouncedAction(waitMillis = 1000) { state.playPreviousChapter() },
                        enabled = state.isThereActiveItem.value,
                        modifier = Modifier.alpha(alpha),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FastRewind,
                            contentDescription = null,
                            modifier = Modifier
                                .size(32.dp)
                                .background(ColorAccent, CircleShape),
                            tint = Color.White,
                        )
                    }
                    IconButton(
                        onClick = debouncedAction(waitMillis = 100) { state.playPreviousItem() },
                        enabled = state.isThereActiveItem.value,
                        modifier = Modifier.alpha(alpha),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NavigateBefore,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(38.dp)
                                .background(ColorAccent, CircleShape),
                        )
                    }
                    IconButton(onClick = { state.setPlaying(!state.isPlaying.value) }) {
                        AnimatedContent(
                            targetState = state.isPlaying.value,
                            modifier = Modifier
                                .size(56.dp)
                                .background(ColorAccent, CircleShape), label = ""
                        ) { target ->
                            when (target) {
                                true -> Icon(
                                    Icons.Rounded.Pause,
                                    contentDescription = null,
                                    tint = Color.White,
                                )

                                false -> Icon(
                                    Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = debouncedAction(waitMillis = 100) { state.playNextItem() },
                        enabled = state.isThereActiveItem.value,
                        modifier = Modifier.alpha(alpha),
                    ) {
                        Icon(
                            Icons.Rounded.NavigateNext,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(38.dp)
                                .background(ColorAccent, CircleShape),
                        )
                    }
                    IconButton(
                        onClick = debouncedAction(waitMillis = 1000) { state.playNextChapter() },
                        enabled = state.isThereActiveItem.value,
                        modifier = Modifier.alpha(alpha),
                    ) {
                        Icon(
                            Icons.Rounded.FastForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(32.dp)
                                .background(ColorAccent, CircleShape),
                        )
                    }

                    IconButton(
                        onClick = debouncedAction(waitMillis = 100) { state.scrollToActiveItem() },
                        enabled = state.isThereActiveItem.value,
                        modifier = Modifier.alpha(alpha).padding(start = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CenterFocusWeak,
                            contentDescription = null,
                            modifier = Modifier
                                .size(36.dp)
                                .background(ColorAccent, CircleShape),
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}