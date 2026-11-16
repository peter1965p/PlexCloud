package com.paeffgen.plexcloud.backup

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Energiesparen: Samsung (und andere) beenden Apps im Hintergrund sehr früh, dann läuft die Sicherung erst,
 * wenn PlexCloud wieder geöffnet wird. Mit der Ausnahme vom Energiesparen läuft sie zuverlässig.
 * Die App fragt nur, Android zeigt dazu einen eigenen Dialog; ablehnen ist jederzeit möglich.
 */
object Battery {
    fun unrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    @SuppressLint("BatteryLife") // App wird nicht über den Play Store verteilt; Sicherung ist genau der vorgesehene Fall
    fun requestIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    /** Falls der Dialog nicht geht: Liste der Akku-Einstellungen */
    fun settingsIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}
