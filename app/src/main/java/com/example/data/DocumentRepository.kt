package com.example.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.Settings
import android.util.Xml
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class DocFormatFilter(val label: String) {
    ALL("All Files"),
    PDF("PDF"),
    WORD("Word / DOC"),
    TXT("TXT")
}

enum class DocFileType(val badge: String, val label: String) {
    PDF("PDF", "PDF Document"),
    DOCX("DOCX", "Word Document"),
    DOC("DOC", "Word 97-2003"),
    TXT("TXT", "Text Document")
}

data class DocumentFileItem(
    val id: String,
    val name: String,
    val filePath: String?,
    val contentUri: Uri?,
    val folderName: String,
    val folderPath: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val extension: String,
    val fileType: DocFileType
) {
    val formattedSize: String
        get() = when {
            sizeBytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", sizeBytes / (1024f * 1024f))
            sizeBytes >= 1024 -> String.format(Locale.US, "%.1f KB", sizeBytes / 1024f)
            else -> "$sizeBytes B"
        }
}

data class DocumentFolderItem(
    val folderPath: String,
    val folderName: String,
    val displaySubtitle: String,
    val documents: List<DocumentFileItem>
) {
    val totalCount: Int get() = documents.size
    val pdfCount: Int get() = documents.count { it.fileType == DocFileType.PDF }
    val wordCount: Int get() = documents.count { it.fileType == DocFileType.DOCX || it.fileType == DocFileType.DOC }
    val txtCount: Int get() = documents.count { it.fileType == DocFileType.TXT }
}

sealed class StructuredDocBlock {
    data class Heading(val text: String, val level: Int = 1) : StructuredDocBlock()
    data class Paragraph(val text: String) : StructuredDocBlock()
    data class BulletItem(val text: String) : StructuredDocBlock()
    data class TableRowBlock(val cells: List<String>, val isHeader: Boolean = false) : StructuredDocBlock()
}

sealed class LoadedDocumentContent {
    data class PdfContent(
        val pageCount: Int,
        val pages: List<Bitmap>,
        val fileSize: Long
    ) : LoadedDocumentContent()

    data class WordContent(
        val blocks: List<StructuredDocBlock>,
        val plainTextFallback: String,
        val wordCount: Int
    ) : LoadedDocumentContent()

    data class TextContent(
        val lines: List<String>,
        val fullText: String,
        val lineCount: Int,
        val wordCount: Int,
        val charCount: Int
    ) : LoadedDocumentContent()

    data class ErrorContent(val message: String) : LoadedDocumentContent()
}

class DocumentRepository(private val context: Context) {

    companion object {
        private val PDF_EXTENSIONS = setOf("pdf")
        private val WORD_EXTENSIONS = setOf("doc", "docx", "rtf", "odt")
        private val TXT_EXTENSIONS = setOf("txt", "md", "csv", "log", "json", "xml", "ini", "cfg")

        val ALL_SUPPORTED_EXTENSIONS: Set<String> =
            PDF_EXTENSIONS + WORD_EXTENSIONS + TXT_EXTENSIONS

        fun resolveDocFileType(extension: String): DocFileType? {
            return when (extension.lowercase(Locale.ROOT)) {
                in PDF_EXTENSIONS -> DocFileType.PDF
                "docx", "odt" -> DocFileType.DOCX
                "doc", "rtf" -> DocFileType.DOC
                in TXT_EXTENSIONS -> DocFileType.TXT
                else -> null
            }
        }

        fun hasAllFilesPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        }

        fun hasReadStoragePermission(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                return true
            }
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

        fun createManageAllFilesIntent(context: Context): Intent? {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                } catch (_: Exception) {
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                }
            } else {
                null
            }
        }
    }

    /**
     * Scans device directories + MediaStore + built-in device document folders and groups
     * all discovered read-only documents (.pdf, .doc, .docx, .txt) by their parent folder.
     */
    suspend fun scanDeviceDocumentFolders(): List<DocumentFolderItem> = withContext(Dispatchers.IO) {
        ensureBuiltInSampleFoldersAndDocs()

        val discoveredByPath = linkedMapOf<String, DocumentFileItem>()

        // 1. Scan built-in app document folders first so there are always rich folders available
        val appDocsRoot = File(context.filesDir, "InAppDeviceDocs")
        if (appDocsRoot.exists()) {
            scanDirectoryRecursively(
                dir = appDocsRoot,
                maxDepth = 3,
                currentDepth = 0,
                results = discoveredByPath
            )
        }

        // 2. Scan app-specific external documents directories if present
        context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.let { extDocs ->
            if (extDocs.exists()) {
                scanDirectoryRecursively(extDocs, 3, 0, discoveredByPath)
            }
        }

        // 3. Scan standard device folders (Documents, Download, root storage) via File API
        val candidateRoots = mutableListOf<File>()
        try {
            val extRoot = Environment.getExternalStorageDirectory()
            if (extRoot != null && extRoot.exists()) {
                candidateRoots.add(File(extRoot, "Documents"))
                candidateRoots.add(File(extRoot, "Download"))
                candidateRoots.add(File(extRoot, "Downloads"))
                candidateRoots.add(File(extRoot, "WhatsApp/Media/WhatsApp Documents"))
                candidateRoots.add(File(extRoot, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents"))
                candidateRoots.add(File(extRoot, "Telegram/Telegram Documents"))
                candidateRoots.add(extRoot)
            }
        } catch (_: Exception) {
        }

        val visitedDirs = mutableSetOf<String>()
        for (root in candidateRoots) {
            if (!root.exists() || !root.canRead()) continue
            val depth = if (root == Environment.getExternalStorageDirectory()) 2 else 4
            scanDirectoryRecursively(
                dir = root,
                maxDepth = depth,
                currentDepth = 0,
                results = discoveredByPath,
                visitedDirs = visitedDirs
            )
        }

        // 4. Query MediaStore.Files for any documents indexed by Android OS
        queryMediaStoreDocuments(discoveredByPath)

        // Group documents by folderPath
        val grouped = discoveredByPath.values.groupBy { it.folderPath }
        grouped.map { (folderPath, docs) ->
            val sortedDocs = docs.sortedWith(
                compareByDescending<DocumentFileItem> { it.lastModified }.thenBy { it.name.lowercase(Locale.ROOT) }
            )
            val folderName = sortedDocs.firstOrNull()?.folderName ?: File(folderPath).name.ifBlank { "Documents" }
            val readableSubtitle = formatFolderSubtitle(folderPath)
            DocumentFolderItem(
                folderPath = folderPath,
                folderName = folderName,
                displaySubtitle = readableSubtitle,
                documents = sortedDocs
            )
        }.sortedWith(
            compareByDescending<DocumentFolderItem> { it.documents.size }
                .thenBy { it.folderName.lowercase(Locale.ROOT) }
        )
    }

    private fun formatFolderSubtitle(folderPath: String): String {
        val internalPrefix = File(context.filesDir, "InAppDeviceDocs").absolutePath
        if (folderPath.startsWith(internalPrefix)) {
            val rel = folderPath.removePrefix(internalPrefix).trimStart('/')
            return "Device Storage / $rel"
        }
        val extRoot = try {
            Environment.getExternalStorageDirectory()?.absolutePath ?: "/storage/emulated/0"
        } catch (_: Exception) {
            "/storage/emulated/0"
        }
        if (folderPath.startsWith(extRoot)) {
            val rel = folderPath.removePrefix(extRoot).trimStart('/')
            return if (rel.isBlank()) "Internal Storage" else "Internal Storage / $rel"
        }
        return folderPath
    }

    private fun scanDirectoryRecursively(
        dir: File,
        maxDepth: Int,
        currentDepth: Int,
        results: MutableMap<String, DocumentFileItem>,
        visitedDirs: MutableSet<String> = mutableSetOf()
    ) {
        if (currentDepth > maxDepth) return
        val canonical = try {
            dir.canonicalPath
        } catch (_: Exception) {
            dir.absolutePath
        }
        if (canonical.contains("/Android/data") || canonical.contains("/Android/obb") || canonical.contains("/.")) {
            return
        }
        if (!dir.isDirectory || !dir.canRead()) return

        val children = try {
            dir.listFiles()
        } catch (_: Exception) {
            null
        } ?: return

        val folderPath = dir.absolutePath
        val folderName = dir.name.ifBlank { "Internal Storage" }

        for (child in children) {
            if (child.name.startsWith(".")) continue
            if (child.isDirectory) {
                val childCanonical = try {
                    child.canonicalPath
                } catch (_: Exception) {
                    child.absolutePath
                }
                if (visitedDirs.add(childCanonical)) {
                    scanDirectoryRecursively(child, maxDepth, currentDepth + 1, results, visitedDirs)
                }
            } else if (child.isFile && child.canRead() && child.length() > 0L) {
                val ext = child.extension.lowercase(Locale.ROOT)
                val docType = resolveDocFileType(ext) ?: continue
                val key = child.absolutePath
                if (!results.containsKey(key)) {
                    results[key] = DocumentFileItem(
                        id = key,
                        name = child.name,
                        filePath = child.absolutePath,
                        contentUri = Uri.fromFile(child),
                        folderName = folderName,
                        folderPath = folderPath,
                        sizeBytes = child.length(),
                        lastModified = child.lastModified(),
                        extension = ext,
                        fileType = docType
                    )
                }
            }
        }
    }

    private fun queryMediaStoreDocuments(results: MutableMap<String, DocumentFileItem>) {
        try {
            val uri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
                MediaStore.Files.FileColumns.RELATIVE_PATH
            )
            val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            context.contentResolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                val dateCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                val bucketCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
                val relPathCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val dataPath = if (dataCol >= 0) cursor.getString(dataCol) else null
                    val rawName = (if (nameCol >= 0) cursor.getString(nameCol) else null)
                        ?: dataPath?.substringAfterLast('/')
                        ?: continue

                    val ext = rawName.substringAfterLast('.', "").lowercase(Locale.ROOT)
                    val docType = resolveDocFileType(ext) ?: continue
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    if (size <= 0L) continue

                    val lastModSec = if (dateCol >= 0) cursor.getLong(dateCol) else 0L
                    val lastModified = if (lastModSec > 0) lastModSec * 1000L else System.currentTimeMillis()
                    val contentUri = ContentUris.withAppendedId(uri, id)

                    val key = if (!dataPath.isNullOrBlank()) dataPath else contentUri.toString()
                    if (results.containsKey(key)) continue

                    val folderPath: String
                    val folderName: String
                    if (!dataPath.isNullOrBlank()) {
                        val parent = File(dataPath).parentFile
                        folderPath = parent?.absolutePath ?: "/storage/emulated/0/Documents"
                        folderName = parent?.name?.ifBlank { "Documents" } ?: "Documents"
                    } else {
                        val rel = if (relPathCol >= 0) cursor.getString(relPathCol)?.trimEnd('/') else null
                        val bucket = if (bucketCol >= 0) cursor.getString(bucketCol) else null
                        folderName = bucket?.ifBlank { null } ?: rel?.substringAfterLast('/') ?: "Documents"
                        folderPath = "/storage/emulated/0/${rel ?: folderName}"
                    }

                    results[key] = DocumentFileItem(
                        id = key,
                        name = rawName,
                        filePath = dataPath,
                        contentUri = contentUri,
                        folderName = folderName,
                        folderPath = folderPath,
                        sizeBytes = size,
                        lastModified = lastModified,
                        extension = ext,
                        fileType = docType
                    )
                }
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Loads and parses any supported document in Read-Only mode for in-app viewing.
     */
    suspend fun openDocumentReadOnly(item: DocumentFileItem): LoadedDocumentContent = withContext(Dispatchers.IO) {
        try {
            when (item.fileType) {
                DocFileType.PDF -> renderPdfDocument(item)
                DocFileType.DOCX -> parseDocxDocument(item)
                DocFileType.DOC -> parseDocDocument(item)
                DocFileType.TXT -> parseTextDocument(item)
            }
        } catch (e: Exception) {
            LoadedDocumentContent.ErrorContent(
                "Unable to render \"${item.name}\": ${e.localizedMessage ?: "Unsupported or corrupted file format"}"
            )
        }
    }

    private fun renderPdfDocument(item: DocumentFileItem): LoadedDocumentContent {
        val targetFile: File = when {
            !item.filePath.isNullOrBlank() && File(item.filePath).canRead() -> File(item.filePath)
            item.contentUri != null -> {
                val tempPdf = File(context.cacheDir, "readonly_preview_${item.id.hashCode()}.pdf")
                context.contentResolver.openInputStream(item.contentUri)?.use { input ->
                    FileOutputStream(tempPdf).use { output ->
                        input.copyTo(output)
                    }
                } ?: return LoadedDocumentContent.ErrorContent("Could not open PDF stream.")
                tempPdf
            }
            else -> return LoadedDocumentContent.ErrorContent("PDF file path not accessible.")
        }

        val pfd = ParcelFileDescriptor.open(targetFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val pages = mutableListOf<Bitmap>()
        val totalPages = renderer.pageCount
        val maxPagesToRender = totalPages.coerceAtMost(40)

        try {
            for (pageIndex in 0 until maxPagesToRender) {
                renderer.openPage(pageIndex).use { page ->
                    val scale = 2.0f
                    val width = (page.width * scale).toInt().coerceIn(600, 1800)
                    val height = (page.height * scale).toInt().coerceIn(800, 2600)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    pages.add(bitmap)
                }
            }
        } finally {
            renderer.close()
            pfd.close()
        }

        return LoadedDocumentContent.PdfContent(
            pageCount = totalPages,
            pages = pages,
            fileSize = item.sizeBytes
        )
    }

    private fun openItemInputStream(item: DocumentFileItem): InputStream? {
        if (!item.filePath.isNullOrBlank()) {
            val f = File(item.filePath)
            if (f.exists() && f.canRead()) {
                return FileInputStream(f)
            }
        }
        if (item.contentUri != null) {
            return context.contentResolver.openInputStream(item.contentUri)
        }
        return null
    }

    private fun parseDocxDocument(item: DocumentFileItem): LoadedDocumentContent {
        val blocks = mutableListOf<StructuredDocBlock>()
        val rawBuilder = StringBuilder()

        openItemInputStream(item)?.use { rawStream ->
            ZipInputStream(rawStream).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    if (entry.name.equals("word/document.xml", ignoreCase = true)) {
                        val parser = Xml.newPullParser()
                        parser.setInput(zip, "UTF-8")

                        var eventType = parser.eventType
                        var currentParagraph = StringBuilder()
                        var currentStyle = ""
                        var inTable = false
                        var currentRowCells = mutableListOf<String>()
                        var currentCellText = StringBuilder()
                        var inTableCell = false
                        var isBulletList = false

                        while (eventType != XmlPullParser.END_DOCUMENT) {
                            when (eventType) {
                                XmlPullParser.START_TAG -> {
                                    val tag = parser.name.substringAfterLast(':')
                                    when (tag) {
                                        "p" -> {
                                            currentParagraph = StringBuilder()
                                            currentStyle = ""
                                            isBulletList = false
                                        }
                                        "pStyle" -> {
                                            val styleVal = parser.getAttributeValue(null, "w:val")
                                                ?: parser.getAttributeValue(0)
                                                ?: ""
                                            currentStyle = styleVal
                                        }
                                        "numPr", "numId" -> {
                                            isBulletList = true
                                        }
                                        "t" -> {
                                            val text = parser.nextText()
                                            if (inTableCell) {
                                                currentCellText.append(text)
                                            } else {
                                                currentParagraph.append(text)
                                            }
                                        }
                                        "tab" -> {
                                            if (!inTableCell) currentParagraph.append("    ")
                                        }
                                        "br", "cr" -> {
                                            if (!inTableCell) currentParagraph.append("\n")
                                        }
                                        "tbl" -> {
                                            inTable = true
                                        }
                                        "tr" -> {
                                            currentRowCells = mutableListOf()
                                        }
                                        "tc" -> {
                                            inTableCell = true
                                            currentCellText = StringBuilder()
                                        }
                                    }
                                }
                                XmlPullParser.END_TAG -> {
                                    val tag = parser.name.substringAfterLast(':')
                                    when (tag) {
                                        "tc" -> {
                                            inTableCell = false
                                            currentRowCells.add(currentCellText.toString().trim())
                                        }
                                        "tr" -> {
                                            if (currentRowCells.any { it.isNotBlank() }) {
                                                blocks.add(
                                                    StructuredDocBlock.TableRowBlock(
                                                        cells = currentRowCells.toList(),
                                                        isHeader = blocks.lastOrNull() !is StructuredDocBlock.TableRowBlock
                                                    )
                                                )
                                                rawBuilder.append(currentRowCells.joinToString(" | ")).append("\n")
                                            }
                                        }
                                        "tbl" -> {
                                            inTable = false
                                        }
                                        "p" -> {
                                            if (!inTable) {
                                                val paragraphText = currentParagraph.toString().trim()
                                                if (paragraphText.isNotEmpty()) {
                                                    rawBuilder.append(paragraphText).append("\n\n")
                                                    when {
                                                        currentStyle.contains("Heading1", ignoreCase = true) ||
                                                                currentStyle.contains("Title", ignoreCase = true) -> {
                                                            blocks.add(StructuredDocBlock.Heading(paragraphText, 1))
                                                        }
                                                        currentStyle.contains("Heading", ignoreCase = true) ||
                                                                currentStyle.contains("Subtitle", ignoreCase = true) -> {
                                                            blocks.add(StructuredDocBlock.Heading(paragraphText, 2))
                                                        }
                                                        isBulletList -> {
                                                            blocks.add(StructuredDocBlock.BulletItem(paragraphText))
                                                        }
                                                        else -> {
                                                            blocks.add(StructuredDocBlock.Paragraph(paragraphText))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            eventType = parser.next()
                        }
                        break
                    }
                    entry = zip.nextEntry
                }
            }
        }

        // If the file wasn't a ZIP/OpenXML container (e.g. plain text saved with .docx), fallback to text extraction
        if (blocks.isEmpty()) {
            return parseDocDocument(item)
        }

        val fullText = rawBuilder.toString().trim()
        val words = fullText.split(Regex("\\s+")).count { it.isNotBlank() }
        return LoadedDocumentContent.WordContent(
            blocks = blocks,
            plainTextFallback = fullText,
            wordCount = words
        )
    }

    private fun parseDocDocument(item: DocumentFileItem): LoadedDocumentContent {
        val bytes = openItemInputStream(item)?.use { it.readBytes() }
            ?: return LoadedDocumentContent.ErrorContent("Unable to read Word document.")

        val rawString = String(bytes, StandardCharsets.UTF_8)
        val blocks = mutableListOf<StructuredDocBlock>()

        // Check if RTF format
        val cleanedText = if (rawString.trimStart().startsWith("{\\rtf")) {
            stripRtfControlWords(rawString)
        } else {
            // Extract printable runs from legacy .doc or UTF-8 text .doc
            extractReadableTextFromDocBytes(bytes)
        }

        val paragraphs = cleanedText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        paragraphs.forEachIndexed { index, line ->
            when {
                index == 0 && line.length < 90 -> blocks.add(StructuredDocBlock.Heading(line, 1))
                line.startsWith("# ") -> blocks.add(StructuredDocBlock.Heading(line.removePrefix("# ").trim(), 1))
                line.startsWith("## ") -> blocks.add(StructuredDocBlock.Heading(line.removePrefix("## ").trim(), 2))
                line.startsWith("•") || line.startsWith("- ") || line.startsWith("* ") -> {
                    blocks.add(StructuredDocBlock.BulletItem(line.trimStart('•', '-', '*', ' ')))
                }
                else -> blocks.add(StructuredDocBlock.Paragraph(line))
            }
        }

        val fallback = paragraphs.joinToString("\n\n")
        val wordCount = fallback.split(Regex("\\s+")).count { it.isNotBlank() }
        return LoadedDocumentContent.WordContent(
            blocks = blocks,
            plainTextFallback = fallback,
            wordCount = wordCount
        )
    }

    private fun stripRtfControlWords(rtf: String): String {
        return rtf
            .replace("\\par", "\n")
            .replace("\\line", "\n")
            .replace("\\tab", "    ")
            .replace(Regex("\\\\'[0-9a-fA-F]{2}"), "")
            .replace(Regex("\\\\[a-zA-Z]+-?\\d* ?"), "")
            .replace("{", "")
            .replace("}", "")
            .trim()
    }

    private fun extractReadableTextFromDocBytes(bytes: ByteArray): String {
        // First check if the file is mostly valid UTF-8 readable text
        val utf8Text = String(bytes, StandardCharsets.UTF_8)
        val printableRatio = utf8Text.count { it.isLetterOrDigit() || it.isWhitespace() || it in ".,;:!?-()[]\"'/@#$%&*+=" }
            .toFloat() / utf8Text.length.coerceAtLeast(1)

        if (printableRatio > 0.80f) {
            return utf8Text
        }

        // Otherwise extract ASCII / UTF-16LE readable sequences from binary .doc
        val sb = StringBuilder()
        var currentRun = StringBuilder()
        for (b in bytes) {
            val ch = b.toInt() and 0xFF
            if (ch in 32..126 || ch == 10 || ch == 13 || ch == 9) {
                currentRun.append(ch.toChar())
            } else if (ch != 0) {
                if (currentRun.length >= 6) {
                    sb.append(currentRun).append("\n")
                }
                currentRun = StringBuilder()
            }
        }
        if (currentRun.length >= 6) {
            sb.append(currentRun)
        }
        return sb.toString()
    }

    private fun parseTextDocument(item: DocumentFileItem): LoadedDocumentContent {
        val lines = mutableListOf<String>()
        openItemInputStream(item)?.use { stream ->
            BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
                var line: String? = reader.readLine()
                var count = 0
                while (line != null && count < 5000) {
                    lines.add(line)
                    count++
                    line = reader.readLine()
                }
            }
        } ?: return LoadedDocumentContent.ErrorContent("Unable to open text file.")

        val fullText = lines.joinToString("\n")
        val wordCount = if (fullText.isBlank()) 0 else fullText.trim().split(Regex("\\s+")).size
        return LoadedDocumentContent.TextContent(
            lines = lines,
            fullText = fullText,
            lineCount = lines.size,
            wordCount = wordCount,
            charCount = fullText.length
        )
    }

    /**
     * Seeds built-in device document folders with real PDF, DOCX, DOC, and TXT files so that
     * folders and files are always available inside the in-app Docs reader even on a fresh device.
     */
    private fun ensureBuiltInSampleFoldersAndDocs() {
        val rootDir = File(context.filesDir, "InAppDeviceDocs")
        val officeFolder = File(rootDir, "Office & Reports")
        val manualsFolder = File(rootDir, "Manuals & PDF Guides")
        val notesFolder = File(rootDir, "Text & Quick Notes")

        officeFolder.mkdirs()
        manualsFolder.mkdirs()
        notesFolder.mkdirs()

        // 1. Create real multi-page PDF documents using Android's PdfDocument
        val welcomePdf = File(manualsFolder, "Clone_Space_Docs_Reader_Guide.pdf")
        if (!welcomePdf.exists() || welcomePdf.length() == 0L) {
            createSamplePdfFile(
                target = welcomePdf,
                title = "Clone Space In-App Docs Reader",
                subtitle = "Built-in Read-Only PDF, Word & Text Document Viewer",
                pagesContent = listOf(
                    listOf(
                        "1. Overview",
                        "Welcome to the built-in Clone Space Docs Reader.",
                        "All folders and documents are managed and rendered 100% inside the app",
                        "without launching any external file manager or third-party viewer.",
                        "",
                        "2. Supported Document Formats (Read-Only)",
                        "• PDF Documents (.pdf) — High-resolution multi-page rendering & zoom",
                        "• Word Documents (.docx, .doc, .rtf) — Structured headings, paragraphs & tables",
                        "• Text Documents (.txt, .md, .csv, .log) — Line numbers, search & font scaling",
                        "",
                        "3. In-App Folder Navigation",
                        "• Browse device folders containing documents directly from the Folders screen.",
                        "• Tap any folder to inspect its PDF, Word, and TXT files.",
                        "• Tap any document to open it in the read-only viewer."
                    ),
                    listOf(
                        "Page 2 — Security & Read-Only Mode",
                        "Every document opened inside Clone Space Docs is strictly Read-Only.",
                        "Your original files on device storage are never modified or overwritten.",
                        "",
                        "Key Reader Controls:",
                        "• Zoom In / Out (100% to 250%) on PDF pages",
                        "• Adjust Typography size on Word and TXT documents",
                        "• Instant in-document keyword search & filter",
                        "• Full device storage scan with permission control"
                    )
                )
            )
        }

        val architecturePdf = File(officeFolder, "Quarterly_Executive_Summary_2025.pdf")
        if (!architecturePdf.exists() || architecturePdf.length() == 0L) {
            createSamplePdfFile(
                target = architecturePdf,
                title = "Quarterly Executive Summary",
                subtitle = "Operations, Product Milestones & Financial Overview",
                pagesContent = listOf(
                    listOf(
                        "Executive Highlights",
                        "• Platform uptime reached 99.98% across all active workspace sandboxes.",
                        "• Document processing latency reduced by 42% using native PDF rendering.",
                        "• Zero external intent handoffs required for local file inspection.",
                        "",
                        "Key Departmental Metrics",
                        "1. Engineering: Completed sandbox isolation for YouTube, TikTok, Discord & Docs.",
                        "2. Security: Enforced strict read-only file descriptors on all opened documents.",
                        "3. Mobile UX: Added folder-level filtering and instant document search."
                    )
                )
            )
        }

        // 2. Create real OpenXML (.docx) Word documents
        val projectSpecDocx = File(officeFolder, "Product_Requirements_Specification.docx")
        if (!projectSpecDocx.exists() || projectSpecDocx.length() == 0L) {
            createSampleDocxFile(
                target = projectSpecDocx,
                title = "Product Requirements Specification",
                subtitle = "Clone Space Document Reader Module (v2.4)",
                bullets = listOf(
                    "In-app folder discovery across Internal Storage, Documents, and Download directories.",
                    "Read-only parsing for PDF (.pdf), Word (.docx / .doc), and Text (.txt) formats.",
                    "Zero external app launches — all folder selection and viewing happens in-app."
                ),
                paragraphs = listOf(
                    "This Word (.docx) document is packaged in standard Office OpenXML format and rendered directly inside the Clone Space Docs viewer.",
                    "Users can filter by PDF, Word, or TXT formats, search across folders, and adjust text zoom comfortably while reading."
                ),
                tableRows = listOf(
                    listOf("Format", "Extension", "Engine"),
                    listOf("PDF Document", ".pdf", "Native Android PdfRenderer"),
                    listOf("Word Document", ".docx / .doc", "OpenXML & Stream Parser"),
                    listOf("Text File", ".txt / .md / .csv", "Buffered UTF-8 Reader")
                )
            )
        }

        // 3. Create a .doc Word document
        val meetingDoc = File(officeFolder, "Team_Meeting_Minutes.doc")
        if (!meetingDoc.exists() || meetingDoc.length() == 0L) {
            meetingDoc.writeText(
                """
                # Weekly Engineering & Product Sync Minutes
                Date: October 2026 | Mode: Read-Only Archive
                
                ## Agenda Items Covered
                • Reviewed Clone Space sandbox stability for YouTube, TikTok, Facebook, and Discord.
                • Verified in-app incoming call popup and full-screen answered call timer.
                • Added inbuilt Docs icon to Clone Space for reading PDF, Word, and TXT files.
                
                ## Action Items
                • Keep all document browsing and reading 100% inside the application.
                • Maintain strict read-only protection on all opened user files.
                """.trimIndent(),
                StandardCharsets.UTF_8
            )
        }

        // 4. Create .txt documents
        val readmeTxt = File(notesFolder, "Release_Notes_and_Shortcuts.txt")
        if (!readmeTxt.exists() || readmeTxt.length() == 0L) {
            readmeTxt.writeText(
                """
                ============================================================
                CLONE SPACE DOCS — READ-ONLY DOCUMENT VIEWER (.TXT)
                ============================================================
                
                Supported File Types:
                1. PDF Files (.pdf)
                2. Word Documents (.docx, .doc, .rtf)
                3. Text Files (.txt, .md, .csv, .log, .json, .xml)
                
                How It Works:
                - Folder View: Lists all folders on your device that contain supported documents.
                - Folder Contents: Tap any folder to view the documents inside that folder.
                - Document Reader: Tap any file to read it directly inside the app.
                
                Permissions:
                - Grant File Access permission using the top banner if you want the app to scan custom folders across your entire phone storage (/storage/emulated/0/Documents, /Download, etc.).
                """.trimIndent(),
                StandardCharsets.UTF_8
            )
        }

        val checklistTxt = File(notesFolder, "Project_Launch_Checklist.txt")
        if (!checklistTxt.exists() || checklistTxt.length() == 0L) {
            checklistTxt.writeText(
                """
                PROJECT LAUNCH CHECKLIST (READ-ONLY)
                ------------------------------------------------------------
                [x] SMS duplicate notification suppression
                [x] Incoming call popup + full-screen answered call timer
                [x] Clone Space YouTube, TikTok, Facebook & Discord helpers
                [x] Inbuilt Docs icon in Clone Space
                [x] In-app folder browser and read-only PDF / Word / TXT viewer
                """.trimIndent(),
                StandardCharsets.UTF_8
            )
        }
    }

    private fun createSamplePdfFile(
        target: File,
        title: String,
        subtitle: String,
        pagesContent: List<List<String>>
    ) {
        try {
            val pdf = PdfDocument()
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(20, 30, 55)
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(90, 105, 135)
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(35, 42, 52)
                textSize = 12.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(37, 99, 235)
                strokeWidth = 2f
            }

            pagesContent.forEachIndexed { index, lines ->
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, index + 1).create()
                val page = pdf.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)

                var y = 56f
                canvas.drawText(title, 44f, y, titlePaint)
                y += 22f
                canvas.drawText("$subtitle  •  Page ${index + 1} of ${pagesContent.size}", 44f, y, subPaint)
                y += 14f
                canvas.drawLine(44f, y, 551f, y, accentPaint)
                y += 30f

                for (line in lines) {
                    if (line.isBlank()) {
                        y += 14f
                    } else {
                        val isSection = line.firstOrNull()?.isDigit() == true || line.startsWith("Page ") || line.endsWith("Highlights") || line.endsWith("Metrics")
                        bodyPaint.typeface = Typeface.create(
                            Typeface.DEFAULT,
                            if (isSection) Typeface.BOLD else Typeface.NORMAL
                        )
                        canvas.drawText(line, 44f, y, bodyPaint)
                        y += 22f
                    }
                }

                pdf.finishPage(page)
            }

            FileOutputStream(target).use { out ->
                pdf.writeTo(out)
            }
            pdf.close()
        } catch (_: Exception) {
        }
    }

    private fun createSampleDocxFile(
        target: File,
        title: String,
        subtitle: String,
        bullets: List<String>,
        paragraphs: List<String>,
        tableRows: List<List<String>>
    ) {
        try {
            val xmlBuilder = StringBuilder()
            xmlBuilder.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            xmlBuilder.append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>""")

            // Title
            xmlBuilder.append("""<w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>${escapeXml(title)}</w:t></w:r></w:p>""")
            // Subtitle
            xmlBuilder.append("""<w:p><w:pPr><w:pStyle w:val="Heading2"/></w:pPr><w:r><w:t>${escapeXml(subtitle)}</w:t></w:r></w:p>""")

            // Paragraphs
            for (p in paragraphs) {
                xmlBuilder.append("""<w:p><w:r><w:t>${escapeXml(p)}</w:t></w:r></w:p>""")
            }

            // Bullets
            for (b in bullets) {
                xmlBuilder.append("""<w:p><w:pPr><w:numPr><w:numId w:val="1"/></w:numPr></w:pPr><w:r><w:t>${escapeXml(b)}</w:t></w:r></w:p>""")
            }

            // Table
            if (tableRows.isNotEmpty()) {
                xmlBuilder.append("""<w:tbl>""")
                for (row in tableRows) {
                    xmlBuilder.append("""<w:tr>""")
                    for (cell in row) {
                        xmlBuilder.append("""<w:tc><w:p><w:r><w:t>${escapeXml(cell)}</w:t></w:r></w:p></w:tc>""")
                    }
                    xmlBuilder.append("""</w:tr>""")
                }
                xmlBuilder.append("""</w:tbl>""")
            }

            xmlBuilder.append("""</w:body></w:document>""")

            ZipOutputStream(FileOutputStream(target)).use { zos ->
                zos.putNextEntry(ZipEntry("word/document.xml"))
                zos.write(xmlBuilder.toString().toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()
            }
        } catch (_: Exception) {
        }
    }

    private fun escapeXml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
