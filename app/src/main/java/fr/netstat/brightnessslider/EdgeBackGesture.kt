package fr.netstat.brightnessslider

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityWindowInfo
import kotlin.math.abs
import kotlin.math.min

enum class Edge { LEFT, RIGHT }

/**
 * Android-style back gesture: drag inwards from a side edge and release to go back, or drag past
 * a fraction of the screen width and release to open recents. Releasing below the back threshold
 * cancels the gesture.
 */
@SuppressLint("RtlHardcoded") // edges are physical, not layout-direction relative
class EdgeBackGesture(
    private val service: AccessibilityService,
    private val statusBarHeightDetector: StatusBarHeightDetector,
) {
    private enum class State { IDLE, PENDING, DRAGGING, VERTICAL }

    private val density = service.resources.displayMetrics.density
    private var edgeWidthPx = (DEFAULT_EDGE_WIDTH_DP * density).toInt()
    private val backThresholdPx = BACK_THRESHOLD_DP * density
    private val slop = ViewConfiguration.get(service).scaledTouchSlop

    private val windowManager = getWindowManager(service)
    private val handler = Handler(Looper.getMainLooper())

    private val edgeViews = mutableMapOf<Edge, View>()
    private var indicator: BackArrowView? = null

    private var edges: Set<Edge> = emptySet()
    private var enabled = false
    private var recentsFraction = 0.6f
    private var imeTop: Int? = null
    private var touchDisabled = false

    private val restoreRunnable = Runnable {
        touchDisabled = false
        applyTouchable()
    }

    // touch state
    private var state = State.IDLE
    private var downX = 0f
    private var downY = 0f
    private var backArmed = false
    private var recentsArmed = false

    fun setEdges(newEdges: Set<Edge>) {
        if (edges == newEdges) return
        edges = newEdges
        syncWindows()
    }

    fun setEdgeWidth(widthDp: Int) {
        val widthPx = (widthDp * density).toInt()
        if (edgeWidthPx == widthPx) return
        edgeWidthPx = widthPx
        updateLayout()
    }

    fun setRecentsFraction(fraction: Float) {
        recentsFraction = fraction
    }

    fun setEnabled(newEnabled: Boolean) {
        if (enabled == newEnabled) return
        enabled = newEnabled
        syncWindows()
    }

    fun updateLayout() {
        imeTop = findImeTop()
        edgeViews.forEach { (edge, view) ->
            val params = view.layoutParams as WindowManager.LayoutParams
            if (applyGeometry(params, edge)) windowManager.updateViewLayout(view, params)
        }
    }

    fun onWindowsChanged() {
        if (edgeViews.isEmpty()) return

        val newImeTop = findImeTop()
        if (newImeTop == imeTop) return

        updateLayout()
    }

    fun destroy() {
        handler.removeCallbacks(restoreRunnable)
        edgeViews.values.forEach { windowManager.removeView(it) }
        edgeViews.clear()
        indicator?.let { windowManager.removeView(it) }
        indicator = null
        state = State.IDLE
    }

    private fun syncWindows() {
        val wanted = if (enabled) edges else emptySet()

        edgeViews.keys.filter { it !in wanted }.forEach { edge ->
            windowManager.removeView(edgeViews.remove(edge))
        }

        if (wanted.isEmpty()) {
            state = State.IDLE
            indicator?.visibility = View.GONE
            return
        }

        imeTop = findImeTop()
        wanted.filter { it !in edgeViews }.forEach { addEdgeView(it) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addEdgeView(edge: Edge) {
        ensureIndicator()

        val view = View(service)
        view.setOnTouchListener { _, event -> onEdgeTouch(edge, event) }

        @Suppress("DEPRECATION")
        val params = WindowManager.LayoutParams(
            edgeWidthPx,
            0,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or (if (touchDisabled) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0),
            PixelFormat.TRANSPARENT,
        )
        applyGeometry(params, edge)

        windowManager.addView(view, params)
        edgeViews[edge] = view
    }

    private fun ensureIndicator() {
        if (indicator != null) return

        val view = BackArrowView(service)
        view.visibility = View.GONE

        @Suppress("DEPRECATION")
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = 0
            y = 0
        }

        windowManager.addView(view, params)
        indicator = view
    }

    /** Returns true if the params changed. */
    private fun applyGeometry(params: WindowManager.LayoutParams, edge: Edge): Boolean {
        val y = statusBarHeightDetector.height
        val height = ((imeTop ?: screenBounds().height()) - y).coerceAtLeast(0)
        val gravity = Gravity.TOP or if (edge == Edge.LEFT) Gravity.LEFT else Gravity.RIGHT

        if (params.y == y && params.height == height && params.width == edgeWidthPx
            && params.gravity == gravity && params.x == 0
        ) return false

        params.x = 0
        params.y = y
        params.width = edgeWidthPx
        params.height = height
        params.gravity = gravity
        return true
    }

    private fun screenBounds() = windowManager.currentWindowMetrics.bounds

    private fun findImeTop(): Int? {
        val ime = service.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            ?: return null
        val rect = android.graphics.Rect()
        ime.getBoundsInScreen(rect)
        return rect.top
    }

    private fun disableFor(milliseconds: Long) {
        touchDisabled = true
        applyTouchable()
        handler.removeCallbacks(restoreRunnable)
        handler.postDelayed(restoreRunnable, milliseconds)
    }

    private fun applyTouchable() {
        edgeViews.values.forEach { view ->
            val params = view.layoutParams as WindowManager.LayoutParams
            val flags = if (touchDisabled) {
                params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            }
            if (flags != params.flags) {
                params.flags = flags
                windowManager.updateViewLayout(view, params)
            }
        }
    }

    private fun onEdgeTouch(edge: Edge, event: MotionEvent): Boolean {
        val dx = if (edge == Edge.LEFT) event.rawX - downX else downX - event.rawX

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                backArmed = false
                recentsArmed = false
                state = State.PENDING
            }

            MotionEvent.ACTION_MOVE -> {
                val dy = event.rawY - downY
                if (state == State.PENDING) {
                    if (abs(dy) > slop && abs(dy) > dx) {
                        state = State.VERTICAL
                    } else if (dx > slop) {
                        state = State.DRAGGING
                        indicator?.visibility = View.VISIBLE
                    }
                }

                if (state == State.DRAGGING) {
                    val recentsPx = recentsFraction * screenBounds().width()

                    if (dx >= backThresholdPx && !backArmed) vibrate(service, 5)
                    backArmed = dx >= backThresholdPx

                    if (dx >= recentsPx && !recentsArmed) vibrate(service, 5)
                    recentsArmed = dx >= recentsPx

                    indicator?.update(edge, event.rawY, dx, backArmed, recentsArmed)
                }
            }

            MotionEvent.ACTION_UP -> {
                indicator?.visibility = View.GONE
                val previous = state
                state = State.IDLE

                when {
                    previous == State.PENDING -> disableFor(TAP_DISABLE_MS)
                    previous == State.DRAGGING && recentsArmed ->
                        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)

                    previous == State.DRAGGING && backArmed ->
                        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)

                    previous != State.IDLE -> disableFor(CANCEL_DISABLE_MS)
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                indicator?.visibility = View.GONE
                state = State.IDLE
            }
        }

        return false
    }

    companion object {
        private const val DEFAULT_EDGE_WIDTH_DP = 32
        private const val BACK_THRESHOLD_DP = 64
        private const val TAP_DISABLE_MS = 5000L
        private const val CANCEL_DISABLE_MS = 2000L
    }
}

/** Bubble with a chevron that follows the finger while dragging from an edge. */
private class BackArrowView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val radius = 22 * density
    private val maxTravel = 40 * density
    private val chevronSize = 8 * density

    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF202020.toInt() }
    private val chevronPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val path = Path()
    private val bubble = RectF()
    private val location = IntArray(2)

    private var edge = Edge.LEFT
    private var rawY = 0f
    private var dx = 0f
    private var backArmed = false
    private var recentsArmed = false

    fun update(edge: Edge, rawY: Float, dx: Float, backArmed: Boolean, recentsArmed: Boolean) {
        this.edge = edge
        this.rawY = rawY
        this.dx = dx
        this.backArmed = backArmed
        this.recentsArmed = recentsArmed
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        getLocationOnScreen(location)

        val travel = min(dx, maxTravel)
        val left = edge == Edge.LEFT
        val rawCx = if (left) travel - radius / 2 else width + location[0] - travel + radius / 2
        val cx = rawCx - location[0]
        val cy = rawY - location[1]

        bubblePaint.alpha = if (backArmed) 230 else 110
        chevronPaint.alpha = if (backArmed) 255 else 140
        bubble.set(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawRoundRect(bubble, radius, radius, bubblePaint)

        // ">" for the left edge and "<" for the right edge (pointing away from the edge)
        val d = if (left) -1f else 1f
        val s = chevronSize
        path.reset()
        if (recentsArmed) {
            for (offset in floatArrayOf(-s * 0.4f, s * 0.4f)) {
                val x = cx + d * offset
                path.moveTo(x + d * s / 2, cy - s)
                path.lineTo(x - d * s / 2, cy)
                path.lineTo(x + d * s / 2, cy + s)
            }
        } else {
            path.moveTo(cx + d * s / 2, cy - s)
            path.lineTo(cx - d * s / 2, cy)
            path.lineTo(cx + d * s / 2, cy + s)
        }
        canvas.drawPath(path, chevronPaint)
    }
}
