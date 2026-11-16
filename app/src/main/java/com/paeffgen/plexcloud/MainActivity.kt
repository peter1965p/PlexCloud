package com.paeffgen.plexcloud

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.result.IntentSenderRequest
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.paeffgen.plexcloud.backup.BackupWorker
import com.paeffgen.plexcloud.backup.Battery
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.paeffgen.plexcloud.ui.BackupSection
import com.paeffgen.plexcloud.ui.BackupUi
import com.paeffgen.plexcloud.ui.CaptureSheet
import com.paeffgen.plexcloud.ui.AlbumDialog
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paeffgen.plexcloud.auth.AuthRepository
import com.paeffgen.plexcloud.data.ROOT
import com.paeffgen.plexcloud.ui.DetailScreen
import com.paeffgen.plexcloud.ui.FilesScreen
import com.paeffgen.plexcloud.ui.ItemRow
import com.paeffgen.plexcloud.ui.LoginScreen
import com.paeffgen.plexcloud.ui.MainViewModel
import com.paeffgen.plexcloud.ui.Message
import com.paeffgen.plexcloud.ui.PIcon
import com.paeffgen.plexcloud.ui.SectionLabel
import com.paeffgen.plexcloud.ui.SystemScreen
import com.paeffgen.plexcloud.ui.Tab
import com.paeffgen.plexcloud.ui.UiEvent
import com.paeffgen.plexcloud.ui.UpdateDialog
import com.paeffgen.plexcloud.ui.UpdateState
import com.paeffgen.plexcloud.ui.UploadsScreen
import com.paeffgen.plexcloud.ui.itemMeta
import com.paeffgen.plexcloud.ui.theme.LocalPlex
import com.paeffgen.plexcloud.ui.theme.PlexTheme
import com.paeffgen.plexcloud.domain.Format

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        if (savedInstanceState == null) vm.checkForUpdates(silent = true)
        setContent {
            val accent by vm.accent.collectAsStateWithLifecycle()
            PlexTheme(accent) { App(vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when {
            intent.action == Intent.ACTION_VIEW && intent.data?.scheme == "plexcloud" -> vm.handleRedirect(intent.data!!)
            intent.action == Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { vm.uploadUris(listOf(it)) }
            intent.action == Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { vm.uploadUris(it) }
            intent.getBooleanExtra(EXTRA_CHECK_UPDATE, false) -> vm.checkForUpdates()
        }
    }

    companion object {
        const val EXTRA_CHECK_UPDATE = "check_update"
    }
}

@Composable
private fun App(vm: MainViewModel) {
    val p = LocalPlex.current
    val ctx = LocalContext.current
    val tokens by vm.tokens.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.uiEvents.collect { e ->
            when (e) {
                is UiEvent.OpenUrl -> runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(e.url))) }
                is UiEvent.Share -> ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(e.mime).putExtra(Intent.EXTRA_STREAM, e.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
                is UiEvent.Toast -> Toast.makeText(ctx, e.text, Toast.LENGTH_LONG).show()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(p.ground)) {
        if (tokens == null) {
            val error by vm.loginError.collectAsStateWithLifecycle()
            val open = { prov: AuthRepository.Provider ->
                CustomTabsIntent.Builder()
                    .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(p.ground.toArgb()).build())
                    .build().launchUrl(ctx, vm.loginUri(prov))
            }
            Scaffold(containerColor = p.ground) { pad -> Box(Modifier.padding(pad)) { LoginScreen(error, { open(AuthRepository.Provider.Google) }, { open(AuthRepository.Provider.Email) }) } }
        } else {
            Main(vm, tokens!!.email)
        }
    }

    if (update is UpdateState.Available || update is UpdateState.Downloading || update is UpdateState.Failed) {
        UpdateDialog(update, vm.currentVersion, onInstall = vm::installUpdate, onDismiss = vm::dismissUpdate)
    }
}

@Composable
private fun Main(vm: MainViewModel, email: String) {
    val p = LocalPlex.current
    val ctx = LocalContext.current
    val tab by vm.tab.collectAsStateWithLifecycle()
    val folder by vm.folder.collectAsStateWithLifecycle()
    val detail by vm.detail.collectAsStateWithLifecycle()

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { vm.photoTaken(it) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { vm.uploadUris(it) }
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT >= 33) notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
    val takePhoto = { runCatching { camera.launch(vm.newPhotoUri()) }.onFailure { Toast.makeText(ctx, "Keine Kamera-App gefunden", Toast.LENGTH_LONG).show() } }

    // Dokumentenscanner (Google Play-Dienste): Kanten, Zuschnitt, bis 20 Seiten, Ergebnis als PDF
    val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val res = GmsDocumentScanningResult.fromActivityResultIntent(r.data)
        val pdf = res?.pdf
        if (r.resultCode == android.app.Activity.RESULT_OK && pdf != null) vm.scannedPdf(pdf.uri, pdf.pageCount)
    }
    val scanDocument = {
        val activity = ctx as android.app.Activity
        GmsDocumentScanning.getClient(
            GmsDocumentScannerOptions.Builder()
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
                .setGalleryImportAllowed(true)
                .setPageLimit(20)
                .build(),
        ).getStartScanIntent(activity)
            .addOnSuccessListener { scanner.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener { Toast.makeText(ctx, "Scanner nicht verfügbar: ${it.message}", Toast.LENGTH_LONG).show() }
        Unit
    }
    var captureChoice by remember { mutableStateOf(false) }
    if (captureChoice) {
        val label = "/" + folder.listing?.path.orEmpty().joinToString("/") { it.name }
        CaptureSheet(label, onDocument = { captureChoice = false; scanDocument() }, onPhoto = { captureChoice = false; takePhoto() }, onDismiss = { captureChoice = false })
    }

    // Sicherung: Galerie-Recht und Ordnerauswahl
    // Album-Dialog: null = zu, true = beim Einschalten, false = Auswahl ändern
    var albumDialog by remember { mutableStateOf<Boolean?>(null) }
    val mediaPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (BackupWorker.hasMediaPermission(ctx)) { vm.loadAlbums(); albumDialog = true }
        else Toast.makeText(ctx, "Ohne Zugriff auf Fotos kann PlexCloud nichts sichern", Toast.LENGTH_LONG).show()
    }
    // Energiesparen: Zustand bei jeder Rückkehr in die App neu lesen (der Nutzer ändert ihn im System-Dialog)
    var batteryOk by remember { mutableStateOf(Battery.unrestricted(ctx)) }
    LifecycleResumeEffect(Unit) { batteryOk = Battery.unrestricted(ctx); onPauseOrDispose { } }
    val askBattery = {
        if (!Battery.unrestricted(ctx)) {
            runCatching { ctx.startActivity(Battery.requestIntent(ctx)) }
                .recoverCatching { ctx.startActivity(Battery.settingsIntent()) }
        }
        Unit
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) { vm.addBackupFolder(uri); askBattery() } }
    albumDialog?.let { enabling ->
        val albums by vm.albums.collectAsStateWithLifecycle()
        val chosen by vm.backupAlbums.collectAsStateWithLifecycle()
        AlbumDialog(
            albums, if (enabling) emptySet() else chosen, enabling,
            onConfirm = { ids, existing ->
                albumDialog = null
                // alle angehakt = „alle“ (dann kommen auch neue Alben automatisch dazu)
                val sel = if (ids.size == albums.size) emptySet() else ids
                if (enabling) { vm.enablePhotoBackup(existing, sel); askBattery() } else vm.setAlbums(sel, existing)
            },
            onDismiss = { albumDialog = null },
        )
    }
    val askPhotos = {
        val perms = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (BackupWorker.hasMediaPermission(ctx)) { vm.loadAlbums(); albumDialog = true } else mediaPermission.launch(perms)
    }

    BackHandler(enabled = detail != null || tab != Tab.Files || folder.folderId != ROOT) { vm.back() }

    Scaffold(
        containerColor = p.ground,
        bottomBar = {
            NavigationBar(containerColor = p.nav, tonalElevation = 0.dp) {
                listOf(
                    Triple(Tab.Files, R.drawable.ic_folder, "Dateien"),
                    Triple(Tab.Uploads, R.drawable.ic_upload, "Uploads"),
                    Triple(Tab.Recent, R.drawable.ic_clock, "Zuletzt"),
                    Triple(Tab.System, R.drawable.ic_server, "System"),
                ).forEach { (t, icon, label) ->
                    NavigationBarItem(
                        selected = tab == t && detail == null,
                        onClick = { vm.closeDetail(); vm.select(t) },
                        icon = { PIcon(icon, null, if (tab == t) p.accent else p.muted) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = p.accentSoft, selectedTextColor = p.text, unselectedTextColor = p.muted),
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == Tab.Files && detail == null) FloatingActionButton(
                onClick = { captureChoice = true }, containerColor = p.accent, contentColor = p.onAccent, shape = RoundedCornerShape(18.dp),
                modifier = Modifier.size(60.dp),
            ) { PIcon(R.drawable.ic_camera, "Foto aufnehmen und hochladen", p.onAccent, 26.dp) }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val d = detail
            if (d != null) {
                DetailScreen(d, vm::closeDetail, { vm.view(it) }, { vm.download(it) }, { vm.share(it) }, { l, on -> vm.setPortal(l, on) })
                return@Box
            }
            when (tab) {
                Tab.Files -> {
                    val filter by vm.filter.collectAsStateWithLifecycle()
                    val lastSync by vm.lastSync.collectAsStateWithLifecycle()
                    val profile by vm.profile.collectAsStateWithLifecycle()
                    FilesScreen(folder, filter, lastSync, vm::setFilter, vm::open, vm::openCrumb, vm::refresh, { vm.createFolder(it) }, { picker.launch("*/*") }, profile, { vm.select(Tab.System) })
                }
                Tab.Uploads -> {
                    val rows by vm.uploads.collectAsStateWithLifecycle()
                    val wifi by vm.wifiOnly.collectAsStateWithLifecycle()
                    UploadsScreen(rows, wifi, vm::cancelUpload, vm::clearFinishedUploads, { vm.cancelBackup() }, { vm.retryFailedUploads() }) { captureChoice = true }
                }
                Tab.Recent -> {
                    val recent by vm.recent.collectAsStateWithLifecycle()
                    Column {
                        SectionLabel("Zuletzt geändert")
                        val items = recent.listing?.items.orEmpty()
                        when {
                            recent.error != null -> Message(R.drawable.ic_cloud, recent.error!!, null, null)
                            items.isEmpty() -> Message(R.drawable.ic_clock, if (recent.loading) "lädt …" else "Noch keine Dateien.", null, null)
                            else -> androidx.compose.foundation.lazy.LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) {
                                items(items.size) { i -> val it = items[i]; ItemRow(it, vm::open, meta = itemMeta(it) + " · " + Format.dateTime(it.updatedAt).takeLast(5)) }
                            }
                        }
                    }
                }
                Tab.System -> {
                    val accent by vm.accent.collectAsStateWithLifecycle()
                    val wifi by vm.wifiOnly.collectAsStateWithLifecycle()
                    val auto by vm.autoUpdate.collectAsStateWithLifecycle()
                    val lat by vm.latencies.collectAsStateWithLifecycle()
                    val lastSync by vm.lastSync.collectAsStateWithLifecycle()
                    val update by vm.update.collectAsStateWithLifecycle()
                    SystemScreen(
                        email, folder.listing?.usage, lat, lastSync, accent, wifi, auto, vm.currentVersion, update,
                        vm::setAccent, vm::setWifiOnly, vm::setAutoUpdate, { vm.checkForUpdates() },
                        { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${BuildConfig.GITHUB_REPO}"))) },
                        vm::signOut,
                        profile = vm.profile.collectAsStateWithLifecycle().value,
                        backup = {
                        val photos by vm.photoBackup.collectAsStateWithLifecycle()
                        val videos by vm.videoBackup.collectAsStateWithLifecycle()
                        val folders by vm.backupFolders.collectAsStateWithLifecycle()
                        val last by vm.lastBackup.collectAsStateWithLifecycle()
                        val chosen by vm.backupAlbums.collectAsStateWithLifecycle()
                        val albums by vm.albums.collectAsStateWithLifecycle()
                        val rows by vm.uploads.collectAsStateWithLifecycle()
                        LaunchedEffect(photos) { if (photos) vm.loadAlbums() }
                        BackupSection(
                            BackupUi(photos, videos, folders, last.first, last.second, batteryOk,
                                albumNames = albums.filter { it.id in chosen }.map { it.name },
                                pending = rows.count { it.backup && !it.state.isFinished }),
                            onPhotos = { on -> if (on) askPhotos() else vm.disablePhotoBackup() },
                            onVideos = { vm.setVideoBackup(it) },
                            onAddFolder = { folderPicker.launch(null) },
                            onRemoveFolder = { vm.removeBackupFolder(it) },
                            onNow = { vm.backupNow() },
                            onBattery = { askBattery() },
                            onAlbums = { vm.loadAlbums(); albumDialog = false },
                            onCancel = { vm.cancelBackup() },
                        )
                    })
                }
            }
        }
    }
}
