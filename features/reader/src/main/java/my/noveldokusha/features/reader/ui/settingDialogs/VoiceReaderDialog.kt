package my.noveldokusha.features.reader.ui.settingDialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
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
import my.noveldoksuha.coreui.composableActions.debouncedAction
import my.noveldoksuha.coreui.theme.ColorAccent
import my.noveldoksuha.coreui.theme.ColorAccentContent
import my.noveldokusha.features.reader.features.TextToSpeechSettingData
import my.noveldoksuha.coreui.theme.AppElevation


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
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = AppElevation.dialog)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(16.dp)
            ) {

                // Player playback buttons: fixed sizes, evenly distributed
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val enabled = state.isThereActiveItem.value
                    val alpha by animateFloatAsState(
                        targetValue = if (enabled) 1f else 0.5f,
                        label = ""
                    )
                    PlayerButton(
                        icon = Icons.Rounded.PlayForWork,
                        size = 36.dp,
                        enabled = enabled,
                        alpha = alpha,
                        onClick = debouncedAction(waitMillis = 100) { state.playFirstVisibleItem() },
                    )
                    PlayerButton(
                        icon = Icons.Rounded.FastRewind,
                        size = 34.dp,
                        enabled = enabled,
                        alpha = alpha,
                        onClick = debouncedAction(waitMillis = 1000) { state.playPreviousChapter() },
                    )
                    PlayerButton(
                        icon = Icons.Rounded.NavigateBefore,
                        size = 40.dp,
                        enabled = enabled,
                        alpha = alpha,
                        onClick = debouncedAction(waitMillis = 100) { state.playPreviousItem() },
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .wrapContentWidth()
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(ColorAccent)
                            .clickable { state.setPlaying(!state.isPlaying.value) }
                    ) {
                        AnimatedContent(targetState = state.isPlaying.value, label = "") { target ->
                            Icon(
                                imageVector = if (target) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = ColorAccentContent,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    PlayerButton(
                        icon = Icons.Rounded.NavigateNext,
                        size = 40.dp,
                        enabled = enabled,
                        alpha = alpha,
                        onClick = debouncedAction(waitMillis = 100) { state.playNextItem() },
                    )
                    PlayerButton(
                        icon = Icons.Rounded.FastForward,
                        size = 34.dp,
                        enabled = enabled,
                        alpha = alpha,
                        onClick = debouncedAction(waitMillis = 1000) { state.playNextChapter() },
                    )
                    PlayerButton(
                        icon = Icons.Rounded.CenterFocusWeak,
                        size = 36.dp,
                        enabled = enabled,
                        alpha = alpha,
                        onClick = debouncedAction(waitMillis = 100) { state.scrollToActiveItem() },
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.PlayerButton(
    icon: ImageVector,
    size: Dp,
    enabled: Boolean,
    alpha: Float,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(1f)
            .wrapContentWidth()
            .alpha(alpha)
            .size(size)
            .clip(CircleShape)
            .background(ColorAccent)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ColorAccentContent,
            modifier = Modifier.size(size * 0.65f)
        )
    }
}
