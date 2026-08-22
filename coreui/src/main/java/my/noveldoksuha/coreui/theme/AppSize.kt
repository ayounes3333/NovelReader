package my.noveldoksuha.coreui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/**
 * Design-system spacing tokens. Use these instead of hardcoded dp values
 * so spacing stays consistent across every screen.
 */
object AppSpacing {
    /** 4.dp — tight inner spacing (badges, icon insets). */
    val xsmall = 4.dp

    /** 8.dp — default gap between sibling elements. */
    val small = 8.dp

    /** 12.dp — section inner padding. */
    val medium = 12.dp

    /** 16.dp — standard screen/content horizontal padding. */
    val large = 16.dp

    /** 24.dp — large separation between major blocks. */
    val xlarge = 24.dp

    /** Unified bottom padding for scrollable lists/grids so content clears bottom bars. */
    val scrollableContentBottomPadding = 300.dp

    /** Standard content padding for full-height lazy lists/grids. */
    val listContentPadding = PaddingValues(
        start = xsmall,
        end = xsmall,
        top = xsmall,
        bottom = scrollableContentBottomPadding,
    )
}

/**
 * Design-system elevation tokens. Use these instead of hardcoded elevations.
 */
object AppElevation {
    /** Flat surfaces. */
    val none = 0.dp

    /** Cards and list containers. */
    val card = 2.dp

    /** Dialogs and floating surfaces. */
    val dialog = 6.dp
}
