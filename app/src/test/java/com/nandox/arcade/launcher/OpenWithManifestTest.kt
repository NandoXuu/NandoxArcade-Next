package com.nandox.arcade.launcher

import com.nandox.arcade.core.Platforms
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Garante que toda extensão suportada aparece em "Abrir com". Se falhar: python3 scripts/gen_rom_filters.py */
class OpenWithManifestTest {
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    @Test
    fun everySupportedExtensionHasAnOpenWithFilter() {
        for (ext in Platforms.all.flatMap { it.extensions }.toSet()) {
            for (variant in listOf(ext.lowercase(), ext.uppercase())) {
                assertTrue("falta filtro para .$variant", manifest.contains("""android:pathPattern=".*\\.$variant""""))
            }
            assertTrue("falta variante com vários pontos para .$ext", manifest.contains("""\\..*\\.$ext""""))
        }
    }

    @Test
    fun openGameActivityAppearsInTheShareSheet() {
        val block = manifest.substringAfter(".launcher.OpenGameActivity").substringBefore("</activity>")
        assertTrue(block.contains("android.intent.action.SEND\""))
        assertTrue(block.contains("android.intent.action.SEND_MULTIPLE"))
    }

    @Test
    fun openGameActivityIsExportedAndHandlesView() {
        val block = manifest.substringAfter(".launcher.OpenGameActivity").substringBefore("</activity>")
        assertTrue(block.contains("""android:exported="true""""))
        assertTrue(block.contains("android.intent.action.VIEW"))
        assertTrue(block.contains("""android:mimeType="*/*""""))
    }
}
