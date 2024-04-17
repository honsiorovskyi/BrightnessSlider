package fr.netstat.brightnessslider

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log

class Torch(s: AccessibilityService): CameraManager.TorchCallback(), AutoCloseable {
    private val cameraManager: CameraManager
    private val cameraId: String
    private var torchActive: Boolean = false

    init {
        cameraManager = s.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraId = findFlashCameraId(cameraManager)

        cameraManager.registerTorchCallback(this, null)
    }

    private fun findFlashCameraId(cameraManager: CameraManager): String {
        val firstCameraWithFlash = cameraManager.cameraIdList.find { camera ->
            cameraManager.getCameraCharacteristics(camera).keys.any { key ->
                key == CameraCharacteristics.FLASH_INFO_AVAILABLE
            }
        }

        if (firstCameraWithFlash != null) {
            return firstCameraWithFlash
        }

        Log.v("ERROR","Camera with flashlight not found")

        return ""
    }

    fun torch(level: Int) {
        try {
            if (!torchActive) {
                if (level != -1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    cameraManager.turnOnTorchWithStrengthLevel(cameraId, level)
                } else {
                    cameraManager.setTorchMode(cameraId, true)
                }
            } else {
                cameraManager.setTorchMode(cameraId, false)
            }
        } catch (e: Exception) {
            Log.v("ERROR", e.stackTraceToString())
        }
    }

    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
        torchActive = enabled
    }

    override fun close() {
        cameraManager.unregisterTorchCallback(this)
    }
}
