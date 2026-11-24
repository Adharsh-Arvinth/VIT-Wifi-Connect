package com.vitwificonnect.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.vitwificonnect.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * ConnectionIndicatorView - Liquid Glass Edition
 *
 * Implements physical liquid glass lens optics:
 * 1. Multi-tier refractive outer halo rings with specular caustic highlight.
 * 2. Translucent glass substrate orb with directional internal light dispersion.
 * 3. Curved specular crescent highlight simulating optical refraction.
 * 4. Dynamic orbital photon for authenticating/connecting states.
 * 5. High-contrast, clean semantic glyphs (Checkmark, Exclamation, Waves).
 */
class ConnectionIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class State {
        DISCONNECTED,
        CONNECTING,
        AUTHENTICATING,
        CONNECTED,
        ERROR
    }

    private var currentState = State.DISCONNECTED
    private var pulseFraction = 0f
    private var rotationAngle = 0f

    private var accentPrimary: Int = Color.parseColor("#10B981")
    private var accentSecondary: Int = Color.parseColor("#059669")

    private var pulseAnimator: ValueAnimator? = null
    private var rotateAnimator: ValueAnimator? = null

    // Geometry
    private val centerBounds = RectF()
    private val specularArcBounds = RectF()
    private val checkPath = Path()

    // Paints
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val glassOrbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val specularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val photonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        // Hardware acceleration for silky 120 FPS rendering
        setLayerType(LAYER_TYPE_HARDWARE, null)
        try {
            accentPrimary = com.vitwificonnect.data.SettingsStore.getAccentColorHex(context)
            accentSecondary = com.vitwificonnect.data.SettingsStore.getSecondaryColorHex(context)
        } catch (_: Exception) {}
        setupAnimators()
    }

    fun setAccentColor(primary: Int, secondary: Int = primary) {
        if (this.accentPrimary == primary && this.accentSecondary == secondary) return
        this.accentPrimary = primary
        this.accentSecondary = secondary
        invalidate()
    }

    private fun areAnimationsEnabled(): Boolean {
        return try {
            val duration = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
            duration > 0f
        } catch (_: Exception) {
            true
        }
    }

    private fun setupAnimators() {
        if (!areAnimationsEnabled()) return

        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1800L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                pulseFraction = it.animatedValue as Float
                invalidate()
            }
        }

        rotateAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 2400L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener {
                rotationAngle = it.animatedValue as Float
                invalidate()
            }
        }
    }

    fun setState(state: State) {
        if (currentState == state) return
        currentState = state

        when (state) {
            State.CONNECTING -> {
                if (pulseAnimator?.isRunning != true && areAnimationsEnabled()) pulseAnimator?.start()
                rotateAnimator?.cancel()
            }
            State.AUTHENTICATING -> {
                if (pulseAnimator?.isRunning != true && areAnimationsEnabled()) pulseAnimator?.start()
                if (rotateAnimator?.isRunning != true && areAnimationsEnabled()) rotateAnimator?.start()
            }
            State.CONNECTED -> {
                pulseAnimator?.cancel()
                rotateAnimator?.cancel()
                pulseFraction = 0f
                rotationAngle = 0f
            }
            else -> {
                pulseAnimator?.cancel()
                rotateAnimator?.cancel()
                pulseFraction = 0f
                rotationAngle = 0f
            }
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pulseAnimator?.cancel()
        rotateAnimator?.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val maxRadius = min(cx, cy) - dpToPx(6f)
        val density = resources.displayMetrics.density

        val isNight = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

        val connectedColor = accentPrimary
        val connectingColor = ContextCompat.getColor(context, R.color.status_connecting)
        val errorColor = ContextCompat.getColor(context, R.color.status_error)

        val r = Color.red(accentPrimary)
        val g = Color.green(accentPrimary)
        val b = Color.blue(accentPrimary)

        // 1. Outer Liquid Glass Rim
        val outerRadius = maxRadius * 0.94f
        val rimTopColor = if (isNight) {
            Color.argb(95, ((200 + r) / 2).coerceIn(0, 255), ((200 + g) / 2).coerceIn(0, 255), ((200 + b) / 2).coerceIn(0, 255))
        } else {
            Color.argb(190, 255, 255, 255)
        }
        val rimBottomColor = if (isNight) Color.argb(14, 255, 255, 255) else Color.argb(25, 0, 0, 0)
        ringPaint.strokeWidth = 1.4f * density
        ringPaint.shader = LinearGradient(
            0f, cy - outerRadius, 0f, cy + outerRadius,
            rimTopColor, rimBottomColor,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, outerRadius, ringPaint)
        ringPaint.shader = null

        // 2. Dynamic Liquid Aura / Ripple
        if (currentState == State.CONNECTING || currentState == State.AUTHENTICATING) {
            val auraRadius = outerRadius * (0.88f + 0.22f * pulseFraction)
            val auraAlpha = ((1f - pulseFraction) * 125).toInt().coerceIn(0, 255)
            val baseAuraColor = if (currentState == State.CONNECTING) connectingColor else accentPrimary
            auraPaint.color = baseAuraColor
            auraPaint.alpha = auraAlpha
            auraPaint.strokeWidth = 2.5f * density
            canvas.drawCircle(cx, cy, auraRadius, auraPaint)
        }

        // 3. Central Liquid Glass Orb
        val orbRadius = outerRadius * 0.74f

        val orbCenterColor = when (currentState) {
            State.CONNECTED -> Color.argb(75, r, g, b)
            State.CONNECTING -> Color.argb(55, 245, 158, 11)
            State.AUTHENTICATING -> Color.argb(60, r, g, b)
            State.ERROR -> Color.argb(65, 239, 68, 68)
            State.DISCONNECTED -> if (isNight) Color.argb(40, r, g, b) else Color.argb(35, r, g, b)
        }

        val orbEdgeColor = when (currentState) {
            State.CONNECTED -> Color.argb(30, r, g, b)
            State.CONNECTING -> Color.argb(20, 217, 119, 6)
            State.AUTHENTICATING -> Color.argb(25, r, g, b)
            State.ERROR -> Color.argb(25, 220, 38, 38)
            State.DISCONNECTED -> if (isNight) Color.argb(14, r, g, b) else Color.argb(12, 0, 0, 0)
        }

        // Radial light dispersion inside the glass orb
        glassOrbPaint.shader = RadialGradient(
            cx, cy - orbRadius * 0.35f, orbRadius * 1.15f,
            intArrayOf(orbCenterColor, orbEdgeColor),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, orbRadius, glassOrbPaint)
        glassOrbPaint.shader = null

        // 4. Specular Caustic Crescent Highlight (Light reflecting on top glass curvature)
        val specHighlightColor = if (isNight) Color.argb(130, 255, 255, 255) else Color.argb(210, 255, 255, 255)
        specularPaint.shader = LinearGradient(
            0f, cy - orbRadius, 0f, cy - orbRadius * 0.1f,
            intArrayOf(specHighlightColor, Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        specularArcBounds.set(cx - orbRadius * 0.72f, cy - orbRadius * 0.95f, cx + orbRadius * 0.72f, cy - orbRadius * 0.05f)
        canvas.drawOval(specularArcBounds, specularPaint)
        specularPaint.shader = null

        // 5. Orb Specular Border Bevel
        val orbBorderColor = when (currentState) {
            State.CONNECTED -> connectedColor
            State.CONNECTING -> connectingColor
            State.AUTHENTICATING -> accentPrimary
            State.ERROR -> errorColor
            State.DISCONNECTED -> if (isNight) Color.argb(135, r, g, b) else Color.argb(140, r, g, b)
        }
        ringPaint.strokeWidth = 1.6f * density
        ringPaint.color = orbBorderColor
        canvas.drawCircle(cx, cy, orbRadius, ringPaint)

        // 6. Orbital Photon for Authenticating State
        if (currentState == State.AUTHENTICATING) {
            val rad = Math.toRadians(rotationAngle.toDouble())
            val px = cx + (outerRadius * 0.88f) * cos(rad).toFloat()
            val py = cy + (outerRadius * 0.88f) * sin(rad).toFloat()
            photonPaint.color = orbBorderColor
            canvas.drawCircle(px, py, 3.2f * density, photonPaint)
        }

        // 7. High-Craft Precision Center Glyphs
        glyphPaint.strokeWidth = 2.2f * density
        when (currentState) {
            State.CONNECTED -> {
                // Precision Apple-style checkmark
                glyphPaint.color = connectedColor
                val sz = orbRadius * 0.44f
                checkPath.reset()
                checkPath.moveTo(cx - sz * 0.75f, cy + sz * 0.05f)
                checkPath.lineTo(cx - sz * 0.2f, cy + sz * 0.6f)
                checkPath.lineTo(cx + sz * 0.8f, cy - sz * 0.48f)
                canvas.drawPath(checkPath, glyphPaint)
            }
            State.ERROR -> {
                // Minimalist exclamation
                glyphPaint.color = errorColor
                canvas.drawLine(cx, cy - orbRadius * 0.42f, cx, cy + orbRadius * 0.08f, glyphPaint)
                canvas.drawCircle(cx, cy + orbRadius * 0.36f, 1.8f * density, glyphPaint)
            }
            State.CONNECTING, State.AUTHENTICATING -> {
                // Refractive rotating arc
                glyphPaint.color = orbBorderColor
                val arcRadius = orbRadius * 0.46f
                centerBounds.set(cx - arcRadius, cy - arcRadius, cx + arcRadius, cy + arcRadius)
                val start = if (currentState == State.AUTHENTICATING) rotationAngle else (pulseFraction * 360f)
                canvas.drawArc(centerBounds, start, 260f, false, glyphPaint)
            }
            State.DISCONNECTED -> {
                // WiFi Waveform Arcs in theme color
                glyphPaint.color = if (isNight) Color.argb(195, r, g, b) else Color.argb(220, r, g, b)
                val r1 = orbRadius * 0.52f
                val r2 = orbRadius * 0.34f
                val r3 = orbRadius * 0.16f

                centerBounds.set(cx - r1, cy - r1, cx + r1, cy + r1)
                canvas.drawArc(centerBounds, 225f, 90f, false, glyphPaint)

                centerBounds.set(cx - r2, cy - r2, cx + r2, cy + r2)
                canvas.drawArc(centerBounds, 225f, 90f, false, glyphPaint)

                centerBounds.set(cx - r3, cy - r3, cx + r3, cy + r3)
                canvas.drawArc(centerBounds, 225f, 90f, false, glyphPaint)
            }
        }
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
}
