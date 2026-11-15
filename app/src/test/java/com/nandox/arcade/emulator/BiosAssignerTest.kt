package com.nandox.arcade.emulator

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BiosAssignerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val ps1 get() = BiosCatalog.groups.first { it.platformId == "ps1" }
    private val nds get() = BiosCatalog.groups.first { it.platformId == "nds" }

    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

    @Test
    fun anyNameBecomesTheGroupsBiosAndOthersAreMovedAside() {
        val system = tmp.newFolder("system")
        val unused = tmp.newFolder("unused")
        File(system, "scph1001.bin").writeBytes(bytes(1))
        File(system, "scph7001.bin").writeBytes(bytes(2))

        val slot = BiosAssigner.assign(system, unused, ps1, null, "minha_bios_ps1.rom", ByteArrayInputStream(bytes(9, 9)))

        assertEquals("scph5501.bin", slot)
        assertArrayEquals(bytes(9, 9), File(system, "scph5501.bin").readBytes())
        assertFalse(File(system, "scph1001.bin").exists())
        assertFalse(File(system, "scph7001.bin").exists())
        assertArrayEquals(bytes(1), File(unused, "ps1/scph1001.bin").readBytes())
        assertArrayEquals(bytes(2), File(unused, "ps1/scph7001.bin").readBytes())
    }

    @Test
    fun knownNameKeepsItsSlotInAnyOfGroup() {
        val system = tmp.newFolder("system")
        val unused = tmp.newFolder("unused")
        File(system, "scph5501.bin").writeBytes(bytes(5))
        val slot = BiosAssigner.assign(system, unused, ps1, null, "SCPH1001.BIN", ByteArrayInputStream(bytes(1)))
        assertEquals("scph1001.bin", slot)
        assertTrue(File(system, "scph1001.bin").isFile)
        assertFalse(File(system, "scph5501.bin").exists())
    }

    @Test
    fun allOfGroupWritesToTheChosenSlotAndKeepsOthers() {
        val system = tmp.newFolder("system")
        val unused = tmp.newFolder("unused")
        File(system, "bios7.bin").writeBytes(bytes(7))
        val slot = BiosAssigner.assign(system, unused, nds, "bios9.bin", "qualquer.bin", ByteArrayInputStream(bytes(9)))
        assertEquals("bios9.bin", slot)
        assertArrayEquals(bytes(7), File(system, "bios7.bin").readBytes())
        assertArrayEquals(bytes(9), File(system, "bios9.bin").readBytes())
        assertEquals(0, unused.listFiles()?.size ?: 0)
    }

    @Test
    fun zipUsesFirstFileInside() {
        val system = tmp.newFolder("system")
        val unused = tmp.newFolder("unused")
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("pasta/")); z.closeEntry()
            z.putNextEntry(ZipEntry("pasta/SCPH5502.bin")); z.write(bytes(3, 3)); z.closeEntry()
        }
        val slot = BiosAssigner.assign(system, unused, ps1, null, "pack.zip", ByteArrayInputStream(out.toByteArray()))
        assertEquals("scph5502.bin", slot)
        assertArrayEquals(bytes(3, 3), File(system, "scph5502.bin").readBytes())
    }

    @Test
    fun slotMustBelongToTheGroup() {
        val system = tmp.newFolder("system")
        val unused = tmp.newFolder("unused")
        val r = runCatching {
            BiosAssigner.assign(system, unused, nds, "scph5501.bin", "x.bin", ByteArrayInputStream(bytes(1)))
        }
        assertTrue(r.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun onlyMultiFileAllOfGroupsNeedASlotChoice() {
        assertFalse(BiosAssigner.needsSlotChoice(ps1))
        assertTrue(BiosAssigner.needsSlotChoice(nds))
        assertFalse(BiosAssigner.needsSlotChoice(BiosCatalog.groups.first { it.platformId == "gba" }))
    }
}
