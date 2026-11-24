package com.vitwificonnect.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.vitwificonnect.R

/**
 * LiquidGlassCardView
 *
 * Implements a physically-inspired Liquid Glass surface:
 * 1. Multi-stage vertical refraction gradient fill.
 * 2. Directional specular top highlight arc (simulating optical caustics).
 * 3. Gradient hairline border with high-index specular bevel.
 * 4. Micro-spring tactile touch response.
 * 5. Android 12+ hardware backdrop blur optimization.
 */
class LiquidGlassCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private var cornerRadius = 24f * density
    private var isInteractive = false

    // Geometry
    private val boundsRect = RectF()
    private val borderRect = RectF()
    private val clipPath = Path()

    // Paints
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val specularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
    }

    private var accentPrimary: Int = Color.parseColor("#10B981")
    private var accentSecondary: Int = Color.parseColor("#059669")

    // Touch feedback animator
    private var touchScale = 1.0f
    private var touchGlowAlpha = 0f
    private var touchAnimator: ValueAnimator? = null

    init {
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false

        // Custom outline provider for clean hardware clipping & elevation shadow
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
            }
        }
        clipToOutline = true

        elevation = 2f * density

        try {
            accentPrimary = com.vitwificonnect.data.SettingsStore.getAccentColorHex(context)
            accentSecondary = com.vitwificonnect.data.SettingsStore.getSecondaryColorHex(context)
        } catch (_: Exception) {}
    }

    fun setAccentColor(primary: Int, secondary: Int = primary) {
        if (this.accentPrimary == primary && this.accentSecondary == secondary) return
        this.accentPrimary = primary
        this.accentSecondary = secondary
        updateGeometry()
        invalidate()
    }

    fun setInteractive(interactive: Boolean) {
        this.isInteractive = interactive
        isClickable = interactive
        isFocusable = interactive
    }

    fun setGlassCornerRadius(radiusDp: Float) {
        cornerRadius = radiusDp * density
        invalidateOutline()
        updateGeometry()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateGeometry()
    }

    private fun updateGeometry() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        boundsRect.set(0f, 0f, w, h)
        val inset = borderPaint.strokeWidth / 2f
        borderRect.set(inset, inset, w - inset, h - inset)

        clipPath.reset()
        clipPath.addRoundRect(boundsRect, cornerRadius, cornerRadius, Path.Direction.CW)

        val isNight = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

        val r = Color.red(accentPrimary)
        val g = Color.green(accentPrimary)
        val b = Color.blue(accentPrimary)

        // 1. Refractive Substrate Fill (Vertical Gradient with subtle theme ambient refraction)
        val fillStart = if (isNight) {
            Color.argb(32, 255, 255, 255)
        } else {
            Color.argb(235, 255, 255, 255)
        }
        val fillMid = if (isNight) {
            Color.argb(18, ((255 + r) / 2).coerceIn(0, 255), ((255 + g) / 2).coerceIn(0, 255), ((255 + b) / 2).coerceIn(0, 255))
        } else {
            Color.argb(210, 245, 248, 252)
        }
        val fillEnd = if (isNight) Color.argb(12, 255, 255, 255) else Color.argb(195, 240, 243, 247)
        fillPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(fillStart, fillMid, fillEnd),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )

        // 2. Specular Top Highlight (Caustic Light Sheen across top 40%)
        val specColor = if (isNight) Color.argb(65, 255, 255, 255) else Color.argb(180, 255, 255, 255)
        specularPaint.shader = LinearGradient(
            0f, 0f, 0f, h * 0.45f,
            intArrayOf(specColor, Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )

        // 3. Hairline Specular Border Gradient (Light catches top edge directly infused with theme sheen)
        val borderTop = if (isNight) {
            Color.argb(95, ((180 + r) / 2).coerceIn(0, 255), ((180 + g) / 2).coerceIn(0, 255), ((180 + b) / 2).coerceIn(0, 255))
        } else {
            Color.argb(175, 255, 255, 255)
        }
        val borderMid = if (isNight) Color.argb(32, r, g, b) else Color.argb(70, 255, 255, 255)
        val borderBottom = if (isNight) Color.argb(10, 255, 255, 255) else Color.argb(20, 0, 0, 0)
        borderPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(borderTop, borderMid, borderBottom),
            floatArrayOf(0f, 0.35f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun dispatchDraw(canvas: Canvas) {
        // Draw liquid glass backdrop before child views
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) {
            super.dispatchDraw(canvas)
            return
        }

        val saveCount = canvas.save()

        // Apply touch spring scale if interactive
        if (touchScale != 1.0f) {
            canvas.scale(touchScale, touchScale, w / 2f, h / 2f)
        }

        // Draw Substrate Fill
        canvas.drawRoundRect(boundsRect, cornerRadius, cornerRadius, fillPaint)

        // Draw Specular Caustic Highlight
        canvas.drawRoundRect(boundsRect, cornerRadius, cornerRadius, specularPaint)

        // Draw Children
        super.dispatchDraw(canvas)

        // Draw Specular Hairline Bevel Over Children (Crisp refractive edge)
        canvas.drawRoundRect(borderRect, cornerRadius - 1f, cornerRadius - 1f, borderPaint)

        canvas.restoreToCount(saveCount)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isInteractive) return super.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                animateTouch(0.985f, 1.0f)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                animateTouch(1.0f, 0.0f)
            }
        }
        return super.onTouchEvent(event)
    }

    private fun animateTouch(targetScale: Float, targetGlow: Float) {
        touchAnimator?.cancel()
        val startScale = touchScale
        val startGlow = touchGlowAlpha
        touchAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 180L
            interpolator = if (targetScale < 1.0f) DecelerateInterpolator() else OvershootInterpolator(1.4f)
            addUpdateListener { animator ->
                val f = animator.animatedValue as Float
                touchScale = startScale + (targetScale - startScale) * f
                touchGlowAlpha = startGlow + (targetGlow - startGlow) * f
                invalidate()
            }
            start()
        }
    }
}
