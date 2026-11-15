package com.nandox.arcade.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RomIdentityTest {
    @Test
    fun n64ExtensionsAreRecognizedAutomatically() {
        for (name in listOf("Mario.z64", "Mario.N64", "mario.v64")) {
            assertEquals(RomMatch.Known("n64"), RomIdentity.match(name, emptyList()))
        }
    }

    @Test
    fun uniqueExtensionsResolveWithoutAsking() {
        assertEquals(RomMatch.Known("gba"), RomIdentity.match("a.gba", emptyList()))
        assertEquals(RomMatch.Known("nds"), RomIdentity.match("a.nds", emptyList()))
        assertEquals(RomMatch.Known("dreamcast"), RomIdentity.match("a.gdi", emptyList()))
        assertEquals(RomMatch.Known("ps1"), RomIdentity.match("a.cue", emptyList()))
    }

    @Test
    fun ambiguousExtensionAsksAmongCandidates() {
        val m = RomIdentity.match("game.iso", emptyList()) as RomMatch.Ask
        assertEquals(setOf("ps2", "psp", "gamecube", "wii"), m.candidates.map { it.id }.toSet())
    }

    @Test
    fun folderNameDisambiguates() {
        val folders = RomIdentity.folderNames("/document/primary:Roms/PSP/game.iso")
        assertEquals(listOf("document", "primary", "Roms", "PSP"), folders)
        assertEquals(RomMatch.Known("psp"), RomIdentity.match("game.iso", folders))
    }

    @Test
    fun unknownExtensionOffersAllPlatforms() {
        val m = RomIdentity.match("readme.txt", emptyList()) as RomMatch.Ask
        assertTrue(m.candidates.size == Platforms.all.size)
    }
}
