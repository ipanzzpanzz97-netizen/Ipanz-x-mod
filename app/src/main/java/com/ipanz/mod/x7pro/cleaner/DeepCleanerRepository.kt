package com.ipanz.mod.x7pro.cleaner

import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

enum class JunkCategory(val labelMs: String) {
    APK_INSTALLER("Baki Pemasang APK"),
    TEMP_OR_LOG("Fail Log & Sementara"),
    APP_CACHE("Cache Aplikasi & Media"),
    EMPTY_DIR("Folder Kosong")
}

data class ScannedFileItem(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val category: JunkCategory,
    val selected: Boolean = true
)

data class DuplicateGroup(
    val sha256: String,
    val fileSizeBytes: Long,
    val files: List<FileEntry>
)

data class FileEntry(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val lastModifiedMs: Long,
    val selectedForDeletion: Boolean = false
)

data class CleanerScanProgress(
    val scannedFiles: Int = 0,
    val currentPath: String = "",
    val phaseLabel: String = ""
)

data class DeepScanResult(
    val junkFiles: List<ScannedFileItem> = emptyList(),
    val totalJunkBytes: Long = 0L,
    val duplicateGroups: List<DuplicateGroup> = emptyList(),
    val reclaimableDuplicateBytes: Long = 0L,
    val heavyFiles: List<FileEntry> = emptyList(),
    val totalScannedCount: Int = 0
)

data class CleanExecutionSummary(
    val deletedCount: Int,
    val freedBytes: Long,
    val failedCount: Int
)

class DeepCleanerRepository {

    companion object {
        private const val HEAVY_FILE_THRESHOLD_BYTES = 100L * 1024L * 1024L // 100 MB
        private const val HASH_BUFFER_SIZE = 64 * 1024 // 64 KB buffered streaming

        // Protected personal media & document directories (never auto-treated as junk)
        private val PROTECTED_TOP_DIRS = setOf(
            "dcim", "pictures", "movies", "music", "documents", "podcasts", "audiobooks", "recordings"
        )

        private val JUNK_EXTENSIONS = setOf("tmp", "temp", "log", "bak", "chk", "exo")
    }

    suspend fun scanStorage(
        rootDir: File = Environment.getExternalStorageDirectory(),
        onProgress: (CleanerScanProgress) -> Unit = {}
    ): DeepScanResult = withContext(Dispatchers.IO) {
        val junkItems = mutableListOf<ScannedFileItem>()
        val heavyItems = mutableListOf<FileEntry>()
        val sizeBuckets = HashMap<Long, MutableList<File>>()
        var scannedCount = 0

        if (!rootDir.exists() || !rootDir.canRead()) {
            return@withContext DeepScanResult()
        }

        val rootPath = rootDir.absolutePath

        try {
            rootDir.walkTopDown()
                .onEnter { dir ->
                    val nameLower = dir.name.lowercase()
                    // Skip Android/obb game assets and protected system anchors
                    nameLower != "obb" && !nameLower.startsWith(".android_secure")
                }
                .forEach { file ->
                    coroutineContext.ensureActive()
                    scannedCount++
                    if (scannedCount % 120 == 0) {
                        onProgress(
                            CleanerScanProgress(
                                scannedFiles = scannedCount,
                                currentPath = file.name,
                                phaseLabel = "Mengimbas storan UFS 4.0..."
                            )
                        )
                    }

                    val relPath = file.absolutePath.removePrefix(rootPath).trimStart(File.separatorChar)
                    val topFolder = relPath.substringBefore(File.separatorChar).lowercase()
                    val isInsideProtectedMedia = topFolder in PROTECTED_TOP_DIRS

                    if (file.isDirectory) {
                        if (!isInsideProtectedMedia && isEmptyDirectory(file)) {
                            junkItems.add(
                                ScannedFileItem(
                                    path = file.absolutePath,
                                    name = file.name,
                                    sizeBytes = 0L,
                                    category = JunkCategory.EMPTY_DIR,
                                    selected = true
                                )
                            )
                        }
                        return@forEach
                    }

                    val size = file.length()
                    val ext = file.extension.lowercase()
                    val pathLower = file.absolutePath.lowercase()

                    // Phase 1 Junk Classification (never touch user photos/videos/docs)
                    val isCacheDir = pathLower.contains("/cache/") ||
                        pathLower.contains("/code_cache/") ||
                        pathLower.contains("/.trash/") ||
                        pathLower.contains("/.shared/") ||
                        pathLower.contains("/.thumbnails/")

                    when {
                        ext == "apk" && !isInsideProtectedMedia -> {
                            junkItems.add(
                                ScannedFileItem(
                                    path = file.absolutePath,
                                    name = file.name,
                                    sizeBytes = size,
                                    category = JunkCategory.APK_INSTALLER,
                                    selected = true
                                )
                            )
                        }

                        (ext in JUNK_EXTENSIONS || file.name.startsWith("logcat_")) && !isInsideProtectedMedia -> {
                            junkItems.add(
                                ScannedFileItem(
                                    path = file.absolutePath,
                                    name = file.name,
                                    sizeBytes = size,
                                    category = JunkCategory.TEMP_OR_LOG,
                                    selected = true
                                )
                            )
                        }

                        isCacheDir && size > 0L -> {
                            junkItems.add(
                                ScannedFileItem(
                                    path = file.absolutePath,
                                    name = file.name,
                                    sizeBytes = size,
                                    category = JunkCategory.APP_CACHE,
                                    selected = true
                                )
                            )
                        }
                    }

                    // Heavy files (> 100 MB)
                    if (size >= HEAVY_FILE_THRESHOLD_BYTES) {
                        heavyItems.add(
                            FileEntry(
                                path = file.absolutePath,
                                name = file.name,
                                sizeBytes = size,
                                lastModifiedMs = file.lastModified(),
                                selectedForDeletion = false
                            )
                        )
                    }

                    // Bucket non-trivial files (>= 16 KB) by size for fast SHA-256 duplicate pre-filtering
                    if (size >= 16L * 1024L && !isCacheDir) {
                        sizeBuckets.getOrPut(size) { mutableListOf() }.add(file)
                    }
                }
        } catch (_: SecurityException) {
            // Gracefully return collected results if permission was revoked mid-scan
        } catch (_: Throwable) {
            // Guard against unreadable FUSE nodes
        }

        // Phase 2: Streaming SHA-256 verification ONLY on same-size candidates
        val candidateGroups = sizeBuckets.values.filter { it.size > 1 }
        val duplicateGroups = mutableListOf<DuplicateGroup>()
        var duplicateWasteBytes = 0L

        candidateGroups.forEachIndexed { idx, files ->
            coroutineContext.ensureActive()
            if (idx % 10 == 0) {
                onProgress(
                    CleanerScanProgress(
                        scannedFiles = scannedCount,
                        currentPath = files.firstOrNull()?.name.orEmpty(),
                        phaseLabel = "Mengesahkan cincangan SHA-256 fail pendua..."
                    )
                )
            }

            val byHash = HashMap<String, MutableList<File>>()
            for (candidate in files) {
                val hash = computeSha256Stream(candidate) ?: continue
                byHash.getOrPut(hash) { mutableListOf() }.add(candidate)
            }

            for ((hash, matched) in byHash) {
                if (matched.size > 1) {
                    val sorted = matched.sortedBy { it.lastModified() }
                    val entries = sorted.mapIndexed { pos, f ->
                        FileEntry(
                            path = f.absolutePath,
                            name = f.name,
                            sizeBytes = f.length(),
                            lastModifiedMs = f.lastModified(),
                            // Keep oldest copy unchecked, mark extra duplicates selected
                            selectedForDeletion = pos > 0
                        )
                    }
                    val fileSize = sorted.first().length()
                    duplicateWasteBytes += fileSize * (matched.size - 1)
                    duplicateGroups.add(
                        DuplicateGroup(
                            sha256 = hash,
                            fileSizeBytes = fileSize,
                            files = entries
                        )
                    )
                }
            }
        }

        DeepScanResult(
            junkFiles = junkItems.sortedByDescending { it.sizeBytes },
            totalJunkBytes = junkItems.sumOf { it.sizeBytes },
            duplicateGroups = duplicateGroups.sortedByDescending { it.fileSizeBytes * (it.files.size - 1) },
            reclaimableDuplicateBytes = duplicateWasteBytes,
            heavyFiles = heavyItems.sortedByDescending { it.sizeBytes },
            totalScannedCount = scannedCount
        )
    }

    suspend fun deletePaths(paths: List<String>): CleanExecutionSummary = withContext(Dispatchers.IO) {
        var deleted = 0
        var freed = 0L
        var failed = 0

        for (path in paths) {
            coroutineContext.ensureActive()
            try {
                val file = File(path)
                if (!file.exists()) continue
                val size = if (file.isDirectory) 0L else file.length()
                val ok = if (file.isDirectory) {
                    if (isEmptyDirectory(file)) file.delete() else false
                } else {
                    file.delete()
                }
                if (ok) {
                    deleted++
                    freed += size
                } else {
                    failed++
                }
            } catch (_: Throwable) {
                failed++
            }
        }

        CleanExecutionSummary(
            deletedCount = deleted,
            freedBytes = freed,
            failedCount = failed
        )
    }

    fun computeSha256Stream(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(HASH_BUFFER_SIZE)
            BufferedInputStream(FileInputStream(file), HASH_BUFFER_SIZE).use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Throwable) {
            null
        }
    }

    private fun isEmptyDirectory(dir: File): Boolean {
        return try {
            val children = dir.listFiles()
            children != null && children.isEmpty()
        } catch (_: Throwable) {
            false
        }
    }
}
