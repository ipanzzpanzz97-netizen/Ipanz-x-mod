package com.ipanz.mod.x7pro

import com.ipanz.mod.x7pro.cleaner.DeepCleanerRepository
import com.ipanz.mod.x7pro.hardware.IPanzHardwareGatekeeper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class IPanzCoreUnitTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `hardware gatekeeper accepts POCO X7 Pro global and India models`() {
        assertTrue(
            IPanzHardwareGatekeeper.verifyParameters(
                device = "rodin",
                product = "rodin_global",
                model = "2412DPC0AG",
                boardPlatform = "mt6899"
            )
        )
        assertTrue(
            IPanzHardwareGatekeeper.verifyParameters(
                device = "rodin_in",
                product = "rodin_in",
                model = "2412DPC0AI",
                boardPlatform = "mt6899"
            )
        )
    }

    @Test
    fun `hardware gatekeeper rejects non POCO X7 Pro devices`() {
        assertFalse(
            IPanzHardwareGatekeeper.verifyParameters(
                device = "sdk_gphone64_arm64",
                product = "sdk_gphone64_arm64",
                model = "Android SDK built for arm64",
                boardPlatform = "ranchu"
            )
        )
        assertFalse(
            IPanzHardwareGatekeeper.verifyParameters(
                device = "duchamp",
                product = "duchamp_global",
                model = "2311DRK48G",
                boardPlatform = "mt6897"
            )
        )
    }

    @Test
    fun `deep cleaner detects SHA256 duplicates and junk while preserving protected media`() = runTest {
        val root = tempFolder.newFolder("storage")
        val dcim = File(root, "DCIM/Camera").apply { mkdirs() }
        val downloads = File(root, "Download").apply { mkdirs() }

        // Protected personal photo (must NOT be flagged as junk)
        val photo = File(dcim, "IMG_001.jpg").apply {
            writeBytes(ByteArray(24 * 1024) { 7 })
        }
        // Residual APK & log junk
        File(downloads, "old_mod.apk").writeBytes(ByteArray(32 * 1024) { 1 })
        File(downloads, "crash.log").writeText("sample log")

        // Exact duplicate files (>= 16 KB threshold)
        val payload = ByteArray(20 * 1024) { (it % 127).toByte() }
        File(downloads, "bundle_a.zip").writeBytes(payload)
        File(downloads, "bundle_copy.zip").writeBytes(payload)

        val repo = DeepCleanerRepository()
        val result = repo.scanStorage(rootDir = root)

        assertTrue(result.junkFiles.any { it.name == "old_mod.apk" })
        assertTrue(result.junkFiles.any { it.name == "crash.log" })
        assertFalse(result.junkFiles.any { it.path == photo.absolutePath })
        assertEquals(1, result.duplicateGroups.size)
        assertEquals(2, result.duplicateGroups.first().files.size)

        // Verify safe deletion of junk files leaves protected photo intact
        val delSummary = repo.deletePaths(result.junkFiles.map { it.path })
        assertTrue(delSummary.deletedCount >= 2)
        assertTrue(photo.exists())
    }

    @Test
    fun `voice presets define 6 unique models with Bahasa Melayu kegunaan text`() {
        val presets = com.ipanz.mod.x7pro.voice.VoicePreset.entries
        assertEquals(6, presets.size)
        assertEquals(6, presets.map { it.id }.distinct().size)
        assertEquals(6, presets.map { it.assetFileName }.distinct().size)
        presets.forEach { preset ->
            assertTrue(preset.kegunaanMs.contains("Kegunaan Fitur Ini"))
            assertTrue(preset.assetFileName.startsWith("models/") && preset.assetFileName.endsWith(".onnx"))
        }
    }
}
