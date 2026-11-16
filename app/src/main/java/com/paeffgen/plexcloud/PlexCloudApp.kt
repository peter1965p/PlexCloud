package com.paeffgen.plexcloud

import android.app.Application
import com.paeffgen.plexcloud.auth.AuthRepository
import com.paeffgen.plexcloud.auth.TokenStore
import com.paeffgen.plexcloud.data.PlexCloudApi
import com.paeffgen.plexcloud.data.SettingsRepository
import com.paeffgen.plexcloud.update.UpdateRepository
import com.paeffgen.plexcloud.update.UpdateWorker
import com.paeffgen.plexcloud.upload.Notifications
import com.paeffgen.plexcloud.upload.Uploads

class PlexCloudApp : Application() {
    val settings by lazy { SettingsRepository(this) }
    val auth by lazy { AuthRepository(TokenStore(this)) }
    val api by lazy { PlexCloudApi(auth) }
    val uploads by lazy { Uploads(this) }
    val updates by lazy { UpdateRepository(this) }

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        UpdateWorker.schedule(this)
    }
}
