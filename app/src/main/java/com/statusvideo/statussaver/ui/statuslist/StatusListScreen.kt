package com.statusvideo.statussaver.ui.statuslist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.decode.VideoFrameDecoder
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.statusvideo.statussaver.R
import com.statusvideo.statussaver.domain.model.MediaType
import com.statusvideo.statussaver.domain.model.Status

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusListScreen(
    onStatusClick: (List<Status>, Int) -> Unit,
    viewModel: StatusListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is StatusListEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (state.selectedIds.isNotEmpty()) {
                        Text(stringResource(R.string.selected_count, state.selectedIds.size))
                    } else {
                        Text(stringResource(R.string.statuses_title))
                    }
                },
                actions = {
                    if (state.selectedIds.isNotEmpty()) {
                        Button(
                            onClick = { viewModel.onIntent(StatusListIntent.SaveSelected) },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(stringResource(R.string.save_selected))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = state.isLoading)

        SwipeRefresh(
            state = swipeRefreshState,
            onRefresh = { viewModel.onIntent(StatusListIntent.Refresh) },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                state.error != null -> ErrorState(
                    message = state.error!!,
                    onRetry = { viewModel.onIntent(StatusListIntent.Refresh) }
                )
                state.statuses.isEmpty() && !state.isLoading -> EmptyState()
                else -> StatusGrid(
                    statuses = state.statuses,
                    selectedIds = state.selectedIds,
                    onItemClick = { status, index ->
                        if (state.selectedIds.isNotEmpty()) {
                            viewModel.onIntent(StatusListIntent.ToggleSelect(status.id))
                        } else {
                            onStatusClick(state.statuses, index)
                        }
                    },
                    onItemLongClick = { status ->
                        viewModel.onIntent(StatusListIntent.ToggleSelect(status.id))
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StatusGrid(
    statuses: List<Status>,
    selectedIds: Set<String>,
    onItemClick: (Status, Int) -> Unit,
    onItemLongClick: (Status) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(statuses, key = { it.id }) { status ->
            val isSelected = status.id in selectedIds
            val index = statuses.indexOf(status)
            StatusThumbnail(
                status = status,
                isSelected = isSelected,
                onClick = { onItemClick(status, index) },
                onLongClick = { onItemLongClick(status) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StatusThumbnail(
    status: Status,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .then(
                if (isSelected) {
                    Modifier.border(
                        BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
                        RoundedCornerShape(6.dp)
                    )
                } else Modifier
            )
    ) {
        val context = LocalContext.current
        val imageRequest = ImageRequest.Builder(context)
            .data(status.uri)
            .apply {
                if (status.type == MediaType.VIDEO) {
                    decoderFactory { result, options, _ ->
                        VideoFrameDecoder(result.source, options)
                    }
                }
            }
            .crossfade(true)
            .build()

        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        if (status.type == MediaType.VIDEO) {
            Icon(
                imageVector = Icons.Filled.PlayCircle,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.Center)
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            )
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(Color.White, CircleShape)
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.no_statuses_found),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(R.string.no_statuses_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp)
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.error_loading),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(8.dp)
        )
        Button(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.retry))
        }
    }
}

