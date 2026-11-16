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
                onClick = { takePhoto() }, containerColor = p.accent, contentColor = p.onAccent, shape = RoundedCornerShape(18.dp),
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
                    FilesScreen(folder, filter, lastSync, vm::setFilter, vm::open, vm::openCrumb, vm::refresh, { vm.createFolder(it) }, { picker.launch("*/*") })
                }
                Tab.Uploads -> {
                    val rows by vm.uploads.collectAsStateWithLifecycle()
                    val wifi by vm.wifiOnly.collectAsStateWithLifecycle()
                    UploadsScreen(rows, wifi, vm::cancelUpload, vm::clearFinishedUploads) { takePhoto() }
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
                    )
                }
            }
        }
    }
}
