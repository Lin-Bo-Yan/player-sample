package tw.com.lig.sdk.player

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.media.MediaRecorder.AudioSource.MIC
import android.os.Environment
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.sceneform.SceneView
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Video Recorder class handles recording the contents of a SceneView. It uses MediaRecorder to
 * encode the video. The quality settings can be set explicitly or simply use the CamcorderProfile
 * class to select a predefined set of parameters.
 */
class VideoRecorder {

    // recordingVideoFlag is true when the media recorder is capturing video.
    var isRecording = false
        private set
    private var mediaRecorder: MediaRecorder? = null
    private var videoSize: Size? = null
    private var sceneView: SceneView? = null
    private var videoCodec = 0
    private var videoDirectory: File? = null
    private var videoBaseName: String? = null
    var videoPath: File? = null
        private set
    private var bitRate = DEFAULT_BITRATE
    private var frameRate = DEFAULT_FRAMERATE
    private var encoderSurface: Surface? = null
    private fun setBitRate(bitRate: Int) {
        this.bitRate = bitRate
    }

    private fun setFrameRate(frameRate: Int) {
        this.frameRate = frameRate
    }

    fun setSceneView(sceneView: SceneView?) {
        this.sceneView = sceneView
    }

    /**
     * Toggles the state of video recording.
     *
     * @return true if recording is now active.
     */
    fun onToggleRecord(): Boolean {
        if (isRecording) {
            stopRecordingVideo()
        } else {
            startRecordingVideo()
        }
        return isRecording
    }

    private fun startRecordingVideo() {
        if (mediaRecorder == null) {
            mediaRecorder = MediaRecorder()
        }
        try {
            buildFilename()
            setUpMediaRecorder()
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e(TAG, "Exception setting up recorder", e)
            return
        }

        // Set up Surface for the MediaRecorder
        encoderSurface = mediaRecorder!!.surface
        sceneView!!.startMirroringToSurface(
            encoderSurface, 0, 0, videoSize!!.width, videoSize!!.height
        )
        isRecording = true
    }

    private fun buildFilename() {
        if (videoDirectory == null) {
            videoDirectory = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                    .toString() + "/LigPlaySample"
            )
        }
        if (videoBaseName == null || videoBaseName!!.isEmpty()) {
            videoBaseName = "ARRecord_"
        }
        videoPath = File(
            videoDirectory,
            videoBaseName + Date().toString("yyyy-MM-dd'T'HHmmssZ") + ".mp4"
        )
        val dir = videoPath!!.parentFile
        if (dir != null) {
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }
    }

    private fun stopRecordingVideo() {
        // UI
        isRecording = false
        if (encoderSurface != null) {
            sceneView!!.stopMirroringToSurface(encoderSurface)
            encoderSurface = null
        }
        // Stop recording
        mediaRecorder!!.stop()
        mediaRecorder!!.reset()
    }

    @Throws(IOException::class)
    private fun setUpMediaRecorder() {
        mediaRecorder!!.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mediaRecorder!!.setAudioSource(MediaRecorder.AudioSource.DEFAULT)
        mediaRecorder!!.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorder!!.setVideoEncoder(videoCodec)
        mediaRecorder!!.setVideoEncodingBitRate(bitRate)
        mediaRecorder!!.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
        mediaRecorder!!.setVideoSize(videoSize!!.width, videoSize!!.height)
        mediaRecorder!!.setVideoFrameRate(frameRate)
        mediaRecorder!!.setOutputFile(videoPath!!.absolutePath)
        mediaRecorder!!.prepare()
        try {
            mediaRecorder!!.start()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Exception starting capture: " + e.message, e)
        }
    }

    private fun setVideoSize(width: Int, height: Int) {
        videoSize = Size(width, height)
    }

    fun setVideoQuality(quality: Int, orientation: Int) {
        var profile: CamcorderProfile? = null
        if (CamcorderProfile.hasProfile(quality)) {
            profile = CamcorderProfile.get(quality)
        }
        if (profile == null) {
            // Select a quality  that is available on this device.
            for (level in FALLBACK_QUALITY_LEVELS) {
                if (CamcorderProfile.hasProfile(level)) {
                    profile = CamcorderProfile.get(level)
                    break
                }
            }
        }
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setVideoSize(profile!!.videoFrameWidth, profile.videoFrameHeight)
        } else {
            setVideoSize(profile!!.videoFrameHeight, profile.videoFrameWidth)
        }
        setVideoCodec(profile.videoCodec)
        setBitRate(profile.videoBitRate)
        setFrameRate(profile.videoFrameRate)
    }

    private fun setVideoCodec(videoCodec: Int) {
        this.videoCodec = videoCodec
    }

    companion object {
        private const val TAG = "VideoRecorder"
        private const val DEFAULT_BITRATE = 10000000
        private const val DEFAULT_FRAMERATE = 30
        private val FALLBACK_QUALITY_LEVELS = intArrayOf(
            CamcorderProfile.QUALITY_HIGH,
            CamcorderProfile.QUALITY_2160P,
            CamcorderProfile.QUALITY_1080P,
            CamcorderProfile.QUALITY_720P,
            CamcorderProfile.QUALITY_480P
        )
    }

    fun Date.toString(format: String, timeZone: TimeZone = TimeZone.getDefault()): String {
        val sdf = SimpleDateFormat(format, Locale.ENGLISH) //強制用英文(因 Zeplin 上是英文)
        sdf.timeZone = timeZone
        return sdf.format(this)
    }
}