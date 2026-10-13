package com.omnira

import android.app.Application
import com.cloudinary.android.MediaManager

class OmniraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        MediaManager.init(this, mapOf("cloud_name" to "dpdg31jra"))
    }
}
