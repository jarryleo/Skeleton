package io.rmiri.skeleton

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.util.Log
import android.util.Pair
import android.view.View
import android.view.ViewGroup
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.core.view.isVisible
import io.rmiri.skeleton.master.SkeletonMaster
import io.rmiri.skeleton.master.SkeletonModel
import io.rmiri.skeleton.master.SkeletonModel.DEFAULT_COLOR_BACKGROUND_MAIN
import io.rmiri.skeleton.utils.ColorUtils
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.min


/**
 * Created by Rasoul Miri on 8/8/17.
 */
class SkeletonViewGroup : SkeletonMaster {
    private var skeletonModels: ArrayList<SkeletonModel>? =
        ArrayList() // Array for all children (add by java)
    private var parentId: Int = -1 // This use for loop for x,y real
    private var skeletonModelsChildren: ArrayList<SkeletonModel>? =
        null // Array for all children extend from SkeletonView (add by xml)
    private var skeletonListener: WeakReference<SkeletonListener>? = null // Listener
    private val mAnimatorListener by lazy {
        AnimatorListener(WeakReference(this))
    }
    private val mAnimatorUpdateListener by lazy {
        AnimatorUpdateListener(WeakReference(this))
    }
    // Variable for draw canvas
    private var paintGradient: Paint? = null
    private var paintBackgroundViews: Paint? = null
    private var canvasGradientElement: Canvas? = null
    private var canvasBackgroundElement: Canvas? = null
    private var bitmapGradientElement: Bitmap? = null
    private var bitmapBackgroundElement: Bitmap? = null
    private val path = Path()

    // Variable for gradient
    private lateinit var gradientColors: IntArray // Is 3 colors
    private var xStartGradient = 0f
    private var yStartGradient = 0f
    private var xEndGradient = 0f
    private var yEndGradient = 0f

    // 渐变着色器只构建一次，动画帧仅通过 Matrix 平移复用，避免每帧分配 native shader
    private var shaderGradient: LinearGradient? = null
    private var isShaderGradientDirty = true
    private val gradientMatrix = Matrix()

    // Variable for fraction animation played
    private var valueAnimator: ValueAnimator? = null
    private var animationFractionMove = 0.0f
    private var animationFraction = 0.0f

    // Variable for checking
    private var isCanDraw = false
    private var isCanDrawFinishState = false
    private var isAnimationPlay = false
    private var isLastLoopAnimation = false

    //优化子view尺寸变化 骨架屏没有变化问题
    private val onChildLayoutChangeListener =
        OnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (left != oldLeft || top != oldTop || right != oldRight || oldBottom != bottom) {
                if (v is SkeletonView) {
                    skeletonModel(v)
                    path.reset()
                    paintGradient = null
                    isShaderGradientDirty = true
                }
            }
        }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    constructor(
        context: Context,
        attrs: AttributeSet?,
        @AttrRes defStyleAttr: Int,
        @StyleRes defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun init(context: Context, attrs: AttributeSet?) {
        super.init(context, attrs)

        // Generate color 010 (color one transparent = 0.0 | color two transparent = 1.0 | color three transparent = 1.0 )
        gradientColors = ColorUtils.generateColorTransparent010(
            skeletonModel.colorHighLight,
            skeletonModel.colorBackgroundViews
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)

        if (changed) {
            skeletonModelsChildren = null
            path.reset()
            paintGradient = null
            isShaderGradientDirty = true
        }

        if (skeletonModelsChildren == null
            && skeletonModels != null
            && skeletonModels!!.size > 0
            && skeletonModel.isShowSkeleton
        ) {
            skeletonModelsChildren = ArrayList()

            // set id for parent if not id
            if (parent is ViewGroup) {
                parentId = (parent as ViewGroup).id
                if (parentId == -1) {
                    Log.e("SkeletonView", "Parent need to ID")
                    (parent as ViewGroup).id = generateViewId()
                    parentId = (parent as ViewGroup).id
                }
            }


            for (item in skeletonModels!!) {
                if (item.startView != null) {
                    item.childView = item.startView
                }

                // Set point X1,Y1,X2,Y2
                var X1 = item.childView.left.toFloat()
                var Y1 = item.childView.top.toFloat()
                var X2 = 0.0f
                var Y2 = 0.0f

                // Calculator X1,X2 to left and top SkeletonGroup view
                val pair = calculateXAndYReal(item.childView)
                X1 += pair.first!!
                Y1 += pair.second!!

                // Calculator padding size
                var pTop: Float
                var pLeft: Float
                var pRight: Float
                var pBottom: Float

                if (item.padding != Float.MIN_VALUE) {
                    pBottom = item.padding
                    pRight = pBottom
                    pLeft = pRight
                    pTop = pLeft
                } else {
                    pTop = if (item.paddingTop != Float.MIN_VALUE) item.paddingTop else 0f
                    pLeft = if (item.paddingLeft != Float.MIN_VALUE) item.paddingLeft else 0f
                    pRight = if (item.paddingRight != Float.MIN_VALUE) item.paddingRight else 0f
                    pBottom = if (item.paddingBottom != Float.MIN_VALUE) item.paddingBottom else 0f
                }

                if (item.isMatchViewBoolean) {
                    X2 = X1 + measuredWidth - pRight
                    Y2 = Y1 + measuredHeight - pBottom
                } else {
                    if (item.endView != null) {
                        // Use startView and endView instead of real childView width

                        val X1End = item.endView.left.toFloat()
                        val Y1End = item.endView.top.toFloat()

                        val pairXY = calculateXAndYReal(item.endView)
                        X2 = X1End + pairXY.first!! + item.endView.measuredWidth - pRight
                        Y2 = Y1End + pairXY.second!! + item.endView.measuredHeight - pBottom
                    } else if (item.customWidth != Float.MIN_VALUE) {
                        // Use customWidth and customHeight instead of real childView width and height
                        X2 = X1 + item.customWidth - pRight
                        Y2 = Y1 + item.customHeight - pBottom
                    } else {
                        X2 = X1 + (item.childView.measuredWidth) - pRight
                        Y2 = Y1 + (item.childView.measuredHeight) - pBottom
                    }
                }

                X1 += pLeft
                Y1 += pTop

                item.x1 = X1
                item.y1 = Y1
                item.x2 = X2
                item.y2 = Y2

                skeletonModelsChildren?.add(item)
            }
            if (skeletonModel.isAutoStartAnimation) {
                setupInitialAnimation()
            }
        } else if (skeletonModelsChildren == null && skeletonModel.isShowSkeleton) {
            // Add all children extend from SkeletonView
            skeletonModelsChildren = ArrayList()

            for (child in getAllChildren(getChildAt(0))) {
                if (child != null && child is SkeletonView) {
                    child.addOnLayoutChangeListener(onChildLayoutChangeListener)
                    val item = skeletonModel(child)
                    item.childView = child
                    skeletonModelsChildren?.add(item)
                }
            }
            if (skeletonModel.isAutoStartAnimation) {
                setupInitialAnimation()
            }
        }
    }

    private fun skeletonModel(child: SkeletonView): SkeletonModel {
        val item = child.skeletonModel

        // Set point X1,Y1,X2,Y2
        var X1 = child.getLeft().toFloat()
        var Y1 = child.getTop().toFloat()
        var X2 = 0.0f
        var Y2 = 0.0f

        // Calculator X1,X2 to left and top SkeletonGroup view
        var parent = child.parent as View
        while (parent !is SkeletonViewGroup) {
            X1 += parent.left.toFloat()
            Y1 += parent.top.toFloat()
            parent = parent.parent as View
        }

        // Calculator padding size
        var pTop: Float
        var pLeft: Float
        var pRight: Float
        var pBottom: Float

        if (item.padding.toInt() != Int.MIN_VALUE) {
            pBottom = item.padding
            pRight = pBottom
            pLeft = pRight
            pTop = pLeft
        } else {
            pTop = if (item.paddingTop.toInt() != Int.MIN_VALUE) item.paddingTop else 0f
            pLeft =
                if (item.paddingLeft.toInt() != Int.MIN_VALUE) item.paddingLeft else 0f
            pRight =
                if (item.paddingRight.toInt() != Int.MIN_VALUE) item.paddingRight else 0f
            pBottom =
                if (item.paddingBottom.toInt() != Int.MIN_VALUE) item.paddingBottom else 0f
        }

        X2 = X1 + child.getMeasuredWidth() - pRight
        Y2 = Y1 + child.getMeasuredHeight() - pBottom
        X1 = X1 + pLeft
        Y1 = Y1 + pTop

        item.x1 = X1
        item.y1 = Y1
        item.x2 = X2
        item.y2 = Y2
        return item
    }


    private fun calculateXAndYReal(view: View?): Pair<Float, Float> {
        if (view == null) {
            Log.i("SkeletonView", "view is null")
            return Pair(0f, 0f)
        }
        var X = 0.0f
        var Y = 0.0f

        // Calculator X1,Y1 to left and top SkeletonGroup view
        var parent = view.parent as View
        while (parent.id != parentId) {
            X += parent.left.toFloat()
            Y += parent.top.toFloat()
            parent = parent.parent as View
        }

        // remove padding left and top parent
        val parentViewMaster = (getParent() as View)
        X -= parentViewMaster.paddingLeft
        Y -= parentViewMaster.paddingTop


        return Pair(X, Y)
    }

    override fun dispatchDraw(canvas: Canvas) {
        // 骨架激活期间子View仅作布局占位(Space/无背景容器)没有可见内容，
        // 跳过整棵子树的绘制分发，避免每帧遍历几十个View
        if (isCanDraw) {
            // Just once run this code
            // Calculate bound child for draw

            if (paintGradient == null) {
                //paint

                //尺寸变化重进此分支时重置path，避免形状重复累积
                path.reset()
                paintBackgroundViews = Paint(Paint.ANTI_ALIAS_FLAG)
                paintBackgroundViews?.style = Paint.Style.FILL
                paintBackgroundViews?.color = skeletonModel.colorBackgroundViews
                paintGradient = Paint(Paint.ANTI_ALIAS_FLAG)
                paintGradient?.style = Paint.Style.FILL

                //canvas
                val bitmapConfig = Bitmap.Config.ALPHA_8
                bitmapBackgroundElement = Bitmap.createBitmap(width, height, bitmapConfig)
                canvasBackgroundElement = Canvas(bitmapBackgroundElement!!)
                bitmapGradientElement = Bitmap.createBitmap(width, height, bitmapConfig)
                canvasGradientElement = Canvas(bitmapGradientElement!!)

                for (i in skeletonModelsChildren!!.indices) {
                    val skeletonAttributeChild = skeletonModelsChildren!![i]
                    if (skeletonAttributeChild.childView?.isVisible == false) {
                        continue
                    }
                    if (skeletonAttributeChild.shapeType == SkeletonModel.SHAPE_TYPE_RECT) {
                        // Rectangle
                        val rectangleRect = RectF(
                            skeletonAttributeChild.x1,
                            skeletonAttributeChild.y1,
                            skeletonAttributeChild.x2,
                            skeletonAttributeChild.y2
                        )
                        val cornerRadius =
                            generateCornerRadius(rectangleRect, skeletonAttributeChild)
                        path.addRoundRect(rectangleRect, cornerRadius, Path.Direction.CW)
                    } else if (skeletonAttributeChild.shapeType == SkeletonModel.SHAPE_TYPE_OVAL) {
                        // Oval
                        val ovalRect = RectF(
                            skeletonAttributeChild.x1,
                            skeletonAttributeChild.y1,
                            skeletonAttributeChild.x2,
                            skeletonAttributeChild.y2
                        )
                        path.addOval(ovalRect, Path.Direction.CW)
                    } else if (skeletonAttributeChild.shapeType == SkeletonModel.SHAPE_TYPE_TEXT) {
                        // Text


                        val lineHeight = skeletonAttributeChild.textShapeLineHeight.toFloat()
                        val lineSpaceVertical =
                            skeletonAttributeChild.textShapeLineSpaceVertical.toFloat()

                        var lineNumber = skeletonAttributeChild.textShapeLineNumber
                        if (lineNumber == 0) {
                            lineNumber = ((skeletonAttributeChild.y2 - skeletonAttributeChild.y1)
                                    / (lineHeight + lineSpaceVertical)).toInt()
                        }
                        for (j in 0 until lineNumber) {
                            var rectangleRect: RectF
                            var newX1 = skeletonAttributeChild.x1
                            var newX2 = skeletonAttributeChild.x2

                            // Y1
                            val newY1 = if (j == 0) {
                                // First line
                                skeletonAttributeChild.y1
                            } else {
                                // Other line
                                (skeletonAttributeChild.y1
                                        + (lineHeight * j) // height children previous
                                        + (lineSpaceVertical * j)) // space vertical children previous
                            }

                            // Y2
                            val newY2 = (skeletonAttributeChild.y1
                                    + (lineHeight * (j + 1)) // height children previous
                                    + (lineSpaceVertical * j)) // space vertical children previous


                            // X1,X2 just for last line
                            if (j == lineNumber - 1) {
                                val quarterWidth =
                                    (skeletonAttributeChild.x2 - skeletonAttributeChild.x1) / 4 //quarter width 1 line

                                when (skeletonAttributeChild.textShapeLineLastWidth) {
                                    SkeletonModel.TEXT_SHAPE_LINE_LAST_WIDTH_FULL -> {}
                                    SkeletonModel.TEXT_SHAPE_LINE_LAST_WIDTH_THREE_QUARTERS -> if (skeletonModel.animationDirection == SkeletonModel.ANIMATION_DIRECTION_RTL) {
                                        newX1 = skeletonAttributeChild.x1 + quarterWidth
                                    } else {
                                        newX2 = skeletonAttributeChild.x2 - quarterWidth
                                    }

                                    SkeletonModel.TEXT_SHAPE_LINE_LAST_WIDTH_HALF -> if (skeletonModel.animationDirection == SkeletonModel.ANIMATION_DIRECTION_RTL) {
                                        newX1 = skeletonAttributeChild.x1 + (2 * quarterWidth)
                                    } else {
                                        newX2 = skeletonAttributeChild.x2 - (2 * quarterWidth)
                                    }

                                    SkeletonModel.TEXT_SHAPE_LINE_LAST_WIDTH_QUARTER -> if (skeletonModel.animationDirection == SkeletonModel.ANIMATION_DIRECTION_RTL) {
                                        newX1 = skeletonAttributeChild.x1 + (3 * quarterWidth)
                                    } else {
                                        newX2 = skeletonAttributeChild.x2 - (3 * quarterWidth)
                                    }
                                }
                            }

                            // Draw text line
                            rectangleRect = RectF(newX1, newY1, newX2, newY2)
                            val cornerRadius =
                                generateCornerRadius(rectangleRect, skeletonAttributeChild)
                            path.addRoundRect(rectangleRect, cornerRadius, Path.Direction.CW)
                        }
                    }


                    // Draw bitmap on canvas
                    canvasBackgroundElement?.drawPath(path, paintBackgroundViews!!)
                    canvasGradientElement?.drawPath(path, paintBackgroundViews!!)
                }
            }


            // Main draw
            if (isCanDrawFinishState) {
                // Finish draw
                when (skeletonModel.animationFinishType) {
                    SkeletonModel.ANIMATION_TYPE_ALPHA -> {
                        // Draw background total view with alpha
                        drawBackgroundTotal(
                            canvas,
                            ColorUtils.convertColorToTransparent(
                                skeletonModel.colorBackgroundMain,
                                1.0f - animationFraction
                            )
                        )

                        paintBackgroundViews?.alpha = (255 * (1.0f - animationFraction)).toInt()
                        canvas.drawBitmap(bitmapBackgroundElement!!, 0f, 0f, paintBackgroundViews)
                    }

                    SkeletonModel.ANIMATION_TYPE_GRADIENT -> {
                        // Draw background total view with alpha
                        // drawBackgroundTotal(canvas, ColorUtils.convertColorToTransparent(skeletonModel.getColorBackgroundMain(), 1.0f - AnimationFraction));
                        if (animationFractionMove == 1f || animationFractionMove == -1f) {
                            drawBackgroundTotal(
                                canvas,
                                skeletonModel.colorBackgroundMain
                            ) //draw background total
                            canvas.drawBitmap(
                                bitmapBackgroundElement!!,
                                0f,
                                0f,
                                paintBackgroundViews
                            ) //draw background view
                        }

                        generatePositionGradientFromDirectionAnimation()
                        applyGradientShader()

                        canvas.drawBitmap(bitmapGradientElement!!, 0f, 0f, paintGradient)
                    }
                }
            } else {
                // Normal draw

                drawBackgroundTotal(
                    canvas,
                    skeletonModel.colorBackgroundMain
                ) //draw background total
                canvas.drawBitmap(
                    bitmapBackgroundElement!!,
                    0f,
                    0f,
                    paintBackgroundViews
                ) //draw background view
                when (skeletonModel.animationNormalType) {
                    SkeletonModel.ANIMATION_TYPE_ALPHA -> {
                        paintGradient?.color = ColorUtils.convertColorToTransparent(
                            skeletonModel.colorHighLight, abs(animationFractionMove.toDouble())
                                .toFloat()
                        )

                        canvas.drawBitmap(bitmapGradientElement!!, 0f, 0f, paintGradient)
                    }

                    SkeletonModel.ANIMATION_TYPE_GRADIENT -> {
                        generatePositionGradientFromDirectionAnimation()
                        applyGradientShader()

                        canvas.drawBitmap(bitmapGradientElement!!, 0f, 0f, paintGradient)
                    }
                }
            }
        } else {
            super.dispatchDraw(canvas)
        }
    }


    private fun generateCornerRadius(
        rectangleRect: RectF,
        skeletonAttributeChild: SkeletonModel
    ): FloatArray {
        // Calculator cornerRadius size

        val cornerRadiusTopLeft: Float
        val cornerRadiusTopRight: Float
        val cornerRadiusBottomLRight: Float
        val cornerRadiusBottomLeft: Float

        if (skeletonAttributeChild.cornerRadius != Int.MIN_VALUE) {
            cornerRadiusBottomLeft =
                getCornerRadius(rectangleRect, skeletonAttributeChild.cornerRadius.toFloat())
            cornerRadiusBottomLRight = cornerRadiusBottomLeft
            cornerRadiusTopRight = cornerRadiusBottomLRight
            cornerRadiusTopLeft = cornerRadiusTopRight
        } else {
            cornerRadiusTopLeft =
                if (skeletonAttributeChild.cornerRadiusTopLeft != Int.MIN_VALUE) getCornerRadius(
                    rectangleRect,
                    skeletonAttributeChild.cornerRadiusTopLeft.toFloat()
                ) else 0f
            cornerRadiusTopRight =
                if (skeletonAttributeChild.cornerRadiusTopRight != Int.MIN_VALUE) getCornerRadius(
                    rectangleRect,
                    skeletonAttributeChild.cornerRadiusTopRight.toFloat()
                ) else 0f
            cornerRadiusBottomLRight =
                if (skeletonAttributeChild.cornerRadiusBottomLRight != Int.MIN_VALUE) getCornerRadius(
                    rectangleRect,
                    skeletonAttributeChild.cornerRadiusBottomLRight.toFloat()
                ) else 0f
            cornerRadiusBottomLeft =
                if (skeletonAttributeChild.cornerRadiusBottomLeft != Int.MIN_VALUE) getCornerRadius(
                    rectangleRect,
                    skeletonAttributeChild.cornerRadiusBottomLeft.toFloat()
                ) else 0f
        }

        return floatArrayOf(
            cornerRadiusTopLeft,
            cornerRadiusTopLeft,
            cornerRadiusTopRight,
            cornerRadiusTopRight,
            cornerRadiusBottomLRight,
            cornerRadiusBottomLRight,
            cornerRadiusBottomLeft,
            cornerRadiusBottomLeft
        )
    }

    private fun getCornerRadius(rectangleRect: RectF, initCornerRadius: Float): Float {
        return min(
            (min(
                rectangleRect.width().toDouble(),
                rectangleRect.height().toDouble()
            ) / 2f), initCornerRadius.toDouble()
        ).toFloat()
    }

    private fun drawBackgroundTotal(canvas: Canvas, color: Int) {
        // For transparent no need draw color
        if (color != DEFAULT_COLOR_BACKGROUND_MAIN) {
            canvas.drawColor(color) // Background total view
        }
    }

    private fun generatePositionGradientFromDirectionAnimation() {
        // Check direction and set x,y start and end for gradient

        when (skeletonModel.animationDirection) {
            SkeletonModel.ANIMATION_DIRECTION_LTR -> {
                val offset = width * animationFractionMove
                xStartGradient = offset
                yStartGradient = 0f
                xEndGradient = offset + width
                yEndGradient = 0f
            }

            SkeletonModel.ANIMATION_DIRECTION_RTL -> {
                val offset = width * animationFractionMove
                xStartGradient = offset
                yStartGradient = 0f
                xEndGradient = offset + width
                yEndGradient = 0f
            }

            SkeletonModel.ANIMATION_DIRECTION_TTB -> {
                val offset = height * animationFractionMove
                xStartGradient = 0f
                yStartGradient = offset
                xEndGradient = 0f
                yEndGradient = offset + height
            }

            SkeletonModel.ANIMATION_DIRECTION_BTT -> {
                val offset = height * animationFractionMove
                xStartGradient = 0f
                yStartGradient = offset
                xEndGradient = 0f
                yEndGradient = offset + height
            }
        }
    }


    /**
     * 构建渐变着色器，基准坐标为(0,0)-(width,0)/(0,height)，
     * 动画时通过 [applyGradientShader] 的 Matrix 平移复用，不重复分配
     */
    private fun buildShaderGradient(): LinearGradient? {
        if (width <= 0 || height <= 0) {
            return null
        }
        val positions = floatArrayOf(0f, 0.4f, 0.8f)
        return when (skeletonModel.animationDirection) {
            SkeletonModel.ANIMATION_DIRECTION_TTB, SkeletonModel.ANIMATION_DIRECTION_BTT ->
                LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    gradientColors, positions, Shader.TileMode.CLAMP
                )

            else ->
                LinearGradient(
                    0f, 0f, width.toFloat(), 0f,
                    gradientColors, positions, Shader.TileMode.CLAMP
                )
        }
    }

    private fun applyGradientShader() {
        if (isShaderGradientDirty) {
            shaderGradient = buildShaderGradient()
            isShaderGradientDirty = false
        }
        val shader = shaderGradient ?: return
        gradientMatrix.reset()
        when (skeletonModel.animationDirection) {
            SkeletonModel.ANIMATION_DIRECTION_TTB, SkeletonModel.ANIMATION_DIRECTION_BTT ->
                gradientMatrix.setTranslate(0f, yStartGradient)

            else ->
                gradientMatrix.setTranslate(xStartGradient, 0f)
        }
        shader.setLocalMatrix(gradientMatrix)
        paintGradient?.shader = shader
    }

    private fun setupInitialAnimation() {
        if (skeletonModelsChildren != null && skeletonModelsChildren!!.size > 0) {
            isCanDraw = true
            isAnimationPlay = true
            isHoldTouchEventsFromChildren = true // Enable Hold touchEvents from this and children
        } else {
            // SkeletonGroup no child extend from skeletonView
            return
        }

        // Checking direction animation for generate value star and end move animation fraction
        var valueAnimationMoveFractionStart = 0f
        var valueAnimationMoveFractionEnd = 0f
        when (skeletonModel.animationDirection) {
            SkeletonModel.ANIMATION_DIRECTION_LTR -> {
                valueAnimationMoveFractionStart = -1f
                valueAnimationMoveFractionEnd = 1f
            }

            SkeletonModel.ANIMATION_DIRECTION_RTL -> {
                valueAnimationMoveFractionStart = 1f
                valueAnimationMoveFractionEnd = -1f
            }

            SkeletonModel.ANIMATION_DIRECTION_TTB -> {
                valueAnimationMoveFractionStart = -1f
                valueAnimationMoveFractionEnd = 1f
            }

            SkeletonModel.ANIMATION_DIRECTION_BTT -> {
                valueAnimationMoveFractionStart = 1f
                valueAnimationMoveFractionEnd = -1f
            }
        }
        // Initial animation
        valueAnimator =
            ValueAnimator.ofFloat(valueAnimationMoveFractionStart, valueAnimationMoveFractionEnd)
        valueAnimator?.setDuration(skeletonModel.animationDuration)
        valueAnimator?.repeatCount = ValueAnimator.INFINITE
        valueAnimator?.repeatMode = ValueAnimator.RESTART
        valueAnimator?.addListener(mAnimatorListener)
        valueAnimator?.addUpdateListener(mAnimatorUpdateListener)
        valueAnimator?.start()
        // Call start animation in listener
        skeletonListener?.get()?.onStartAnimation()
    }

    private fun update(animation: ValueAnimator) {
        animationFractionMove = animation.animatedValue as Float
        animationFraction = animation.animatedFraction
        invalidate()
    }

    private class AnimatorListener(var weakView: WeakReference<SkeletonViewGroup>) :
        Animator.AnimatorListener {
        override fun onAnimationStart(animation: Animator) {
        }

        override fun onAnimationEnd(animation: Animator) {
        }

        override fun onAnimationCancel(animation: Animator) {
        }

        override fun onAnimationRepeat(animation: Animator) {
            val view = weakView.get()
            if (view == null) {
                animation.removeListener(this)
            } else {
                view.repeat()
            }
        }

    }

    private class AnimatorUpdateListener(var weakView: WeakReference<SkeletonViewGroup>) :
        ValueAnimator.AnimatorUpdateListener {
        override fun onAnimationUpdate(animation: ValueAnimator) {
            val view = weakView.get()
            if (view == null) {
                animation.removeUpdateListener(this)
            } else {
                view.update(animation)
            }
        }
    }

    private fun repeat() {
        if (isCanDrawFinishState) {
            setupFinishingAnimation()
        }


        if (isLastLoopAnimation) {
            // Last repeat animation and start finish animation
            if (skeletonModel.animationFinishType == SkeletonModel.ANIMATION_TYPE_NON) {
                setupFinishingAnimation()
            } else {
                isLastLoopAnimation = false
                isCanDrawFinishState = true
                // Change color 011 (color one transparent = 0.0 | color two transparent = 1.0 | color three transparent = 1.0 )
                gradientColors = ColorUtils.generateColorTransparent011(
                    skeletonModel.colorHighLight,
                    skeletonModel.colorBackgroundViews
                )
                isShaderGradientDirty = true
            }
        }
    }


    private fun setupFinishingAnimation() {
        isCanDraw = false
        isLastLoopAnimation = false
        isCanDrawFinishState = false

        // Remove animation listener
        if (valueAnimator != null) {
            valueAnimator?.removeAllListeners()
            valueAnimator?.end()
            valueAnimator?.cancel()
        }


        // Disable Hold touchEvents from this and children
        isHoldTouchEventsFromChildren = false

        // 骨架绘制关闭后重绘一次，让被跳过的子View恢复绘制
        invalidate()

        // Call finish state in listener
        skeletonListener?.get()?.onFinishAnimation()
    }


    fun startAnimation() {
        if (!isAnimationPlay) {
            setupInitialAnimation()
        }
    }

    fun finishAnimation() {
        if (isAnimationPlay) {
            isLastLoopAnimation = true
        }
    }

    fun setSkeletonModels(skeletonModels: ArrayList<SkeletonModel>?) {
        this.skeletonModels = skeletonModels
    }

    fun setAutoPlay(isAnimationAutoStart: Boolean) {
        skeletonModel.isAutoStartAnimation = isAnimationAutoStart
    }

    fun setShowSkeleton(isShowSkeleton: Boolean) {
        skeletonModel.isShowSkeleton = isShowSkeleton
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        setupFinishingAnimation()
    }

    fun setSkeletonListener(skeletonListener: SkeletonListener?) {
        this.skeletonListener = WeakReference(skeletonListener)
    }

    //==============================================================================================
    /* Listener */
    interface SkeletonListener {
        fun onStartAnimation()

        fun onFinishAnimation()
    } //==============================================================================================
}
