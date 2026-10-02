package com.ibmempire.recorder.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.HandlerThread
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import kotlin.math.abs

class FloatingCameraOverlay(private val context: Context) {
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val cameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private var root: FrameLayout? = null
    private var textureView: TextureView? = null
    private var params: WindowManager.LayoutParams? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null

    private val cameraThread = HandlerThread("IBMEmpireFloatingCamera").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)

    fun show() {
        if (root != null) return
        if (!Settings.canDrawOverlays(context)) return
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val texture = TextureView(context).apply {
            scaleX = -1f
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int
                ) {
                    openFrontCamera(surface)
                }

                override fun onSurfaceTextureSizeChanged(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int
                ) = Unit

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    closeCamera()
                    return true
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
            }
        }

        val container = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(18).toFloat()
                setColor(Color.BLACK)
                setStroke(dp(2), Color.argb(220, 56, 189, 248))
            }
            clipToOutline = true
            outlineProvider = ViewOutlineProviderCompat.rounded(dp(18).toFloat())
            elevation = dp(12).toFloat()
            contentDescription = "Floating front camera"
            addView(
                texture,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }

        val layoutParams = WindowManager.LayoutParams(
            dp(150),
            dp(200),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(16)
            y = dp(260)
        }

        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0

        container.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = layoutParams.x
                    startY = layoutParams.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    if (abs(dx) > dp(2) || abs(dy) > dp(2)) {
                        layoutParams.x = (startX - dx).coerceAtLeast(0)
                        layoutParams.y = (startY + dy).coerceAtLeast(0)
                        runCatching {
                            windowManager.updateViewLayout(container, layoutParams)
                        }
                    }
                    true
                }
                else -> true
            }
        }

        root = container
        textureView = texture
        params = layoutParams
        windowManager.addView(container, layoutParams)
    }

    private fun openFrontCamera(surfaceTexture: SurfaceTexture) {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val cameraId = runCatching {
            cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_FRONT
            }
        }.getOrNull() ?: return

        surfaceTexture.setDefaultBufferSize(720, 960)
        val surface = Surface(surfaceTexture)

        runCatching {
            cameraManager.openCamera(
                cameraId,
                object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cameraDevice = camera
                        createPreviewSession(camera, surface)
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        if (cameraDevice === camera) cameraDevice = null
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        camera.close()
                        if (cameraDevice === camera) cameraDevice = null
                    }
                },
                cameraHandler
            )
        }
    }

    private fun createPreviewSession(camera: CameraDevice, surface: Surface) {
        runCatching {
            camera.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        val request = camera
                            .createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                            .apply { addTarget(surface) }
                            .build()
                        runCatching {
                            session.setRepeatingRequest(request, null, cameraHandler)
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        runCatching { session.close() }
                    }
                },
                cameraHandler
            )
        }
    }

    private fun closeCamera() {
        runCatching { captureSession?.stopRepeating() }
        runCatching { captureSession?.close() }
        captureSession = null
        runCatching { cameraDevice?.close() }
        cameraDevice = null
    }

    fun hide() {
        closeCamera()
        root?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        root = null
        textureView = null
        params = null
    }

    fun release() {
        hide()
        cameraThread.quitSafely()
    }
}

private class ViewOutlineProviderCompat(
    private val radius: Float
) : android.view.ViewOutlineProvider() {
    companion object {
        fun rounded(radius: Float) = ViewOutlineProviderCompat(radius)
    }

    override fun getOutline(view: View, outline: android.graphics.Outline) {
        outline.setRoundRect(0, 0, view.width, view.height, radius)
    }
}
