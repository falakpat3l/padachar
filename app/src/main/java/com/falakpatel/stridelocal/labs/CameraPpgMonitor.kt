package com.falakpatel.stridelocal.labs

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Camera2 driver for [PpgEstimator]. Rear camera + torch, smallest YUV size, ~30 fps. */
class CameraPpgMonitor(private val context: Context) {
    private val _bpm = MutableStateFlow<Int?>(null)
    val bpm: StateFlow<Int?> = _bpm

    private val estimator = PpgEstimator()
    private var thread: HandlerThread? = null
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null

    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // checked by hasPermission()
    fun start(): Boolean {
        if (!hasPermission()) return false
        val manager = context.getSystemService(CameraManager::class.java)
        val id = manager.cameraIdList.firstOrNull { cid ->
            val c = manager.getCameraCharacteristics(cid)
            c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK &&
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return false
        val chars = manager.getCameraCharacteristics(id)
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return false
        // Smallest YUV size is plenty for an average brightness and costs the least battery.
        val size = map.getOutputSizes(ImageFormat.YUV_420_888).minByOrNull { it.width * it.height } ?: return false
        val fpsRange = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?.filter { it.upper >= 30 }?.minByOrNull { it.upper - it.lower } ?: Range(15, 30)

        val t = HandlerThread("ppg").also { it.start() }
        thread = t
        val handler = Handler(t.looper)
        estimator.reset()
        val r = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 3)
        reader = r
        r.setOnImageAvailableListener({ ir ->
            val img = ir.acquireLatestImage() ?: return@setOnImageAvailableListener
            val y = img.planes[0].buffer
            var sum = 0L
            var n = 0
            var i = 0
            while (i < y.remaining()) { sum += (y.get(i).toInt() and 0xFF); n++; i += 4 } // sample every 4th byte
            estimator.add(img.timestamp / 1e9, sum.toDouble() / n)
            img.close()
            _bpm.value = estimator.bpm()
        }, handler)

        manager.openCamera(id, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                camera = device
                @Suppress("DEPRECATION")
                device.createCaptureSession(listOf(r.surface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(s: CameraCaptureSession) {
                        session = s
                        val req = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(r.surface)
                            set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange)
                        }
                        s.setRepeatingRequest(req.build(), null, handler)
                    }
                    override fun onConfigureFailed(s: CameraCaptureSession) = stop()
                }, handler)
            }
            override fun onDisconnected(device: CameraDevice) = stop()
            override fun onError(device: CameraDevice, error: Int) = stop()
        }, handler)
        return true
    }

    fun stop() {
        runCatching { session?.close() }
        runCatching { camera?.close() }
        runCatching { reader?.close() }
        thread?.quitSafely()
        session = null; camera = null; reader = null; thread = null
    }
}
