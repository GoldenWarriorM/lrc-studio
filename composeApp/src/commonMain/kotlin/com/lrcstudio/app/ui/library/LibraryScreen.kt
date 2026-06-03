package com.lrcstudio.app.ui.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lrcstudio.app.data.model.Song
import com.lrcstudio.app.util.fabBottomPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onSongClick: (Song) -> Unit,
    onImportLrcFile: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var showNewSongSheet by remember { mutableStateOf(false) }
    var showPasteDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }

    var showSearch by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.onSearchQueryChange("")
        viewModel.refresh()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            TopAppBar(
                title = {
                    val searchAlpha by animateFloatAsState(
                        if (showSearch) 1f else 0f,
                        label = "searchAlpha"
                    )
                    val titleAlpha by animateFloatAsState(
                        if (showSearch) 0f else 1f,
                        label = "titleAlpha"
                    )
                    Box(contentAlignment = Alignment.CenterStart) {
                        Text(
                            text = "LRC Studio",
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.alpha(titleAlpha)
                        )
                        TextField(
                            value = state.searchQuery,
                            onValueChange = viewModel::onSearchQueryChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 8.dp)
                                .alpha(searchAlpha),
                            placeholder = { Text("Search songs...") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        showSearch = !showSearch
                        if (!showSearch) viewModel.onSearchQueryChange("")
                    }) {
                        Icon(
                            imageVector = if (showSearch) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (showSearch) "Close search" else "Search"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            Box(modifier = Modifier.padding(bottom = fabBottomPadding())) {
                ExtendedFloatingActionButton(
                    onClick = { showNewSongSheet = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("New Song") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.songs.isEmpty() -> {
                    EmptyLibrary(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = fabBottomPadding())
                    )
                }
                state.filteredSongs.isEmpty() -> {
                    EmptySearchResult(
                        query = state.searchQuery,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = fabBottomPadding())
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = fabBottomPadding()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
                    ) {
                        items(state.filteredSongs, key = { it.id }) { song ->
                            SongCard(
                                song = song,
                                onClick = { onSongClick(song) },
                                onDelete = { showDeleteConfirm = song.id }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNewSongSheet) {
        NewSongSheet(
            onPasteLyrics = {
                showNewSongSheet = false
                showPasteDialog = true
            },
            onImportLrc = {
                showNewSongSheet = false
                onImportLrcFile()
            },
            onDismiss = { showNewSongSheet = false }
        )
    }

    if (showPasteDialog) {
        PasteLyricsDialog(
            onConfirm = { lyrics ->
                viewModel.createSongFromPastedText(lyrics)
                showPasteDialog = false
            },
            onDismiss = { showPasteDialog = false }
        )
    }

    showDeleteConfirm?.let { songId ->
        val song = state.songs.find { it.id == songId }
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            shape = RoundedCornerShape(24.dp),
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(40.dp)
                )
            },
            title = {
                Text(
                    text = "Delete song?",
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Text(
                    text = "\"${song?.title ?: "Untitled"}\" will be permanently deleted. This action cannot be undone.",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                Button(
     			  	onClick = {
            			viewModel.deleteSong(songId)
            			showDeleteConfirm = null
        			},
        			colors = ButtonDefaults.buttonColors(
            			containerColor = MaterialTheme.colorScheme.errorContainer
        			),
        			shape = RoundedCornerShape(12.dp)
    			) {
        			Text("Delete", color = MaterialTheme.colorScheme.onErrorContainer)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewSongSheet(
    onPasteLyrics: () -> Unit,
    onImportLrc: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Add New Song",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            NewSongOption(
                icon = Icons.Default.EditNote,
                title = "Paste lyrics text",
                subtitle = "Type or paste song lyrics line by line",
                onClick = onPasteLyrics
            )

            NewSongOption(
                icon = Icons.Default.FileOpen,
                title = "Import LRC file",
                subtitle = "Open an existing .lrc file",
                onClick = onImportLrc
            )
        }
    }
}

@Composable
private fun NewSongOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PasteLyricsDialog(
    onConfirm: (lyrics: String) -> Unit,
    onDismiss: () -> Unit
) {
    var lyrics by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text("Paste Lyrics", style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = lyrics,
                    onValueChange = { lyrics = it },
                    label = { Text("Paste or type lyrics (one line per verse)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 300.dp),
                    maxLines = 20,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(lyrics) },
                enabled = lyrics.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun EmptyLibrary(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.outlineVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No songs yet",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap + to add lyrics\nand create your first song",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EmptySearchResult(query: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.outlineVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No songs found",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "No results for \"$query\"",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongCard(
    song: Song,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${song.lyrics.size} lines",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
