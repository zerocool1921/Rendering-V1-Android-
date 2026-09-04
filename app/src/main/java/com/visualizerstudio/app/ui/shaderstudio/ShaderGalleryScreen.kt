package com.visualizerstudio.app.ui.shaderstudio

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.visualizerstudio.app.domain.model.ShaderCategory
import com.visualizerstudio.app.domain.model.ShaderTemplateEntity
import com.visualizerstudio.app.ui.shaderstudio.wizard.Timer3DWizardScreen
import com.visualizerstudio.app.ui.shaderstudio.wizard.SpectrumWizardScreen
import com.visualizerstudio.app.ui.shaderstudio.wizard.OverlayLoopWizardScreen

enum class ShaderStudioDestination { GALLERY, TIMER_3D, SPECTRUM, OVERLAY_LOOP }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShaderStudioNavHost(
    viewModel: ShaderStudioViewModel = hiltViewModel(),
    onClose: () -> Unit
) {
    var destination by remember { mutableStateOf(ShaderStudioDestination.GALLERY) }

    when (destination) {
        ShaderStudioDestination.GALLERY -> ShaderGalleryScreen(
            viewModel = viewModel,
            onCreateTimer3D = { destination = ShaderStudioDestination.TIMER_3D },
            onCreateSpectrum = { destination = ShaderStudioDestination.SPECTRUM },
            onCreateOverlayLoop = { destination = ShaderStudioDestination.OVERLAY_LOOP },
            onClose = onClose
        )
        ShaderStudioDestination.TIMER_3D -> Timer3DWizardScreen(
            viewModel = viewModel,
            onDone = { destination = ShaderStudioDestination.GALLERY }
        )
        ShaderStudioDestination.SPECTRUM -> SpectrumWizardScreen(
            viewModel = viewModel,
            onDone = { destination = ShaderStudioDestination.GALLERY }
        )
        ShaderStudioDestination.OVERLAY_LOOP -> OverlayLoopWizardScreen(
            viewModel = viewModel,
            onDone = { destination = ShaderStudioDestination.GALLERY }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShaderGalleryScreen(
    viewModel: ShaderStudioViewModel,
    onCreateTimer3D: () -> Unit,
    onCreateSpectrum: () -> Unit,
    onCreateOverlayLoop: () -> Unit,
    onClose: () -> Unit
) {
    val gallery by viewModel.gallery.collectAsState()
    var showCreateSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shader Template Studio") },
                navigationIcon = { TextButton(onClick = onClose) { Text("Tutup") } }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateSheet = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Buat Shader") }
            )
        }
    ) { padding ->
        if (gallery.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Belum ada shader. Ketuk \"Buat Shader\" untuk mulai.")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(gallery, key = { it.id }) { entity ->
                    ShaderGalleryCard(entity, viewModel)
                }
            }
        }

        if (showCreateSheet) {
            ModalBottomSheet(onDismissRequest = { showCreateSheet = false }) {
                Column(Modifier.padding(16.dp).padding(bottom = 24.dp)) {
                    Text("Pilih jenis wizard", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    ListItem(
                        headlineContent = { Text("Timer Countdown 3D") },
                        supportingContent = { Text("Angka mundur timbul 3D + teks intro bergantian") },
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).let { m ->
                            m
                        }
                    )
                    TextButton(onClick = { showCreateSheet = false; onCreateTimer3D() }) { Text("Buat Timer 3D →") }
                    Divider(Modifier.padding(vertical = 8.dp))
                    Text("Spectrum Custom — bar/wave/circular/particle")
                    TextButton(onClick = { showCreateSheet = false; onCreateSpectrum() }) { Text("Buat Spectrum →") }
                    Divider(Modifier.padding(vertical = 8.dp))
                    Text("Overlay Effect Loop — hujan/salju/api/kunang-kunang/dll")
                    TextButton(onClick = { showCreateSheet = false; onCreateOverlayLoop() }) { Text("Buat Overlay Loop →") }
                }
            }
        }
    }
}

@Composable
private fun ShaderGalleryCard(entity: ShaderTemplateEntity, viewModel: ShaderStudioViewModel) {
    val thumbnails by viewModel.thumbnails.collectAsState()
    val thumbnailUri = entity.thumbnailUri ?: thumbnails[entity.id]

    LaunchedEffect(entity.id) { viewModel.ensureThumbnail(entity) }

    Card {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            ) {
                if (thumbnailUri != null) {
                    AsyncImage(
                        model = thumbnailUri,
                        contentDescription = entity.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    }
                }
            }
            Column(Modifier.padding(8.dp)) {
                Text(entity.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(entity.category.name, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
