package tw.com.lig.sdk.player

import android.app.Application
import tw.com.lig.sdk.scanner.LiGScanner

class MainApplication: Application() {

    companion object {
        private const val SDK_PRODUCT_KEY = "C7BE6-E2A01-66949-B3009-D04E0"
    }

    override fun onCreate() {
        super.onCreate()
        LiGScanner.initialize(this, SDK_PRODUCT_KEY)
    }

    override fun onTerminate() {
        super.onTerminate()
        LiGScanner.deinitialize()
    }
}