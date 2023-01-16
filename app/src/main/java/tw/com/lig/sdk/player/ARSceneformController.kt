package tw.com.lig.sdk.player

import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.Scene
import com.google.ar.sceneform.math.Matrix

import kotlinx.coroutines.*

import tw.com.lig.sdk.player.ar.LiGScene
import tw.com.lig.sdk.player.model.ArObjectModel
import tw.com.lig.sdk.scanner.LiGScanner
import tw.com.lig.sdk.scanner.LightID
import tw.com.lig.sdk.scanner.Transform


class ARSceneformController(
    private val activity: AppCompatActivity,
    private val sceneView: ArSceneView,
    private val lightId: LightID) : Scene.OnUpdateListener {

    private var isStarted = false
    private var isDestroyed = false
    private var modelPlaced = false
    private val scope = MainScope()

    init {
        sceneView.scene.addOnUpdateListener(this)

        // close plane renderer
        sceneView?.planeRenderer?.isVisible = false
        sceneView?.scene?.camera?.nearClipPlane = 0.001f
        sceneView?.scene?.camera?.farClipPlane = 100f
    }

    private fun createARSession(session: Session): Config {
        return Config(session).apply {
            focusMode = Config.FocusMode.AUTO
            augmentedFaceMode = Config.AugmentedFaceMode.DISABLED
            cloudAnchorMode = Config.CloudAnchorMode.DISABLED
            updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            lightEstimationMode = Config.LightEstimationMode.DISABLED
            depthMode = Config.DepthMode.DISABLED
            planeFindingMode = Config.PlaneFindingMode.DISABLED
        }
    }

    fun start() {
        if (isStarted || isDestroyed)
            return

        if (sceneView.session == null) {
            val session = Session(activity)
            val config = createARSession(session)
            session.configure(config)
            sceneView.session = session
        }

        // start
        sceneView.resume()
        isStarted = true
    }

    fun pause(){}

    fun stop() {
        if (!isStarted || isDestroyed)
            return

        isStarted = false
        sceneView.pause()

        LiGPlayer.context.unload()
    }

    fun destroy() {
        scope.cancel()
        isDestroyed = true
        sceneView.session?.close()

    }

    override fun onUpdate(frameTime: FrameTime?) {
        if (sceneView.session == null || sceneView.arFrame == null)
            return

        if (modelPlaced) {
            sceneView.scene?.camera?.let { LiGPlayer.context.visibilityCheckWith(it.worldPosition) }
        }

        if (!modelPlaced && sceneView.arFrame?.camera?.trackingState == TrackingState.TRACKING) {
            modelPlaced = true

            playerInit()
        }
    }

    private fun playerInit() {
        val cameraPose = Transform()
        sceneView.arFrame?.camera?.displayOrientedPose?.toMatrix(cameraPose.data, 0)
        val transform = lightId.transform(cameraPose)

        LiGCoordinateSystem.getLightTagTransform(
            lightId.deviceId,
            Matrix(transform.data)
        ) { position, rotation ->
            MainScope().launch {
                val anchor = LiGPlayer.context.origin
                anchor.worldPosition = position
                anchor.worldRotation = rotation
                sceneView.scene.addChild(anchor)
            }
        }

        val accessToken = LiGScanner.getAccessToken()
        LiGScene.readFromCloud(lightId.deviceId, accessToken) { payload ->
            MainScope().launch {
                LiGPlayer.context.ligScene =
                    payload.scenes.first().arObjects?.map { arObject -> ArObjectModel(arObject) }
                        ?.let { it1 -> LiGScene(it1) }

                LiGPlayer.context.load(activity)
                LiGPlayer.context.configureSceneView(sceneView)
            }
        }
    }
}