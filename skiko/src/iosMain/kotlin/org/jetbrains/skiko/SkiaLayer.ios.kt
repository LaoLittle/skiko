package org.jetbrains.skiko

import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.PictureRecorder
import org.jetbrains.skia.Rect
import org.jetbrains.skiko.context.MetalContextHandler
import org.jetbrains.skiko.redrawer.MetalRedrawer
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSSelectorFromString
import platform.UIKit.*
import platform.darwin.NSObject
import kotlin.system.getTimeNanos

actual open class SkiaLayer {
    private val gestures: Array<SkikoGestureEventKind>?

    constructor(gestures: Array<SkikoGestureEventKind>? = null) {
        this.gestures = gestures
    }

    fun isShowing(): Boolean {
        return true
    }

    fun showScreenKeyboard() {
        view?.becomeFirstResponder()
    }

    fun hideScreenKeyboard() { view?.resignFirstResponder() }

    fun isScreenKeyboardOpen(): Boolean {
        return if (view == null) false else view!!.isFirstResponder
    }

    actual var renderApi: GraphicsApi
        get() = GraphicsApi.METAL
        set(value) { throw UnsupportedOperationException() }

    actual val contentScale: Float
        get() = view!!.contentScaleFactor.toFloat()

    actual var fullscreen: Boolean
        get() = true
        set(value) { throw UnsupportedOperationException() }

    actual var transparency: Boolean
        get() = false
        set(value) { throw UnsupportedOperationException() }

    actual fun needRedraw() {
        redrawer?.needRedraw()
    }

    val width: Float
       get() = view!!.frame.useContents {
           return@useContents size.width.toFloat()
       }

    val height: Float
        get() = view!!.frame.useContents {
            return@useContents size.height.toFloat()
        }

    internal var view: UIView? = null
    // We need to keep reference to controller as Objective-C will only keep weak reference here.
    lateinit private var controller: NSObject

    actual fun attachTo(container: Any) {
        attachTo(container as UIView)
    }

    fun attachTo(view: UIView) {
        this.view = view
        contextHandler = MetalContextHandler(this)
        // See https://developer.apple.com/documentation/uikit/touches_presses_and_gestures/using_responders_and_the_responder_chain_to_handle_events?language=objc
        controller = object : NSObject() {
            @ObjCAction
            fun onTap(sender: UITapGestureRecognizer) {
                val (x, y) = sender.locationInView(view).useContents { x to y }
                skikoView?.onGestureEvent(
                    SkikoGestureEvent(
                        x = x,
                        y = y,
                        kind = SkikoGestureEventKind.TAP,
                        state = toSkikoGestureState(sender.state)
                    )
                )
            }

            @ObjCAction
            fun onDoubleTap(sender: UITapGestureRecognizer) {
                val (x, y) = sender.locationInView(view).useContents { x to y }
                skikoView?.onGestureEvent(
                    SkikoGestureEvent(
                        x = x,
                        y = y,
                        kind = SkikoGestureEventKind.DOUBLETAP,
                        state = toSkikoGestureState(sender.state)
                    )
                )
            }

            @ObjCAction
            fun onLongPress(sender: UILongPressGestureRecognizer) {
                val (x, y) = sender.locationInView(view).useContents { x to y }
                skikoView?.onGestureEvent(
                    SkikoGestureEvent(
                        x = x,
                        y = y,
                        kind = SkikoGestureEventKind.LONGPRESS,
                        state = toSkikoGestureState(sender.state)
                    )
                )
            }

            @ObjCAction
            fun onPinch(sender: UIPinchGestureRecognizer) {
                val (x, y) = sender.locationInView(view).useContents { x to y }
                skikoView?.onGestureEvent(
                    SkikoGestureEvent(
                        x = x,
                        y = y,
                        kind = SkikoGestureEventKind.PINCH,
                        scale = sender.scale,
                        velocity = sender.velocity,
                        state = toSkikoGestureState(sender.state)
                    )
                )
            }

            @ObjCAction
            fun onRotation(sender: UIRotationGestureRecognizer) {
                val (x, y) = sender.locationInView(view).useContents { x to y }
                skikoView?.onGestureEvent(
                    SkikoGestureEvent(
                        x = x,
                        y = y,
                        kind = SkikoGestureEventKind.ROTATION,
                        rotation = sender.rotation,
                        velocity = sender.velocity,
                        state = toSkikoGestureState(sender.state)
                    )
                )
            }

            @ObjCAction
            fun onSwipe(sender: UISwipeGestureRecognizer) {
                val (x, y) = sender.locationInView(view).useContents { x to y }
                skikoView?.onGestureEvent(
                    SkikoGestureEvent(
                        x = x,
                        y = y,
                        kind = SkikoGestureEventKind.SWIPE,
                        direction = toSkikoGestureDirection(sender.direction),
                        state = toSkikoGestureState(sender.state)
                    )
                )
            }

            @ObjCAction
            fun onPan(sender: UIPanGestureRecognizer) {
                val (x, y) = sender.locationInView(view).useContents { x to y }
                skikoView?.onGestureEvent(
                    SkikoGestureEvent(
                        x = x,
                        y = y,
                        kind = SkikoGestureEventKind.PAN,
                        state = toSkikoGestureState(sender.state)
                    )
                )
            }
        }

        if (!gestures.isNullOrEmpty()) {
            // We have ':' in selector to take care of function argument.
            if (gestures.contains(SkikoGestureEventKind.TAP)) {
                view.addGestureRecognizer(UITapGestureRecognizer(controller, NSSelectorFromString("onTap:")))
            }
            if (gestures.contains(SkikoGestureEventKind.DOUBLETAP)) {
                view.addGestureRecognizer(
                    UITapGestureRecognizer(controller, NSSelectorFromString("onDoubleTap:")).apply {
                        numberOfTapsRequired = 2.toULong()
                    }
                )
            }
            if (gestures.contains(SkikoGestureEventKind.LONGPRESS)) {
                view.addGestureRecognizer(UILongPressGestureRecognizer(controller, NSSelectorFromString("onLongPress:")))
            }
            if (gestures.contains(SkikoGestureEventKind.PINCH)) {
                view.addGestureRecognizer(UIPinchGestureRecognizer(controller, NSSelectorFromString("onPinch:")))
            }
            if (gestures.contains(SkikoGestureEventKind.ROTATION)) {
                view.addGestureRecognizer(UIRotationGestureRecognizer(controller, NSSelectorFromString("onRotation:")))
            }
            if (gestures.contains(SkikoGestureEventKind.SWIPE)) {
                view.addGestureRecognizer(UISwipeGestureRecognizer(controller, NSSelectorFromString("onSwipe:")))
            }
            if (gestures.contains(SkikoGestureEventKind.PAN)) {
                view.addGestureRecognizer(UIPanGestureRecognizer(controller, NSSelectorFromString("onPan:")))
            }
        }
        // TODO: maybe add observer for view.viewDidDisappear() to detach us?
        redrawer = MetalRedrawer(this).apply {
            needRedraw()
        }
    }

    private var isDisposed = false
    actual fun detach() {
        if (!isDisposed) {
            redrawer?.dispose()
            redrawer = null
            contextHandler?.dispose()
            contextHandler = null
            isDisposed = true
        }
    }
    actual var skikoView: SkikoView? = null

    internal var redrawer: MetalRedrawer? = null
    private var contextHandler: MetalContextHandler? = null

    internal actual fun draw(canvas: Canvas) {
        check(!isDisposed) { "SkiaLayer is disposed" }
        val (w, h) = view!!.frame.useContents {
            size.width to size.height
        }
        val pictureWidth = (w.toFloat() * contentScale).coerceAtLeast(0.0F)
        val pictureHeight = (h.toFloat() * contentScale).coerceAtLeast(0.0F)

        skikoView?.onRender(canvas, pictureWidth.toInt(), pictureHeight.toInt(), getTimeNanos())
    }
}

// TODO: do properly
actual typealias SkikoTouchPlatformEvent = UITouch
actual typealias SkikoGesturePlatformEvent = UIEvent
actual typealias SkikoPlatformInputEvent = UIPress
actual typealias SkikoPlatformKeyboardEvent = UIPress
actual typealias SkikoPlatformPointerEvent = UIEvent
