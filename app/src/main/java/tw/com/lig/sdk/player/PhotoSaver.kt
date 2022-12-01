package tw.com.lig.sdk.player

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Log
import android.view.PixelCopy
import android.view.View
import androidx.annotation.RequiresApi
import com.google.ar.sceneform.ArSceneView
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*


class PhotoSaver(private var context: Context) {

    private var sceneView: ArSceneView? = null
    private var frameView: View? = null
    private var callback: ShotCallback? = null

    private val handler = PhotoSaveHandler()

    private var filename: String = ""

    interface ShotCallback {
        fun onSuccess(uri: Uri?)
        fun onFail(msg: String)
    }

    /**
     * 設定拍照事件回呼。
     * @param   callback    拍照事件回呼接口
     * @return  無
     */
    fun setPhotoSaveCallback(callback: ShotCallback) {
        this.callback = callback
    }

//    /**
//     * 設定相框畫面。
//     * @param   view   相框畫面的UI元件
//     * @return  無
//     */
//    fun setFrameView(view: View?) {
//        Log.e("Saver", "$view")
//        this.frameView = view
//    }

    /**
     * 設定 AR 場景的畫面。
     * @param   sceneView   AR 場景的畫面元件
     * @return  無
     */
    fun setSceneView(sceneView: ArSceneView?) {
        Log.e("Saver", "$sceneView")
        this.sceneView = sceneView
    }

    private fun generateFilename(): String {
        val date = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date())
        return "IMG_$date.jpg"
    }

    /**
     * 儲存照片(for Android 10以上版本)。
     * @param   bitmap      相片點陣圖物件
     * @param   filename    相片檔名
     * @return  無
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    @Throws(IOException::class)
    fun saveBitmapToDiskQ(bitmap: Bitmap, filename: String) {
        val outputStream: OutputStream?
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)!!
        outputStream = resolver.openOutputStream(uri)

        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        outputStream?.flush()
        outputStream?.close()

        val msg = handler.obtainMessage(SUCCESS)
        handler.sendMessage(msg)
    }

    /**
     * 儲存照片(for Android 10以下版本)。
     * @param   bitmap      相片點陣圖物件
     * @param   filename    相片檔名
     * @return  無
     */
    @Throws(IOException::class)
    fun saveBitmapToDisk(bitmap: Bitmap, filename: String) {
        val outputStream: OutputStream?
        val imagesDir = "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)}"
        val file = File(imagesDir)
        if (!file.exists()) file.mkdir()

        val image = File(imagesDir, filename)
        outputStream = FileOutputStream(image)

        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        outputStream.flush()
        outputStream.close()

        MyMediaScannerConnectionClient(context, image)

        val msg = handler.obtainMessage(SUCCESS)
        handler.sendMessage(msg)
    }

    /**
     * 拍照。
     * @return  無
     */
    fun takePhoto() {
        if (sceneView == null) return
        filename = generateFilename()

        // Create a bitmap the size of the scene view.
        val bitmap = Bitmap.createBitmap(sceneView!!.width, sceneView!!.height, Bitmap.Config.ARGB_8888)

        // Create a handler thread to offload the processing of the image.
        val handlerThread = HandlerThread("PixelCopier")
        handlerThread.start()
        // Make the request to copy.
        PixelCopy.request(sceneView!!, bitmap, { copyResult ->
            if (copyResult == PixelCopy.SUCCESS) {
                try {
                    val frame = getBitmapFromView()
                    if (frame != null) {
                        val canvas = Canvas(bitmap)
                        canvas.drawBitmap(frame, 0f, 0f, null)
                        canvas.save()
                        canvas.restore()
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        saveBitmapToDiskQ(bitmap, filename)
                    } else {
                        saveBitmapToDisk(bitmap, filename)
                    }
                } catch (e: IOException) {
                    val msg = handler.obtainMessage(FAILURE)
                    handler.sendMessage(msg)
                    return@request
                }
            } else {
                val msg = handler.obtainMessage(FAILURE)
                handler.sendMessage(msg)
            }
            handlerThread.quitSafely()
        }, Handler(handlerThread.looper))
    }

    /**
     * Copy View to Canvas and return bitMap and fill it with default color
     */
    private fun getBitmapFromView(): Bitmap? {
        if (frameView == null) return null
        val bitmap = Bitmap.createBitmap(frameView!!.width, frameView!!.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)
        frameView!!.draw(canvas)
        return bitmap
    }

    private fun getFileUriImage(filename: String): Uri? {
        return if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getFileUriImageQ(filename)
        } else {
            val file = File("${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)}/$filename")
            Uri.fromFile(file)
        }
    }

    @SuppressLint("Range")
    private fun getFileUriImageQ(filename: String): Uri? {
        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            MediaStore.Images.Media.DISPLAY_NAME + "=? ",
            arrayOf(filename),
            null
        )
        return if (cursor != null && cursor.moveToFirst()) {
            val id = cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns._ID))
            val baseUri = Uri.parse("content://media/external/images/media")
            Uri.withAppendedPath(baseUri, "$id")
        } else null
    }

    private inner class MyMediaScannerConnectionClient(ctx: Context?, file: File) :
        MediaScannerConnection.MediaScannerConnectionClient {
        private val mFilename: String = file.absolutePath
        private val mMimetype: String? = null
        private val mConn: MediaScannerConnection = MediaScannerConnection(ctx, this)
        override fun onMediaScannerConnected() {
            mConn.scanFile(mFilename, mMimetype)
        }

        override fun onScanCompleted(path: String?, uri: Uri?) {
            mConn.disconnect()
        }

        init {
            mConn.connect()
        }
    }

    private inner class PhotoSaveHandler: Handler(Looper.getMainLooper()) {

        override fun handleMessage(msg: Message) {
            when (msg.what) {
                SUCCESS -> {
                    val fileUri = getFileUriImage(filename)
                    callback?.onSuccess(fileUri)
                }
                FAILURE -> {
                    callback?.onFail("Failed...")
                }
            }
        }
    }

    companion object {
        private const val SUCCESS = 0
        private const val FAILURE = 1
    }
}