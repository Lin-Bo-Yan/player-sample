package tw.com.lig.sdk.player

import android.graphics.Typeface
import android.media.CamcorderProfile
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.ar.sceneform.ArSceneView
import tw.com.lig.sdk.scanner.LightID

class ARActivity : AppCompatActivity() {

    private lateinit var arSceneView: ArSceneView
    private lateinit var lightId: LightID
    private lateinit var controller: ARSceneformController
    // 拍照功能物件
    private lateinit var photoSaver: PhotoSaver
    // 因為錄影的時候要隱藏，所以需要把所有可以隱藏的Icon放到Array裡面。
    private var recordViewArray = arrayListOf<View>() // 放所有錄影中要隱藏的Icon

    private lateinit var btnLeftUp:ImageButton
    private lateinit var btnRecord:ImageButton
    private lateinit var btnCamera:ImageButton
    private lateinit var llSwitchText: LinearLayout
    private lateinit var tvToggleTakePic: TextView
    private lateinit var tvToggleTakeRec: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ar);
        //隱藏ActionBar
        supportActionBar?.hide()

        arSceneView= findViewById(R.id.ar_scene_view)

        btnLeftUp = findViewById(R.id.btn_left_up)
        btnRecord = findViewById(R.id.btn_record)
        btnCamera = findViewById(R.id.btn_camera)
        llSwitchText = findViewById(R.id.ll_switch_text)
        tvToggleTakePic = findViewById(R.id.tv_toggle_take_pic)
        tvToggleTakeRec = findViewById(R.id.tv_toggle_screen_rec)

        // get Light ID data
        lightId = intent.getParcelableExtra("light-id")!!

        // create 3D viewer
        controller = ARSceneformController(this, arSceneView, lightId)

        setupDefaultSceneUi()

        initPhotoSaver(arSceneView)

        recorder = initRecorder()

    }

    override fun onResume() {
        super.onResume()
        controller.start()
    }

    override fun onPause() {
        super.onPause()
        controller.pause()
    }

    override fun onStop() {
        super.onStop()
        controller.stop()
    }

    override fun onDestroy() {
        controller.destroy()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // hide system bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController ?: return
            controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsets.Type.statusBars())
            controller.hide(WindowInsets.Type.navigationBars())
        }
    }

    // 初始化拍照
    private fun initPhotoSaver(sceneView: ArSceneView) {
        photoSaver = PhotoSaver(this).apply {
            setSceneView(sceneView)
            setPhotoSaveCallback(object : PhotoSaver.ShotCallback {
                override fun onSuccess(uri: Uri?) {
                    if (uri == null) return
                    runOnUiThread {
                        Toast.makeText(this@ARActivity, "已儲存！", Toast.LENGTH_SHORT ).show()
                    }
                }
                override fun onFail(msg: String) {
                    Toast.makeText(this@ARActivity, msg, Toast.LENGTH_SHORT ).show()
                }
            })
        }
    }

    // 螢幕錄影套件載入
    private lateinit var recorder: VideoRecorder

    private fun initRecorder() = VideoRecorder().apply {
        val orientation = resources.configuration.orientation
        setVideoQuality(CamcorderProfile.QUALITY_2160P, orientation)
        setSceneView(arSceneView)
    }

    private fun setupDefaultSceneUi() {
        //錄影中隱藏的UI
        recordViewArray = arrayListOf(btnLeftUp, llSwitchText)
        //拍照錄影文字切換
        initSwitchEvent()
        //回到掃描頁
        btnLeftUp.setOnClickListener {
            finish()
        }
        //拍照
        btnCamera.setOnClickListener {
            photoSaver.takePhoto()
        }
        //錄影
        btnRecord.setOnClickListener {
            try {
                recorder.onToggleRecord()

                if (!recorder.isRecording) {
                    Toast.makeText(this@ARActivity, "已儲存！", Toast.LENGTH_SHORT ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ARActivity, "螢幕錄影出現其他問題", Toast.LENGTH_SHORT ).show()
                if (recorder.isRecording) {
                    recorder.onToggleRecord()
                }
            }
            onRecording(recorder.isRecording)
        }
    }

    private fun initSwitchEvent() {
        tvToggleTakePic.setOnClickListener {
            setControlIcon(true)
            tvToggleTakePic.setBold(true)
            tvToggleTakeRec.setBold(false)
        }

        tvToggleTakeRec.setOnClickListener {
            setControlIcon(false)
            tvToggleTakePic.setBold(false)
            tvToggleTakeRec.setBold(true)
        }
    }

    private fun setControlIcon(isTakePic: Boolean) {
        btnCamera.isVisible = isTakePic
        btnRecord.isVisible = !isTakePic
    }

    private fun TextView.setBold(isBold: Boolean) {
        setTypeface(Typeface.SANS_SERIF, if (isBold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun onRecording(isRecording: Boolean) {
        recordViewArray.forEach { it.isVisible = !isRecording } // 若錄影中，不顯示，非錄影中，則顯示
    }
}