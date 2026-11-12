package my.noveldoksuha.coreui.theme

import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope

interface ThemeProvider {

    fun followSystem(stateCoroutineScope: CoroutineScope): State<Boolean>

    fun currentTheme(stateCoroutineScope: CoroutineScope): State<Themes>

    fun accentColor(stateCoroutineScope: CoroutineScope): State<Color>
}
