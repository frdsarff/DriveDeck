package com.example.drivedeck.car

import android.content.Context
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Bridges Android Auto's host-provided surface to a VirtualDisplay that hosts
 * a Presentation/WebView. This is an experimental rendering PoC.
 */
class CarSurfaceRenderer(
    private val carContext: CarContext,
    lifecycle: Lifecycle
) : SurfaceCallback, LifecycleEventObserver {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val appManager = carContext.getCarService(AppManager::class.java)
    private val displayManager =
        carContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    private var isRegistered = false
    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: CarDashboardPresentation? = null
    private var activeSurface: Surface? = null

    private var currentWidth = 0
    private var currentHeight = 0
    private var currentDpi = 0
    private var currentDisplayId = -1
    private var visibleRectStr = "N/A"
    private var stableRectStr = "N/A"
    private var eventCounter = 0

    init {
        // Register immediately from HomeScreen, and observe only destruction.
        lifecycle.addObserver(this)
    }

    fun register() {
        runOnMainThread {
            if (isRegistered) return@runOnMainThread
            try {
                appManager.setSurfaceCallback(this)
                isRegistered = true
                Log.i(TAG, "SurfaceCallback registered")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register SurfaceCallback", e)
            }
        }
    }

    private fun unregister() {
        if (!isRegistered) {
            releaseRenderResources("unregister-without-registration")
            return
        }
        try {
            appManager.setSurfaceCallback(null)
            Log.i(TAG, "SurfaceCallback unregistered")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister SurfaceCallback", e)
        } finally {
            isRegistered = false
            releaseRenderResources("unregister")
        }
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_DESTROY) {
            runOnMainThread { unregister() }
        }
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        val surface = surfaceContainer.surface
        val width = surfaceContainer.width
        val height = surfaceContainer.height
        val dpi = surfaceContainer.dpi
        Log.i(TAG, "onSurfaceAvailable: ${width}x${height} @ ${dpi} dpi, valid=${surface?.isValid}")

        runOnMainThread {
            setupOrUpdateSurface(surface, width, height, dpi)
        }
    }

    private fun setupOrUpdateSurface(surface: Surface?, width: Int, height: Int, dpi: Int) {
        if (surface == null || !surface.isValid || width <= 0 || height <= 0 || dpi <= 0) {
            Log.e(TAG, "setupOrUpdateSurface: Invalid surface metrics")
            releaseRenderResources("invalid-surface")
            return
        }

        eventCounter++
        val existing = virtualDisplay
        if (existing != null && presentation?.isShowing == true &&
            currentWidth == width && currentHeight == height && currentDpi == dpi
        ) {
            if (activeSurface !== surface) {
                val oldSurface = activeSurface
                try {
                    existing.surface = surface
                    activeSurface = surface
                    oldSurface?.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to rebind VirtualDisplay surface", e)
                    releaseRenderResources("surface-rebind-failed")
                    createRenderResources(surface, width, height, dpi)
                    return
                }
            }
            pushCurrentDiagnostics()
            return
        }

        releaseRenderResources("surface-characteristics-changed")
        createRenderResources(surface, width, height, dpi)
    }

    private fun createRenderResources(surface: Surface, width: Int, height: Int, dpi: Int) {
        try {
            val vd = displayManager.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME,
                width,
                height,
                dpi,
                surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
            ) ?: throw IllegalStateException("DisplayManager returned null VirtualDisplay")

            virtualDisplay = vd
            activeSurface = surface
            currentWidth = width
            currentHeight = height
            currentDpi = dpi
            currentDisplayId = vd.display?.displayId ?: -1

            val display = vd.display
                ?: throw IllegalStateException("VirtualDisplay has no Display")

            presentation = CarDashboardPresentation(
                carContext,
                display,
                buildDiagnostics()
            ).also { it.show() }

            Log.i(
                TAG,
                "VirtualDisplay + Presentation created: displayId=$currentDisplayId, ${width}x${height} @ ${dpi}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create VirtualDisplay/Presentation", e)
            releaseRenderResources("create-failed")
        }
    }

    /**
     * For hosts that support Car App API 5 tap callbacks, forward the host
     * surface coordinates to the WebView shown on the virtual display.
     */
    override fun onClick(x: Float, y: Float) {
        Log.i(TAG, "SurfaceCallback.onClick: x=$x y=$y")
        runOnMainThread {
            presentation?.dispatchDashboardClick(x, y)
                ?: Log.w(TAG, "onClick ignored: Presentation is unavailable")
        }
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        Log.d(TAG, "SurfaceCallback.onScroll: dx=$distanceX dy=$distanceY")
        runOnMainThread {
            presentation?.dispatchDashboardScroll(distanceX, distanceY)
        }
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        Log.d(TAG, "SurfaceCallback.onFling: vx=$velocityX vy=$velocityY")
        runOnMainThread {
            presentation?.dispatchDashboardFling(velocityX, velocityY)
        }
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        runOnMainThread {
            visibleRectStr = visibleArea.flattenToString()
            eventCounter++
            pushCurrentDiagnostics()
        }
    }

    override fun onStableAreaChanged(stableArea: Rect) {
        runOnMainThread {
            stableRectStr = stableArea.flattenToString()
            eventCounter++
            pushCurrentDiagnostics()
        }
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        Log.i(
            TAG,
            "onSurfaceDestroyed: width=${surfaceContainer.width}, height=${surfaceContainer.height}, dpi=${surfaceContainer.dpi}"
        )
        runOnMainThread {
            releaseRenderResources("onSurfaceDestroyed")
        }
    }

    fun reloadDashboard() {
        runOnMainThread {
            val pres = presentation
            if (pres != null && pres.isShowing) {
                pres.reloadWebView()
            } else {
                Log.w(TAG, "reloadDashboard: Ignored because Presentation is not currently active")
            }
        }
    }

    private fun pushCurrentDiagnostics() {
        presentation?.updateDiagnostics(buildDiagnostics())
    }

    private fun buildDiagnostics(): SurfaceDiagnostics {
        return SurfaceDiagnostics(
            width = currentWidth,
            height = currentHeight,
            dpi = currentDpi,
            displayId = currentDisplayId,
            carApiLevel = carContext.carAppApiLevel,
            visibleArea = visibleRectStr,
            stableArea = stableRectStr,
            updateCount = eventCounter
        )
    }

    private fun releaseRenderResources(reason: String) {
        Log.i(TAG, "releaseRenderResources (reason=$reason): Releasing Presentation and VirtualDisplay")

        val pres = presentation
        presentation = null
        if (pres != null) {
            try {
                pres.cleanup()
            } catch (e: Exception) {
                Log.w(TAG, "releaseRenderResources: Error cleaning up presentation", e)
            }
        }

        val vd = virtualDisplay
        virtualDisplay = null
        val surface = activeSurface
        activeSurface = null

        if (vd != null) {
            try {
                vd.surface = null
                vd.release()
                Log.i(TAG, "VirtualDisplay released cleanly")
            } catch (e: Exception) {
                Log.w(TAG, "releaseRenderResources: Error releasing VirtualDisplay", e)
            }
        }
        try {
            surface?.release()
        } catch (e: Exception) {
            Log.w(TAG, "releaseRenderResources: Error releasing host Surface", e)
        }

        currentWidth = 0
        currentHeight = 0
        currentDpi = 0
        currentDisplayId = -1
    }

    private fun runOnMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    companion object {
        private const val TAG = "DriveDeckPoC"
        private const val VIRTUAL_DISPLAY_NAME = "DriveDeckCarVirtualDisplay"
    }
}
