package io.rmiri.skeleton

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.widget.Space
import io.rmiri.skeleton.master.SkeletonMaster

/**
 * Created by Rasoul Miri on 8/8/17.
 */
class SkeletonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SkeletonMaster(context, attrs, defStyleAttr) {
    init {
        addView(Space(context))
        if (isInEditMode) {
            setBackgroundColor(Color.LTGRAY)
        }
    }
}