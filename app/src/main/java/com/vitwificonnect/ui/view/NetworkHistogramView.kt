package com.vitwificonnect.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.vitwificonnect.R
import com.vitwificonnect.data.SettingsStore

/**
 * NetworkHistogramView - Liquid Glass Edition
 *
 * Renders network activity bars with liquid glass optics:
 * 1. Smoked glass background slots with subtle specular bevels.
 * 2. Active liquid glass capsules with gradient luminescence.
 * 3. Specular highlight caps on each active bar.
 * 4. Physics-interpolated smooth height transitions.
 */
class NetworkHistogramView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val BAR_COUNT = 14
    private val currentHeights = FloatArray(BAR_COUNT) { 0.06f }
    private val targetHeights = FloatArray(BAR_COUNT) { 0.06f }

    private val barRect = RectF()
    private val capRect = RectF()

    private var accentPrimary: Int = Color.parseColor("#10B981")
    private var accentSecondary: Int = Color.parseColor("#059669")

    private val slotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val activeBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val specularCapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        try {
            accentPrimary = SettingsStore.getAccentColorHex(context)
            accentSecondary = SettingsStore.getGlowColorHex(context)
        } catch (_: Exception) {}
    }

    fun setAccentColor(primary: Int, secondary: Int = primary) {
        if (this.accentPrimary == primary && this.accentSecondary == secondary) return
        this.accentPrimary = primary
        this.accentSecondary = secondary
        postInvalidateOnAnimation()
    }

    fun setValues(values: List<Float>) {
        val startIdx = (BAR_COUNT - values.size).coerceAtLeast(0)
        for (i in 0 until BAR_COUNT) {
            val v = if (i >= startIdx && i - startIdx < values.size) {
                values[i - startIdx].coerceIn(0.06f, 1.0f)
            } else {
                0.06f
            }
            targetHeights[i] = v
        }
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val isNight = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

        val isDynamic = SettingsStore.getChartColorMode(context) == "dynamic"
        val r = Color.red(accentPrimary)
        val g = Color.green(accentPrimary)
        val b = Color.blue(accentPrimary)

        // Frosted slot color with delicate theme ambient reflection
        val slotColor = if (isNight) {
            Color.argb(25, ((180 + r) / 2).coerceIn(0, 255), ((180 + g) / 2).coerceIn(0, 255), ((180 + b) / 2).coerceIn(0, 255))
        } else {
            Color.argb(35, 0, 0, 0)
        }
        slotPaint.color = slotColor

        // Specular cap paint
        val capColor = if (isNight) Color.argb(180, 255, 255, 255) else Color.argb(220, 255, 255, 255)
        specularCapPaint.color = capColor

        val gap = dpToPx(3.5f)
        val totalGaps = gap * (BAR_COUNT - 1)
        val barWidth = ((w - totalGaps) / BAR_COUNT).coerceAtLeast(dpToPx(2f))
        val cornerRadius = barWidth / 2f

        var hasPendingAnimation = false

        for (i in 0 until BAR_COUNT) {
            // Smooth interpolation
            val diff = targetHeights[i] - currentHeights[i]
            if (kotlin.math.abs(diff) > 0.004f) {
                currentHeights[i] += diff * 0.22f
                hasPendingAnimation = true
            } else {
                currentHeights[i] = targetHeights[i]
            }

            val left = i * (barWidth + gap)
            val right = left + barWidth
            val barHeight = (h * currentHeights[i]).coerceAtLeast(dpToPx(4f))
            val top = h - barHeight
            val bottom = h

            // 1. Frosted slot track
            barRect.set(left, 0f, right, h)
            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, slotPaint)

            // 2. Active liquid glass bar with gradient
            barRect.set(left, top, right, bottom)
            if (isDynamic) {
                activeBarPaint.shader = LinearGradient(
                    left, top, left, bottom,
                    accentSecondary, accentPrimary,
                    Shader.TileMode.CLAMP
                )
            } else {
                activeBarPaint.shader = null
                activeBarPaint.color = ContextCompat.getColor(context, R.color.chart_bar_fill)
            }
            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, activeBarPaint)
            activeBarPaint.shader = null

            // 3. Specular highlight cap on active bar tip
            if (currentHeights[i] > 0.10f) {
                capRect.set(left + dpToPx(0.8f), top + dpToPx(0.8f), right - dpToPx(0.8f), top + dpToPx(3.5f))
                canvas.drawRoundRect(capRect, cornerRadius, cornerRadius, specularCapPaint)
            }
        }

        if (hasPendingAnimation) {
            postInvalidateOnAnimation()
        }
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
}
