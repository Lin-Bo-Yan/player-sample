package tw.com.lig.sdk.player

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import tw.com.lig.sdk.scanner.LiGScanner
import tw.com.lig.sdk.scanner.LightID
import tw.com.lig.sdk.scanner.ScannerStatusListener
import tw.com.lig.sdk.scanner.Vector3

class MainActivity: Activity() {

    private var surfaceView: LiGSurfaceView? = null
    private var opened = false

    private val sLackPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    private fun updateLightIDMessage(id: LightID) {
        val builder = StringBuilder();

        var status = "";
        when (id.status) {
            LightID.Status.READY -> status = "READY"
            LightID.Status.NOT_DETECTED -> status = "NOT_DETECTED"
            LightID.Status.NOT_DECODED -> status = "NOT_DECODED"
            LightID.Status.INVALID_POSITION -> status = "INVALID_POSITION"
            LightID.Status.NOT_REGISTERED -> status = "NOT_REGISTERED"
            LightID.Status.INVALID_POSITION_TOO_CLOSE -> status = "INVALID_POSITION_TOO_CLOSE"
            LightID.Status.INVALID_POSITION_UNKNOWN -> status = "INVALID_POSITION_UNKNOWN"
        }
        builder.append("Status: $status\n")

        if (id.isDetected) {
            builder.append("Detection: ${id.detectionTime} ms\n")
            builder.append("Decoded: ${id.decodedTime} ms\n")
        }

        if (id.isReady) {
            builder.append(String.format("Rotation: [ %.2f %.2f %.2f ]\n", id.rotation.x, id.rotation.y, id.rotation.z))
            builder.append(String.format("Translation: [ %.2f %.2f %.2f ]\n", id.translation.x, id.translation.y, id.translation.z))
            builder.append(String.format("Position:  [ %.2f %.2f %.2f ]\n", id.position.x, id.position.y, id.position.z))
        }

        findViewById<TextView>(R.id.lightid_message).text = builder.toString()
    }

    private fun updateCommandMessage(msg: String) {
        val view = findViewById<TextView>(R.id.command_msg)
        val origin = view.text
        val combine = "${origin}\n${msg}"
        view.text = combine
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQ_CODE) {
            if (requestCode == PERMISSION_REQ_CODE && !LiGScanner.isRunning()) {
                // start scanner
                LiGScanner.start()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_main);
        surfaceView = findViewById(R.id.surface_view)
        findViewById<TextView>(R.id.app_version).text = BuildConfig.VERSION_NAME

        // receive scanner status
        LiGScanner.setStatusListener { status ->
            runOnUiThread { updateCommandMessage("onStatus > $status") }
            if (status == ScannerStatusListener.Status.AUTHENTICATION_OK) {
                val token = LiGScanner.getAccessToken()
                runOnUiThread { updateCommandMessage("Access Token(${token.length}) > $token") }
            }
        }

        // receive scan result
        LiGScanner.setResultListener { ids ->
            if (ids.isNotEmpty()) {
                surfaceView?.let {

//                    val lightId = LightID(
//                        status = 1,
//                        coordinateX = 10.0f,
//                        coordinateY = 10.0f,
//                        isDetected = true,
//                        deviceId = 123456789L,
//                        detectionTime = System.currentTimeMillis(),
//                        decodedTime = System.currentTimeMillis() - 1000,
//                        isReady = true,
//                        rotation = Vector3(0.0f, 1.0f, 0.0f),
//                        translation = Vector3(0.0f, 1.0f, 0.0f),
//                        position = Vector3(0.0f, 1.0f, 0.0f)
//                    )

                    val lightId = ids[0]
                    it.send(lightId)


                    runOnUiThread {
                        updateLightIDMessage(lightId)
                        updateCommandMessage("lightId > $lightId")
                    }

                    if (lightId.isReady && !opened) {
                        opened = true
                        val intent = Intent(this, ARActivity::class.java)
                        intent.putExtra("light-id", lightId)
                        startActivity(intent)
                    }
                }
            }
        }

        // show the scanner version and UUID
        updateCommandMessage("UUID > ${LiGScanner.getUUID()}")
        updateCommandMessage("SDK Version > ${LiGScanner.version}")
    }

    override fun onStart() {
        super.onStart()
        findViewById<LiGSurfaceView>(R.id.surface_view).startDrawing()
        requestPermissions(sLackPermissions, PERMISSION_REQ_CODE)
    }

    override fun onStop() {
        super.onStop()
        findViewById<LiGSurfaceView>(R.id.surface_view).stopDrawing()

        // stop scanner
        LiGScanner.stop()

        // clear message
        findViewById<TextView>(R.id.command_msg).text = ""

        opened = false
    }

    companion object {
        private const val PERMISSION_REQ_CODE = 10202
    }
}