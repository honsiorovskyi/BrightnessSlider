package fr.netstat.brightnessslider

import android.annotation.SuppressLint
import android.util.Log
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

private fun noMotionEvent() = MotionEvent.obtain(
    -1,
    -1,
    -1,
    -1f,
    -1f,
    -1
)

enum class MotionState {
    UNKNOWN,
    SINGLE_TAP,
    SINGLE_LONG_TAP,
    DOUBLE_TAP,
    DOUBLE_LONG_TAP,
    SINGLE_HORIZONTAL_SLIDE,
    DOUBLE_HORIZONTAL_SLIDE,
    SINGLE_HORIZONTAL_FLICK,
    DOUBLE_HORIZONTAL_FLICK,
}

interface GestureListener {
    // make sure these handlers are fast, as they'll block further processing!
    fun onSingleTap(event: MotionEvent) {}
    fun onDoubleTap(event: MotionEvent) {}

    fun onSingleTapConfirmed(event: MotionEvent) {}
    fun onDoubleTapConfirmed(event: MotionEvent) {}

    fun onSingleLongTap(event: MotionEvent) {}
    fun onDoubleLongTap(event: MotionEvent) {}

    fun onSingleHorizontalSlide(event: MotionEvent) {}
    fun onDoubleHorizontalSlide(event: MotionEvent) {}

    fun onSingleHorizontalFlick(event: MotionEvent, velocity: Float) {}
    fun onDoubleHorizontalFlick(event: MotionEvent, velocity: Float) {}

    fun onUnTap(event: MotionEvent) {}
}

class GestureDetector(private val gestureListener: GestureListener): View.OnTouchListener {
    private val doubleTapThreshold = 300
    private val longTapThreshold = 400
    private val xMoveThreshold = 80

    private var state: MotionState = MotionState.UNKNOWN
    private var downEvent: MotionEvent = noMotionEvent()

    private fun hasDownEventHappened(): Boolean {
        if (downEvent.downTime == -1L && downEvent.eventTime == -1L && downEvent.action == -1 && downEvent.x == -1f && downEvent.y == -1f && downEvent.metaState == -1) {
            return false
        }

        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        if (event == null) {
            return false
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                state = MotionState.SINGLE_TAP
                gestureListener.onSingleTap(event)

                if (hasDownEventHappened() && event.eventTime - downEvent.eventTime < doubleTapThreshold) {
                    state = MotionState.DOUBLE_TAP
                    gestureListener.onDoubleTap(event)
                }

                downEvent = MotionEvent.obtain(event)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!hasDownEventHappened()) {
                    // looks like we ended up here by an error
                    Log.v("WARN", "ACTION_MOVE event without prior ACTION_DOWN")
                    return false
                }

                when (val prevState = state) {
                    // looks like we ended up here by an error, let's ignore
                    MotionState.UNKNOWN -> {}

                    // if we've already detected any kind of long taps or flicks
                    // ignore further motion events
                    MotionState.SINGLE_LONG_TAP, MotionState.DOUBLE_LONG_TAP,
                    MotionState.SINGLE_HORIZONTAL_FLICK, MotionState.DOUBLE_HORIZONTAL_FLICK -> {}

                    MotionState.SINGLE_TAP, MotionState.DOUBLE_TAP -> {
                        // user has tapped once or twice and possibly sliding now
                        // from here we can detect
                        // - a sliding event,
                        // - a flick, or
                        // - a long tap

                        when {
                            // either a horizontal slide or flick
                            abs(event.x - downEvent.x) > xMoveThreshold -> {
                                // if it's fast, it's a flick
                                val speed = (event.x - downEvent.x)/(event.eventTime - downEvent.eventTime)

                                when {
                                    // it's a flick (fast)
                                    abs(speed) > 1 -> {
                                        when (prevState) {
                                            MotionState.SINGLE_TAP -> {
                                                state = MotionState.SINGLE_HORIZONTAL_FLICK
                                                gestureListener.onSingleHorizontalFlick(event, speed)
                                            }
                                            MotionState.DOUBLE_TAP -> {
                                                state = MotionState.DOUBLE_HORIZONTAL_FLICK
                                                gestureListener.onDoubleHorizontalFlick(event, speed)
                                            }
                                            else -> {}
                                        }
                                    }

                                    // it's a slide (slow)
                                    else -> {
                                        when (prevState) {
                                            MotionState.SINGLE_TAP -> {
                                                state = MotionState.SINGLE_HORIZONTAL_SLIDE
                                                gestureListener.onSingleHorizontalSlide(event)
                                            }
                                            MotionState.DOUBLE_TAP -> {
                                                state = MotionState.DOUBLE_HORIZONTAL_SLIDE
                                                gestureListener.onDoubleHorizontalSlide(event)
                                            }
                                            else -> {}
                                        }
                                    }
                                }
                            }

                            // long tap
                            event.eventTime - downEvent.eventTime > longTapThreshold -> {
                                when (prevState) {
                                    MotionState.SINGLE_TAP -> {
                                        state = MotionState.SINGLE_LONG_TAP
                                        gestureListener.onSingleLongTap(event)
                                    }
                                    MotionState.DOUBLE_TAP -> {
                                        state = MotionState.DOUBLE_LONG_TAP
                                        gestureListener.onDoubleLongTap(event)
                                    }
                                    else -> {}
                                }
                            }
                        }
                    }

                    MotionState.SINGLE_HORIZONTAL_SLIDE -> gestureListener.onSingleHorizontalSlide(event)
                    MotionState.DOUBLE_HORIZONTAL_SLIDE -> gestureListener.onDoubleHorizontalSlide(event)
                }
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                when (state) {
                    MotionState.SINGLE_TAP -> gestureListener.onSingleTapConfirmed(event)
                    MotionState.DOUBLE_TAP -> gestureListener.onDoubleTapConfirmed(event)
                    else -> {}
                }

                gestureListener.onUnTap(event)
            }
        }

        return false
    }
}