package com.example.ui

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.view.View
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import kotlinx.coroutines.flow.SharedFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortalScreen(
    isVisible: Boolean,
    uiState: PortalUiState,
    commands: SharedFlow<PortalCommand>,
    onBackPressedInWeb: () -> Unit,
    onForwardPressedInWeb: () -> Unit,
    onReloadPressed: () -> Unit,
    onLoadUrl: (String) -> Unit,
    onClearCache: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onExitFullscreen: () -> Unit,
    onWebPageStarted: (String?) -> Unit,
    onWebPageFinished: (String?, String?, Boolean, Boolean) -> Unit,
    onWebProgressChanged: (Int, String?) -> Unit,
    onWebError: (String, String?) -> Unit,
    onWebHistoryChanged: (Boolean, Boolean) -> Unit,
    onOpenOfflineIndex: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        filePathCallback?.onReceiveValue(
            if (uris.isNotEmpty()) uris.toTypedArray() else null
        )
        filePathCallback = null
    }

    val downloadStartedMsg = stringResource(R.string.portal_download_started)
    val downloadFailedMsg = stringResource(R.string.portal_download_failed)
    val cacheClearedMsg = stringResource(R.string.portal_cache_cleared)

    val webView = remember(context) {
        createConfiguredWebView(
            context = context,
            initialUrl = PortalUrls.TAHFIDZ_HOME,
            onPageStarted = onWebPageStarted,
            onPageFinished = onWebPageFinished,
            onProgressChanged = onWebProgressChanged,
            onError = onWebError,
            onHistoryChanged = onWebHistoryChanged,
            onShowFileChooser = { callback ->
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                runCatching {
                    fileChooserLauncher.launch(arrayOf("*/*"))
                }.onFailure {
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = null
                }
                true
            },
            onDownloadRequest = { url, userAgent, contentDisposition, mimetype ->
                val started = startSecureDownload(
                    context = context,
                    url = url,
                    userAgent = userAgent,
                    contentDisposition = contentDisposition,
                    mimetype = mimetype
                )
                onShowMessage(if (started) downloadStartedMsg else downloadFailedMsg)
            }
        )
    }

    DisposableEffect(webView) {
        onDispose {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
            webView.destroy()
        }
    }

    LaunchedEffect(commands) {
        commands.collect { cmd ->
            when (cmd) {
                is PortalCommand.LoadUrl -> webView.loadUrl(cmd.url)
                PortalCommand.Reload -> webView.reload()
                PortalCommand.GoBack -> if (webView.canGoBack()) webView.goBack()
                PortalCommand.GoForward -> if (webView.canGoForward()) webView.goForward()
                PortalCommand.ClearCache -> {
                    webView.clearCache(true)
                    webView.reload()
                    onShowMessage(cacheClearedMsg)
                }
            }
        }
    }

    BackHandler(enabled = isVisible && (uiState.canGoBack || uiState.isFullscreen)) {
        if (uiState.canGoBack) {
            onBackPressedInWeb()
        } else if (uiState.isFullscreen) {
            onExitFullscreen()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AnimatedVisibility(
            visible = !uiState.isFullscreen,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut()
        ) {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    title = {
                        Column {
                            Text(
                                text = uiState.pageTitle,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = formatDisplayUrl(uiState.currentUrl),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    actions = {
                        if (uiState.canGoBack) {
                            IconButton(
                                onClick = onBackPressedInWeb,
                                modifier = Modifier.testTag("portal_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.portal_back)
                                )
                            }
                        }
                        if (uiState.canGoForward) {
                            IconButton(
                                onClick = onForwardPressedInWeb,
                                modifier = Modifier.testTag("portal_forward_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = stringResource(R.string.portal_forward)
                                )
                            }
                        }
                        IconButton(
                            onClick = onReloadPressed,
                            modifier = Modifier.testTag("portal_reload_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.portal_reload)
                            )
                        }
                        IconButton(
                            onClick = onToggleFullscreen,
                            modifier = Modifier.testTag("portal_fullscreen_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Fullscreen,
                                contentDescription = stringResource(R.string.portal_fullscreen_enter)
                            )
                        }
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.testTag("portal_more_menu_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = stringResource(R.string.portal_menu_more)
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.portal_home_shortcut)) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Home, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onLoadUrl(PortalUrls.TAHFIDZ_HOME)
                                    },
                                    modifier = Modifier.testTag("menu_tahfidz_home")
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.portal_mq_root)) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Bookmark, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onLoadUrl(PortalUrls.MQ_ROOT)
                                    },
                                    modifier = Modifier.testTag("menu_mq_root")
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.portal_dasister_root)) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Bookmark, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onLoadUrl(PortalUrls.DASISTER_MAIN)
                                    },
                                    modifier = Modifier.testTag("menu_dasister_root")
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.portal_share)) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Share, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        shareUrl(context, uiState.currentUrl, uiState.pageTitle)
                                    },
                                    modifier = Modifier.testTag("menu_share_link")
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.portal_open_browser)) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        openInExternalBrowser(context, uiState.currentUrl)
                                    },
                                    modifier = Modifier.testTag("menu_open_browser")
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.portal_clear_cache)) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onClearCache()
                                    },
                                    modifier = Modifier.testTag("menu_clear_cache")
                                )
                            }
                        }
                    }
                )
                if (uiState.isLoading && uiState.progress in 1..99) {
                    LinearProgressIndicator(
                        progress = { uiState.progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                } else if (uiState.isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(
                    if (uiState.isFullscreen) {
                        Modifier.windowInsetsPadding(WindowInsets.statusBars)
                    } else {
                        Modifier
                    }
                )
        ) {
            AndroidView(
                factory = { webView },
                update = { view ->
                    view.visibility = if (isVisible) View.VISIBLE else View.GONE
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("portal_webview")
            )

            if (uiState.hasError) {
                PortalOfflineFallback(
                    failingUrl = uiState.currentUrl,
                    errorDescription = uiState.errorDescription,
                    onRetry = onReloadPressed,
                    onOpenOfflineIndex = onOpenOfflineIndex
                )
            }

            if (uiState.isFullscreen) {
                Surface(
                    onClick = onExitFullscreen,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .testTag("portal_exit_fullscreen_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FullscreenExit,
                            contentDescription = stringResource(R.string.portal_fullscreen_exit),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.portal_fullscreen_exit),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PortalOfflineFallback(
    failingUrl: String,
    errorDescription: String,
    onRetry: () -> Unit,
    onOpenOfflineIndex: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        OutlinedCard(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.widthIn(max = 460.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.portal_error_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.portal_error_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                if (errorDescription.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "$failingUrl\n($errorDescription)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("portal_retry_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.portal_retry),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = onOpenOfflineIndex,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("portal_open_offline_index_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.portal_go_offline_tools))
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createConfiguredWebView(
    context: Context,
    initialUrl: String,
    onPageStarted: (String?) -> Unit,
    onPageFinished: (String?, String?, Boolean, Boolean) -> Unit,
    onProgressChanged: (Int, String?) -> Unit,
    onError: (String, String?) -> Unit,
    onHistoryChanged: (Boolean, Boolean) -> Unit,
    onShowFileChooser: (ValueCallback<Array<Uri>>) -> Boolean,
    onDownloadRequest: (String, String, String, String) -> Unit
): WebView {
    return WebView(context).apply {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(this, true)

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            allowFileAccess = false
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
        }

        setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            onDownloadRequest(url, userAgent, contentDisposition, mimetype)
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                onProgressChanged(newProgress, view?.title)
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                if (filePathCallback == null) return false
                return onShowFileChooser(filePathCallback)
            }
        }

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase() ?: return false
                val host = uri.host?.lowercase() ?: ""

                if (scheme == "https" && (host == "dasister.com" || host.endsWith(".dasister.com"))) {
                    return false
                }
                if (scheme == "http" && (host == "dasister.com" || host.endsWith(".dasister.com"))) {
                    val upgraded = uri.buildUpon().scheme("https").build().toString()
                    view?.loadUrl(upgraded)
                    return true
                }

                // External links or deep links (WhatsApp, mailto, tel, external SSO)
                return runCatching {
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    true
                }.getOrDefault(false)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                onPageStarted(url)
                if (view != null) {
                    onHistoryChanged(view.canGoBack(), view.canGoForward())
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                CookieManager.getInstance().flush()
                onPageFinished(
                    url,
                    view?.title,
                    view?.canGoBack() ?: false,
                    view?.canGoForward() ?: false
                )
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    val desc = error?.description?.toString() ?: "Kesalahan jaringan"
                    onError(desc, request.url?.toString())
                }
            }
        }

        loadUrl(initialUrl)
    }
}

private fun startSecureDownload(
    context: Context,
    url: String,
    userAgent: String,
    contentDisposition: String,
    mimetype: String
): Boolean {
    return runCatching {
        val uri = Uri.parse(url)
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
        val request = DownloadManager.Request(uri).apply {
            setMimeType(mimetype)
            val cookie = CookieManager.getInstance().getCookie(url)
            if (!cookie.isNullOrEmpty()) {
                addRequestHeader("Cookie", cookie)
            }
            addRequestHeader("User-Agent", userAgent)
            setTitle(fileName)
            setDescription("Unduhan dari Portal MQ Tahfidz")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        }
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        true
    }.getOrElse {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}

private fun shareUrl(context: Context, url: String, title: String) {
    runCatching {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "$title\n$url")
        }
        context.startActivity(Intent.createChooser(shareIntent, "Bagikan Tautan Portal"))
    }
}

private fun openInExternalBrowser(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

private fun formatDisplayUrl(url: String): String {
    return url.removePrefix("https://").removePrefix("http://").trimEnd('/')
}
