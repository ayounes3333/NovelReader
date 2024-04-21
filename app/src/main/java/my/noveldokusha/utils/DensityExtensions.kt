package my.noveldokusha.utils

import android.content.res.Resources
import kotlin.math.roundToInt

val Int.dpToPx : Int
    get() = (this * Resources.getSystem().displayMetrics.density).roundToInt()

val Int.pxToDp : Int
    get() = (this / Resources.getSystem().displayMetrics.density).roundToInt()

val Int.spToPx : Int
    get() = (this * Resources.getSystem().displayMetrics.scaledDensity).roundToInt()