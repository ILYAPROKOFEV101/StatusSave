package com.statusvideo.statussaver.ui.preview

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.statusvideo.statussaver.R
import com.statusvideo.statussaver.domain.model.MediaType
import com.statusvideo.statussaver.domain.model.Status
import com.statusvideo.statussaver.ui.statuslist.StatusListViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    statuses: List<Status>,
    initialIndex: Int,
    onBack: () -> Unit,
    viewModel: StatusListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val pagerState = rememberPagerState(initialPage = initialIndex) { statuses.size }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is com.statusvideo.statussaver.ui.statuslist.StatusListEffect.ShowMessage ->
                    snackbarHostState.showSnackbar(effect.message)
                else -> Unit
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            R.string.preview_position,
                            pagerState.currentPage + 1,
                            statuses.size
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.7f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            val currentStatus = statuses.getOrNull(pagerState.currentPage)
            if (currentStatus != null) {
                PreviewBottomBar(
                    status = currentStatus,
                    isSaving = state.selectedIds.contains(currentStatus.id),
                    onSave = {
                        viewModel.onIntent(
                            com.statusvideo.statussaver.ui.statuslist.StatusListIntent.ToggleSelect(currentStatus.id)
                        )
                        viewModel.onIntent(
                            com.statusvideo.statussaver.ui.statuslist.StatusListIntent.SaveSelected
                        )
                    },
                    onShare = {
                        shareStatus(context, currentStatus)
                    }
                )
            }
        },
        containerColor = Color.Black
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            reverseLayout = true,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) { page ->
            val status = statuses[page]
            when (status.type) {
                MediaType.IMAGE -> ImagePage(status)
                MediaType.VIDEO -> VideoPage(status, isActive = page == pagerState.currentPage)
            }
        }
    }
}

@Composable
private fun ImagePage(status: Status) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(status.uri)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun VideoPage(status: Status, isActive: Boolean) {
    val context = LocalContext.current
    val exoPlayer = remember(status.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(status.uri))
            prepare()
            playWhenReady = false
        }
    }

    LaunchedEffect(isActive) {
        if (isActive) exoPlayer.play() else exoPlayer.pause()
    }

    DisposableEffect(status.id) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    // Keep touch free for pager swipes.
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun PreviewBottomBar(
    status: Status,
    isSaving: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    var saveTriggered by remember { mutableStateOf(false) }
    val saveButtonColor by animateColorAsState(
        targetValue = if (saveTriggered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
        animationSpec = tween(300),
        label = "saveColor"
    )

    BottomAppBar(
        containerColor = Color.Black.copy(alpha = 0.8f),
        contentColor = Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = onShare,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.share), modifier = Modifier.padding(start = 6.dp))
            }

            FilledTonalButton(
                onClick = {
                    saveTriggered = true
                    onSave()
                },
                enabled = !isSaving,
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.save), modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

private fun shareStatus(context: android.content.Context, status: Status) {
    val shareUri = try {
        if (status.uri.scheme == "content") {
            status.uri
        } else {
            val file = File(status.uri.path ?: return)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    } catch (e: Exception) {
        return
    }

    val mimeType = if (status.type == MediaType.IMAGE) "image/*" else "video/*"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, shareUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(intent, context.getString(R.string.share_via))
    )
}
