package com.nandox.arcade.emulator

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BiosInstallerTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((name, data) in entries) {
                z.putNextEntry(ZipEntry(name)); z.write(data); z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun targetIsCaseInsensitiveAndKeepsSubfolder() {
        assertEquals("scph5501.bin", BiosCatalog.targetFor("SCPH5501.BIN"))
        assertEquals("dc/dc_boot.bin", BiosCatalog.targetFor("some/dir/DC_BOOT.bin"))
        assertNull(BiosCatalog.targetFor("mario64.z64"))
    }

    @Test
    fun everyPlatformWithABiosGroupHasOneButtonTarget() {
        val ids = BiosCatalog.groups.map { it.platformId }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue("ps2" in ids && "ps1" in ids && "nds" in ids && "dreamcast" in ids)
    }

    @Test
    fun installsSingleKnownFile() {
        val system = tmp.newFolder("system")
        val r = BiosInstaller.installStream(system, "bios7.bin", ByteArrayInputStream(byteArrayOf(1, 2, 3)))
        assertEquals(listOf("bios7.bin"), r.installed)
        assertArrayEquals(byteArrayOf(1, 2, 3), File(system, "bios7.bin").readBytes())
    }

    @Test
    fun ignoresUnknownFile() {
        val system = tmp.newFolder("system")
        val r = BiosInstaller.installStream(system, "game.bin", ByteArrayInputStream(byteArrayOf(9)))
        assertTrue(r.installed.isEmpty())
        assertEquals(1, r.ignored)
        assertFalse(File(system, "game.bin").exists())
    }

    @Test
    fun zipInstallsOnlyKnownFilesToTheRightPlaces() {
        val system = tmp.newFolder("system")
        val zip = zipOf(
            "pack/bios9.bin" to byteArrayOf(9),
            "pack/dc/dc_flash.bin" to byteArrayOf(7),
            "pack/readme.txt" to byteArrayOf(0),
            "../evil.bin" to byteArrayOf(6),
        )
        val r = BiosInstaller.installStream(system, "pack.zip", ByteArrayInputStream(zip))
        assertEquals(setOf("bios9.bin", "dc/dc_flash.bin"), r.installed.toSet())
        assertEquals(2, r.ignored)
        assertTrue(File(system, "dc/dc_flash.bin").isFile)
        assertFalse(File(system.parentFile, "evil.bin").exists())
    }

    @Test
    fun groupStatusRespectsAnyOfAndAllOf() {
        val system = tmp.newFolder("system")
        val ps1 = BiosCatalog.groups.first { it.platformId == "ps1" }
        val nds = BiosCatalog.groups.first { it.platformId == "nds" }
        assertFalse(BiosCatalog.isPresent(ps1, system))
        File(system, "scph1001.bin").writeBytes(byteArrayOf(1))
        assertTrue(BiosCatalog.isPresent(ps1, system))
        File(system, "bios7.bin").writeBytes(byteArrayOf(1))
        assertFalse(BiosCatalog.isPresent(nds, system))
        assertEquals(listOf("bios9.bin", "firmware.bin"), BiosCatalog.missing(nds, system))
    }
}
