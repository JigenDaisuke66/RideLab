package com.isaac.ridelab.export

import android.content.ContentResolver
import android.net.Uri
import com.isaac.ridelab.data.repository.RoomRideRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException

/** Android document I/O, isolated from both Compose and the serializer. */
class RideCsvDocumentWriter(private val resolver: ContentResolver, private val repository: RoomRideRepository) {
    suspend fun write(id: Long, uri: Uri, headers: List<String>) = withContext(Dispatchers.IO) {
        // One transaction reads the session and all raw samples before opening the output.
        val samples = repository.exportSamples(id)
        val jobContext = coroutineContext
        val stream = resolver.openOutputStream(uri, "wt") ?: throw IOException("No document output stream")
        stream.bufferedWriter(Charsets.UTF_8).use { writer ->
            RideCsvExporter.write(writer, headers, samples) { jobContext.ensureActive() }
        }
    }
}
