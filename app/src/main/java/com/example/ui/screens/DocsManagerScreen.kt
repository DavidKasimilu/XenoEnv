package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DocFileType
import com.example.data.DocFormatFilter
import com.example.data.DocumentFileItem
import com.example.data.DocumentFolderItem
import com.example.data.DocumentRepository
import com.example.data.LoadedDocumentContent
import com.example.data.StructuredDocBlock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocsManagerScreen(
    folders: List<DocumentFolderItem>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onOpenDocumentContent: suspend (DocumentFileItem) -> LoadedDocumentContent,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var hasFullStorageAccess by remember {
        mutableStateOf(DocumentRepository.hasAllFilesPermission(context))
    }
    var hasReadPermission by remember {
        mutableStateOf(DocumentRepository.hasReadStoragePermission(context))
    }

    val readStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasReadPermission = granted || DocumentRepository.hasReadStoragePermission(context)
        hasFullStorageAccess = DocumentRepository.hasAllFilesPermission(context)
        onRefresh()
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasFullStorageAccess = DocumentRepository.hasAllFilesPermission(context)
        hasReadPermission = DocumentRepository.hasReadStoragePermission(context)
        onRefresh()
    }

    var selectedFolderPath by remember { mutableStateOf<String?>(null) }
    var selectedDocument by remember { mutableStateOf<DocumentFileItem?>(null) }
    var formatFilter by remember { mutableStateOf(DocFormatFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    // Resolve active folder from current folders list
    val selectedFolder = remember(folders, selectedFolderPath) {
        selectedFolderPath?.let { path -> folders.find { it.folderPath == path } }
    }

    // Handle system Back button across the 3 internal levels
    BackHandler {
        when {
            selectedDocument != null -> selectedDocument = null
            selectedFolderPath != null -> {
                selectedFolderPath = null
                searchQuery = ""
            }
            else -> onClose()
        }
    }

    // Filter helper for DocumentFileItem
    fun matchesFilterAndQuery(doc: DocumentFileItem): Boolean {
        val matchesType = when (formatFilter) {
            DocFormatFilter.ALL -> true
            DocFormatFilter.PDF -> doc.fileType == DocFileType.PDF
            DocFormatFilter.WORD -> doc.fileType == DocFileType.DOCX || doc.fileType == DocFileType.DOC
            DocFormatFilter.TXT -> doc.fileType == DocFileType.TXT
        }
        val matchesSearch = searchQuery.isBlank() ||
                doc.name.contains(searchQuery, ignoreCase = true) ||
                doc.folderName.contains(searchQuery, ignoreCase = true)
        return matchesType && matchesSearch
    }

    val filteredFolders = remember(folders, formatFilter, searchQuery) {
        folders.mapNotNull { folder ->
            val matchingDocs = folder.documents.filter { matchesFilterAndQuery(it) }
            if (matchingDocs.isNotEmpty() || (searchQuery.isNotBlank() && folder.folderName.contains(searchQuery, ignoreCase = true))) {
                folder.copy(documents = if (matchingDocs.isNotEmpty()) matchingDocs else folder.documents)
            } else {
                null
            }
        }
    }

    val activeDocument = selectedDocument
    if (activeDocument != null) {
        InAppReadOnlyDocumentViewer(
            document = activeDocument,
            onLoadContent = onOpenDocumentContent,
            onBack = { selectedDocument = null },
            modifier = modifier.fillMaxSize()
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = selectedFolder?.folderName ?: "Docs",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                ReadOnlyBadge()
                            }
                            Text(
                                text = if (selectedFolder != null) {
                                    "${selectedFolder.documents.size} document(s) • ${selectedFolder.displaySubtitle}"
                                } else {
                                    "In-app folders • PDF, Word (.docx/.doc) & TXT reader"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectedFolderPath != null) {
                                selectedFolderPath = null
                                searchQuery = ""
                            } else {
                                onClose()
                            }
                        },
                        modifier = Modifier.testTag("docs_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            hasFullStorageAccess = DocumentRepository.hasAllFilesPermission(context)
                            hasReadPermission = DocumentRepository.hasReadStoragePermission(context)
                            onRefresh()
                        },
                        modifier = Modifier.testTag("docs_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Scan device folders"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Device File Access Permission Banner (if full storage permission not yet granted)
            AnimatedVisibility(visible = !hasFullStorageAccess) {
                StoragePermissionCard(
                    hasBasicRead = hasReadPermission,
                    onGrantPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            DocumentRepository.createManageAllFilesIntent(context)?.let { intent ->
                                manageStorageLauncher.launch(intent)
                            }
                        } else {
                            readStorageLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                    }
                )
            }

            // Search bar & Format filter chips
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            if (selectedFolder == null) "Search device folders or documents..."
                            else "Search files in ${selectedFolder.folderName}..."
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("docs_search_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    DocFormatFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = formatFilter == filter,
                            onClick = { formatFilter = filter },
                            label = { Text(filter.label) },
                            leadingIcon = if (formatFilter == filter) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            modifier = Modifier.testTag("docs_filter_${filter.name.lowercase(Locale.ROOT)}")
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            if (isLoading && folders.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Scanning device folders for PDF, Word & TXT files...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (selectedFolder == null) {
                // LEVEL 1: IN-APP DEVICE FOLDERS LIST
                if (filteredFolders.isEmpty()) {
                    EmptyDocsPlaceholder(
                        title = "No matching document folders",
                        subtitle = "Try switching the format filter to 'All Files' or tap Refresh to rescan your device folders."
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("docs_folders_list")
                    ) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp)
                            ) {
                                Text(
                                    text = "DEVICE DOCUMENT FOLDERS (${filteredFolders.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Tap a folder to view files",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        items(filteredFolders, key = { it.folderPath }) { folder ->
                            DocumentFolderCard(
                                folder = folder,
                                onClick = {
                                    selectedFolderPath = folder.folderPath
                                    searchQuery = ""
                                }
                            )
                        }
                    }
                }
            } else {
                // LEVEL 2: DOCUMENTS INSIDE SELECTED FOLDER
                val docsInFolder = remember(selectedFolder, formatFilter, searchQuery) {
                    selectedFolder.documents.filter { matchesFilterAndQuery(it) }
                }

                if (docsInFolder.isEmpty()) {
                    EmptyDocsPlaceholder(
                        title = "No matching files in ${selectedFolder.folderName}",
                        subtitle = "Change the filter chip above or clear your search query to see all documents in this folder."
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("docs_files_list")
                    ) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp)
                            ) {
                                Text(
                                    text = "FILES IN ${selectedFolder.folderName.uppercase(Locale.ROOT)} (${docsInFolder.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Tap to open Read-Only",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        items(docsInFolder, key = { it.id }) { docItem ->
                            DocumentFileRowCard(
                                item = docItem,
                                onClick = { selectedDocument = docItem }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadOnlyBadge() {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "Read Only",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "READ ONLY",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun StoragePermissionCard(
    hasBasicRead: Boolean,
    onGrantPermission: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = Icons.Filled.FolderOpen,
                    contentDescription = "File Access Permission",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Device File Access Permission",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (hasBasicRead) {
                        "Allow full file access to discover PDF, Word, and TXT folders across all device directories."
                    } else {
                        "Grant storage permission so Docs can list all folders on your device containing PDF, DOC, DOCX, and TXT files."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onGrantPermission,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.testTag("docs_grant_storage_permission_btn")
            ) {
                Text("Allow", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DocumentFolderCard(
    folder: DocumentFolderItem,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("docs_folder_card_${folder.folderName}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF2563EB),
                                Color(0xFF1D4ED8)
                            )
                        )
                    )
            ) {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = folder.folderName,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.folderName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = folder.displaySubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (folder.pdfCount > 0) {
                        FormatCountPill(label = "${folder.pdfCount} PDF", color = Color(0xFFDC2626))
                    }
                    if (folder.wordCount > 0) {
                        FormatCountPill(label = "${folder.wordCount} WORD", color = Color(0xFF2563EB))
                    }
                    if (folder.txtCount > 0) {
                        FormatCountPill(label = "${folder.txtCount} TXT", color = Color(0xFF059669))
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${folder.totalCount}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Open folder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FormatCountPill(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun DocumentFileRowCard(
    item: DocumentFileItem,
    onClick: () -> Unit
) {
    val badgeColor = when (item.fileType) {
        DocFileType.PDF -> Color(0xFFDC2626)
        DocFileType.DOCX, DocFileType.DOC -> Color(0xFF2563EB)
        DocFileType.TXT -> Color(0xFF059669)
    }
    val dateFormatted = remember(item.lastModified) {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(item.lastModified))
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("docs_file_card_${item.name}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(badgeColor.copy(alpha = 0.15f))
                    .border(1.dp, badgeColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            ) {
                Text(
                    text = item.fileType.badge,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = badgeColor
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${item.fileType.label} • ${item.formattedSize} • $dateFormatted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            ReadOnlyBadge()
        }
    }
}

@Composable
private fun EmptyDocsPlaceholder(
    title: String,
    subtitle: String
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * In-App Read-Only Document Viewer supporting PDF pages, Word (.docx/.doc) structured pages,
 * and Text (.txt) files with zoom/font controls and in-document keyword search.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InAppReadOnlyDocumentViewer(
    document: DocumentFileItem,
    onLoadContent: suspend (DocumentFileItem) -> LoadedDocumentContent,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var loadedContent by remember(document.id) { mutableStateOf<LoadedDocumentContent?>(null) }
    var textFontSizeSp by remember { mutableIntStateOf(15) }
    var pdfZoomPercent by remember { mutableIntStateOf(100) }
    var showLineNumbers by remember { mutableStateOf(true) }
    var showFindBar by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }

    LaunchedEffect(document.id) {
        loadedContent = null
        loadedContent = onLoadContent(document)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = document.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            ReadOnlyBadge()
                        }
                        Text(
                            text = "${document.fileType.label} • ${document.formattedSize} • ${document.folderName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("doc_viewer_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to folder"
                        )
                    }
                },
                actions = {
                    if (document.fileType == DocFileType.PDF) {
                        IconButton(
                            onClick = { pdfZoomPercent = (pdfZoomPercent - 25).coerceAtLeast(100) },
                            enabled = pdfZoomPercent > 100
                        ) {
                            Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom Out")
                        }
                        Text(
                            text = "$pdfZoomPercent%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { pdfZoomPercent = (pdfZoomPercent + 25).coerceAtMost(225) },
                            enabled = pdfZoomPercent < 225
                        ) {
                            Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom In")
                        }
                    } else {
                        IconButton(
                            onClick = { showFindBar = !showFindBar }
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = "Find in document")
                        }
                        IconButton(
                            onClick = { textFontSizeSp = (textFontSizeSp - 2).coerceAtLeast(11) }
                        ) {
                            Icon(Icons.Filled.TextDecrease, contentDescription = "Decrease font size")
                        }
                        IconButton(
                            onClick = { textFontSizeSp = (textFontSizeSp + 2).coerceAtMost(26) }
                        ) {
                            Icon(Icons.Filled.TextIncrease, contentDescription = "Increase font size")
                        }
                        if (document.fileType == DocFileType.TXT) {
                            IconButton(
                                onClick = { showLineNumbers = !showLineNumbers }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FormatListNumbered,
                                    contentDescription = "Toggle line numbers"
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(visible = showFindBar && document.fileType != DocFileType.PDF) {
                OutlinedTextField(
                    value = findQuery,
                    onValueChange = { findQuery = it },
                    placeholder = { Text("Find text in document...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (findQuery.isNotEmpty()) {
                            IconButton(onClick = { findQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            when (val content = loadedContent) {
                null -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Opening ${document.name} (Read-Only)...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is LoadedDocumentContent.ErrorContent -> {
                    EmptyDocsPlaceholder(
                        title = "Cannot Open Document",
                        subtitle = content.message
                    )
                }

                is LoadedDocumentContent.PdfContent -> {
                    PdfReadOnlyPagesView(
                        pdfContent = content,
                        zoomPercent = pdfZoomPercent
                    )
                }

                is LoadedDocumentContent.WordContent -> {
                    WordReadOnlyDocumentView(
                        wordContent = content,
                        fontSizeSp = textFontSizeSp,
                        findQuery = findQuery
                    )
                }

                is LoadedDocumentContent.TextContent -> {
                    TextReadOnlyDocumentView(
                        textContent = content,
                        fontSizeSp = textFontSizeSp,
                        showLineNumbers = showLineNumbers,
                        findQuery = findQuery
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfReadOnlyPagesView(
    pdfContent: LoadedDocumentContent.PdfContent,
    zoomPercent: Int
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E2430))
    ) {
        Surface(
            color = Color(0xFF111827),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "PDF Reader • ${pdfContent.pageCount} Page(s)",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
                Text(
                    text = "Zoom: $zoomPercent% • Read-Only Mode",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9CA3AF)
                )
            }
        }

        val horizontalScroll = rememberScrollState()
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (zoomPercent > 100) Modifier.horizontalScroll(horizontalScroll)
                    else Modifier
                )
                .testTag("pdf_viewer_pages_list")
        ) {
            itemsIndexed(pdfContent.pages) { index, bitmap ->
                val baseWidthDp = 360.dp
                val scaledWidth = baseWidthDp * (zoomPercent / 100f)

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "PDF Page ${index + 1}",
                            contentScale = ContentScale.FillWidth,
                            modifier = if (zoomPercent > 100) {
                                Modifier.width(scaledWidth)
                            } else {
                                Modifier.fillMaxWidth()
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Page ${index + 1} of ${pdfContent.pageCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFD1D5DB)
                    )
                }
            }
        }
    }
}

@Composable
private fun WordReadOnlyDocumentView(
    wordContent: LoadedDocumentContent.WordContent,
    fontSizeSp: Int,
    findQuery: String
) {
    val filteredBlocks = remember(wordContent.blocks, findQuery) {
        if (findQuery.isBlank()) {
            wordContent.blocks
        } else {
            wordContent.blocks.filter { block ->
                when (block) {
                    is StructuredDocBlock.Heading -> block.text.contains(findQuery, ignoreCase = true)
                    is StructuredDocBlock.Paragraph -> block.text.contains(findQuery, ignoreCase = true)
                    is StructuredDocBlock.BulletItem -> block.text.contains(findQuery, ignoreCase = true)
                    is StructuredDocBlock.TableRowBlock -> block.cells.any { it.contains(findQuery, ignoreCase = true) }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Word Document Reader • ${wordContent.wordCount} words",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Font: ${fontSizeSp}sp • Read-Only",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag("word_viewer_content_list")
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        if (filteredBlocks.isEmpty()) {
                            Text(
                                text = wordContent.plainTextFallback.ifBlank { "No matching text found." },
                                fontSize = fontSizeSp.sp,
                                lineHeight = (fontSizeSp + 7).sp
                            )
                        } else {
                            filteredBlocks.forEach { block ->
                                when (block) {
                                    is StructuredDocBlock.Heading -> {
                                        Text(
                                            text = block.text,
                                            fontSize = if (block.level == 1) (fontSizeSp + 6).sp else (fontSizeSp + 3).sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            lineHeight = (fontSizeSp + 10).sp
                                        )
                                        if (block.level == 1) {
                                            HorizontalDivider(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }

                                    is StructuredDocBlock.Paragraph -> {
                                        Text(
                                            text = block.text,
                                            fontSize = fontSizeSp.sp,
                                            lineHeight = (fontSizeSp + 7).sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    is StructuredDocBlock.BulletItem -> {
                                        Row(
                                            verticalAlignment = Alignment.Top,
                                            modifier = Modifier.padding(start = 8.dp)
                                        ) {
                                            Text(
                                                text = "•  ",
                                                fontSize = fontSizeSp.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = block.text,
                                                fontSize = fontSizeSp.sp,
                                                lineHeight = (fontSizeSp + 6).sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    is StructuredDocBlock.TableRowBlock -> {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    if (block.isHeader)
                                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                                    else
                                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .border(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.outlineVariant,
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .padding(10.dp)
                                        ) {
                                            block.cells.forEach { cell ->
                                                Text(
                                                    text = cell,
                                                    fontSize = (fontSizeSp - 1).sp,
                                                    fontWeight = if (block.isHeader) FontWeight.Bold else FontWeight.Normal,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .padding(end = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextReadOnlyDocumentView(
    textContent: LoadedDocumentContent.TextContent,
    fontSizeSp: Int,
    showLineNumbers: Boolean,
    findQuery: String
) {
    val displayedLines = remember(textContent.lines, findQuery) {
        val indexed = textContent.lines.mapIndexed { idx, line -> (idx + 1) to line }
        if (findQuery.isBlank()) {
            indexed
        } else {
            indexed.filter { (_, line) -> line.contains(findQuery, ignoreCase = true) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "${textContent.lineCount} lines • ${textContent.wordCount} words • ${textContent.charCount} chars",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "UTF-8 • Read-Only",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag("txt_viewer_lines_list")
        ) {
            items(displayedLines, key = { it.first }) { (lineNumber, lineText) ->
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (showLineNumbers) {
                        Text(
                            text = String.format(Locale.US, "%4d", lineNumber),
                            fontSize = (fontSizeSp - 2).sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .width(44.dp)
                                .padding(end = 8.dp)
                        )
                    }
                    Text(
                        text = lineText.ifEmpty { " " },
                        fontSize = fontSizeSp.sp,
                        lineHeight = (fontSizeSp + 6).sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
