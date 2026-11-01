package com.statusvideo.statussaver.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.statusvideo.statussaver.data.local.db.StatusDao
import com.statusvideo.statussaver.data.local.db.StatusEntity
import com.statusvideo.statussaver.domain.model.MediaType
import com.statusvideo.statussaver.domain.model.Status
import com.statusvideo.statussaver.domain.repository.StatusRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatusRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val statusDao: StatusDao,
    private val safUriStore: SafUriStore
) : StatusRepository {
    private val saveMutex = Mutex()
    private val activeSaveIds = mutableSetOf<String>()

    // Android 10 and below: direct file path
    private val legacyStatusPath = File(
        Environment.getExternalStorageDirectory(),
        "WhatsApp/Media/.Statuses"
    )

    override fun observeStatuses(): Flow<List<Status>> = callbackFlow {
        val scope = this

        suspend fun emitCurrent() {
            val statuses = readStatuses()
            trySend(statuses)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: use SAF URI stored from folder picker
            val treeUri = safUriStore.getTreeUri()
            if (treeUri != null) {
                launch { emitCurrent() }
                // Poll via a lightweight DocumentFile refresh; real-time via FileObserver
                // on Android 11+ is not possible without root, so we rely on manual refresh
                // and ViewModel-triggered re-reads. The flow stays open for manual triggers.
            } else {
                trySend(emptyList())
            }
            awaitClose { /* no-op */ }
        } else {
            launch { emitCurrent() }
            val observer = object : FileObserver(legacyStatusPath.absolutePath, ALL_EVENTS) {
                override fun onEvent(event: Int, path: String?) {
                    if (path != null && !path.endsWith(".nomedia")) {
                        scope.launch { emitCurrent() }
                    }
                }
            }
            observer.startWatching()
            awaitClose { observer.stopWatching() }
        }
    }.flowOn(Dispatchers.IO)

    private fun readStatuses(): List<Status> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            readStatusesViaSaf()
        } else {
            readStatusesLegacy()
        }
    }

    private fun readStatusesViaSaf(): List<Status> {
        val treeUri = safUriStore.getTreeUri() ?: return emptyList()
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        return tree.listFiles()
            .filter { it.isFile && it.name?.endsWith(".nomedia") == false }
            .mapNotNull { doc ->
                val name = doc.name ?: return@mapNotNull null
                val type = when {
                    name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) ||
                            name.endsWith(".png", true) || name.endsWith(".webp", true) -> MediaType.IMAGE
                    name.endsWith(".mp4", true) || name.endsWith(".3gp", true) ||
                            name.endsWith(".mkv", true) -> MediaType.VIDEO
                    else -> return@mapNotNull null
                }
                Status(
                    id = doc.uri.toString(),
                    uri = doc.uri,
                    type = type,
                    timestamp = doc.lastModified()
                )
            }
            .sortedByDescending { it.timestamp }
    }

    private fun readStatusesLegacy(): List<Status> {
        if (!legacyStatusPath.exists() || !legacyStatusPath.isDirectory) return emptyList()
        return legacyStatusPath.listFiles()
            ?.filter { it.isFile && it.name != ".nomedia" }
            ?.mapNotNull { file ->
                val type = when {
                    file.name.endsWith(".jpg", true) || file.name.endsWith(".jpeg", true) ||
                            file.name.endsWith(".png", true) || file.name.endsWith(".webp", true) -> MediaType.IMAGE
                    file.name.endsWith(".mp4", true) || file.name.endsWith(".3gp", true) ||
                            file.name.endsWith(".mkv", true) -> MediaType.VIDEO
                    else -> return@mapNotNull null
                }
                Status(
                    id = file.absolutePath,
                    uri = Uri.fromFile(file),
                    type = type,
                    timestamp = file.lastModified()
                )
            }
            ?.sortedByDescending { it.timestamp }
            ?: emptyList()
    }

    override suspend fun saveStatus(status: Status): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val reservedStatus = reserveUnsavedStatuses(listOf(status)).firstOrNull()
                ?: return@runCatching
            persistReservedStatus(reservedStatus)
        }
    }

    override suspend fun saveAvailableStatuses(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val statuses = readStatuses()
            saveStatusesInParallel(statuses)
        }
    }

    private suspend fun saveStatusesInParallel(statuses: List<Status>): Int {
        val reservedStatuses = reserveUnsavedStatuses(statuses)
        if (reservedStatuses.isEmpty()) return 0

        var savedCount = 0
        for (chunk in reservedStatuses.chunked(PARALLEL_SAVE_LIMIT)) {
            val chunkResults = supervisorScope {
                chunk.map { status ->
                    async(Dispatchers.IO) {
                        runCatching {
                            persistReservedStatus(status)
                            true
                        }.getOrDefault(false)
                    }
                }.awaitAll()
            }
            savedCount += chunkResults.count { it }
        }
        return savedCount
    }

    private suspend fun reserveUnsavedStatuses(statuses: List<Status>): List<Status> {
        val distinctStatuses = statuses.distinctBy { it.id }
        if (distinctStatuses.isEmpty()) return emptyList()

        return saveMutex.withLock {
            val knownIds = statusDao.getSavedIds(distinctStatuses.map(Status::id)).toHashSet()
            distinctStatuses.filter { status ->
                status.id !in knownIds && activeSaveIds.add(status.id)
            }
        }
    }

    private suspend fun persistReservedStatus(status: Status) {
        try {
            val savedUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(status)
            } else {
                saveToExternalStorage(status)
            }
            statusDao.insertStatus(
                StatusEntity(
                    id = status.id,
                    filePath = status.uri.toString(),
                    savedUri = savedUri.toString(),
                    type = status.type.name,
                    savedAt = System.currentTimeMillis()
                )
            )
        } finally {
            saveMutex.withLock {
                activeSaveIds.remove(status.id)
            }
        }
    }

    private fun saveViaMediaStore(status: Status): Uri {
        val isImage = status.type == MediaType.IMAGE
        val collection = if (isImage) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        }

        val fileName = "status_${System.currentTimeMillis()}${if (isImage) ".jpg" else ".mp4"}"
        val relativeDir = if (isImage) "Pictures/StatusSaver" else "Movies/StatusSaver"

        val values = ContentValues().apply {
            put(if (isImage) MediaStore.Images.Media.DISPLAY_NAME else MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(if (isImage) MediaStore.Images.Media.MIME_TYPE else MediaStore.Video.Media.MIME_TYPE,
                if (isImage) "image/jpeg" else "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(if (isImage) MediaStore.Images.Media.RELATIVE_PATH else MediaStore.Video.Media.RELATIVE_PATH, relativeDir)
                put(if (isImage) MediaStore.Images.Media.IS_PENDING else MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val destUri = resolver.insert(collection, values)
            ?: throw IOException("MediaStore insert returned null")

        resolver.openInputStream(status.uri)?.use { input ->
            resolver.openOutputStream(destUri)?.use { output ->
                input.copyTo(output)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(if (isImage) MediaStore.Images.Media.IS_PENDING else MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(destUri, values, null, null)
        }

        return destUri
    }

    private fun saveToExternalStorage(status: Status): Uri {
        val isImage = status.type == MediaType.IMAGE
        val dir = File(
            Environment.getExternalStoragePublicDirectory(
                if (isImage) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES
            ),
            "StatusSaver"
        )
        if (!dir.exists()) dir.mkdirs()

        val ext = if (isImage) ".jpg" else ".mp4"
        val destFile = File(dir, "status_${System.currentTimeMillis()}$ext")

        context.contentResolver.openInputStream(status.uri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        } ?: FileInputStream(File(status.uri.path!!)).use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }

        return Uri.fromFile(destFile)
    }

    override fun getSavedStatuses(): Flow<List<Status>> = statusDao.getAllSaved()
        .map { entities ->
            entities.map { entity ->
                Status(
                    id = entity.id,
                    uri = Uri.parse(entity.savedUri),
                    type = MediaType.valueOf(entity.type),
                    timestamp = entity.savedAt
                )
            }
        }
        .flowOn(Dispatchers.IO)

    override suspend fun deleteSaved(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val entity = statusDao.getById(id) ?: return@runCatching
            val savedUri = Uri.parse(entity.savedUri)
            if (savedUri.scheme == "content") {
                context.contentResolver.delete(savedUri, null, null)
            } else {
                savedUri.path?.let { File(it).delete() }
            }
            statusDao.deleteById(id)
        }
    }

    companion object {
        private const val PARALLEL_SAVE_LIMIT = 3
    }
}
