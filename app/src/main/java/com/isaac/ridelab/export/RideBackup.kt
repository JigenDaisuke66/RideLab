package com.isaac.ridelab.export

import com.isaac.ridelab.data.model.*
import java.io.*
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

data class BackupRide(val session: RideSession, val samples: List<RideSample>)
data class RestoreResult(val added: Int, val skipped: Int)

/** Versioned, compressed full-fidelity history archive. IDs are local and remapped on restore.
 * The SHA-256 trailer detects damaged files; this is not encryption or authentication.
 */
object RideBackup {
    private const val MAX_BYTES = 128 * 1024 * 1024
    fun write(rides: List<BackupRide>, output: OutputStream) {
        require(rides.size <= 100_000 && rides.sumOf { it.samples.size.toLong() } <= 2_000_000)
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeUTF("RideLab history"); out.writeInt(1); out.writeInt(rides.size)
            rides.forEach { ride ->
                validate(ride)
                val s = ride.session
                out.writeLong(s.startTimeEpochMillis); out.longOrNull(s.endTimeEpochMillis)
                out.writeLong(s.durationMillis); out.writeUTF(s.status.name)
                out.string(s.trainerName); out.string(s.trainerAddress)
                out.string(s.heartRateSensorName); out.string(s.heartRateSensorAddress)
                out.intOrNull(s.lastTargetPowerWatts); out.intOrNull(s.analyticsFtpWatts); out.doubleOrNull(s.bodyWeightKg)
                out.writeInt(ride.samples.size)
                ride.samples.forEach { p ->
                    out.writeLong(p.timestampEpochMillis); out.writeLong(p.elapsedMillis)
                    out.intOrNull(p.trainerPowerWatts); out.doubleOrNull(p.cadenceRpm)
                    out.intOrNull(p.heartRateBpm); out.intOrNull(p.targetPowerWatts)
                    out.intOrNull(p.externalPowerWatts); out.doubleOrNull(p.speedKph); out.doubleOrNull(p.resistanceLevel)
                    out.string(p.trainerAddress); out.string(p.heartRateSensorAddress)
                }
                require(bytes.size() <= MAX_BYTES)
            }
        }
        val data = bytes.toByteArray()
        GZIPOutputStream(output).use { it.write(data); it.write(digest(data)) }
    }

    fun read(input: InputStream): List<BackupRide> {
        val bytes = GZIPInputStream(input).use { zip ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val size = zip.read(buffer)
                if (size < 0) break
                require(out.size().toLong() + size <= MAX_BYTES + 32L)
                out.write(buffer, 0, size)
            }
            out.toByteArray()
        }
        require(bytes.size >= 32)
        val data = bytes.copyOf(bytes.size - 32)
        require(MessageDigest.isEqual(digest(data), bytes.copyOfRange(data.size, bytes.size)))
        return DataInputStream(ByteArrayInputStream(data)).use { reader ->
            require(reader.readUTF() == "RideLab history" && reader.readInt() == 1)
            val count = reader.readInt().also { require(it in 0..100_000) }
            var total = 0L
            val rides = List(count) {
                val session = RideSession(
                    startTimeEpochMillis = reader.readLong(), endTimeEpochMillis = reader.longOrNull(),
                    durationMillis = reader.readLong(), status = StoredRideStatus.valueOf(reader.readUTF()),
                    trainerName = reader.string(), trainerAddress = reader.string(),
                    heartRateSensorName = reader.string(), heartRateSensorAddress = reader.string(),
                    lastTargetPowerWatts = reader.intOrNull(), analyticsFtpWatts = reader.intOrNull(),
                    bodyWeightKg = reader.doubleOrNull(), activeSlot = null)
                val samplesCount = reader.readInt().also {
                    require(it >= 0); total += it; require(total <= 2_000_000)
                }
                val samples = List(samplesCount) {
                    RideSample(0, reader.readLong(), reader.readLong(),
                        reader.intOrNull(), reader.doubleOrNull(), reader.intOrNull(), reader.intOrNull(),
                        reader.intOrNull(), reader.doubleOrNull(), reader.doubleOrNull(), reader.string(), reader.string())
                }
                BackupRide(session, samples).also(::validate)
            }
            require(reader.read() == -1)
            rides
        }
    }

    fun same(a: BackupRide, b: BackupRide): Boolean =
        a.session.copy(id = 0) == b.session.copy(id = 0) &&
            a.samples.map { it.copy(sessionId = 0) } == b.samples.map { it.copy(sessionId = 0) }

    private fun validate(ride: BackupRide) {
        val s = ride.session
        require(s.status != StoredRideStatus.ACTIVE && s.activeSlot == null)
        require(s.startTimeEpochMillis >= 0 && s.durationMillis >= 0)
        require(s.endTimeEpochMillis == null || s.endTimeEpochMillis >= s.startTimeEpochMillis)
        require(s.bodyWeightKg == null || (s.bodyWeightKg.isFinite() && s.bodyWeightKg > 0))
        require(s.analyticsFtpWatts == null || s.analyticsFtpWatts > 0)
        var previous = -1L
        ride.samples.forEach {
            require(it.elapsedMillis > previous && it.elapsedMillis <= s.durationMillis)
            require(it.timestampEpochMillis >= 0)
            require(listOf(it.cadenceRpm, it.speedKph, it.resistanceLevel).all { v -> v == null || v.isFinite() })
            previous = it.elapsedMillis
        }
    }

    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
    private fun DataOutputStream.string(v: String?) { writeBoolean(v != null); if (v != null) writeUTF(v) }
    private fun DataInputStream.string(): String? = if (readBoolean()) readUTF() else null
    private fun DataOutputStream.intOrNull(v: Int?) { writeBoolean(v != null); if (v != null) writeInt(v) }
    private fun DataInputStream.intOrNull(): Int? = if (readBoolean()) readInt() else null
    private fun DataOutputStream.longOrNull(v: Long?) { writeBoolean(v != null); if (v != null) writeLong(v) }
    private fun DataInputStream.longOrNull(): Long? = if (readBoolean()) readLong() else null
    private fun DataOutputStream.doubleOrNull(v: Double?) { writeBoolean(v != null); if (v != null) writeDouble(v) }
    private fun DataInputStream.doubleOrNull(): Double? = if (readBoolean()) readDouble() else null
}
