package dev.droidos.shell.apps.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalEngineTest {

    @Test
    fun builtinsAreRecognisedOnlyOnTheirOwnLine() {
        assertEquals(TerminalEngine.Builtin.CLEAR, TerminalEngine.builtin("  clear "))
        assertEquals(TerminalEngine.Builtin.CLEAR, TerminalEngine.builtin("cls"))
        assertEquals(TerminalEngine.Builtin.HELP, TerminalEngine.builtin("HELP"))
        assertEquals(TerminalEngine.Builtin.EXIT, TerminalEngine.builtin("exit"))
        assertNull(TerminalEngine.builtin("clear-all"))
        assertNull(TerminalEngine.builtin("ls"))
        assertNull(TerminalEngine.builtin(""))
    }

    @Test
    fun quotesEscapeSingleQuotesForPosixShell() {
        assertEquals("'/data/user/0/dev.droidos'", TerminalEngine.quote("/data/user/0/dev.droidos"))
        assertEquals("'it'\\''s'", TerminalEngine.quote("it's"))
    }

    @Test
    fun scriptRunsInsideTheWorkingDirectory() {
        assertEquals(
            "cd '/sdcard' && ( ls -l )",
            TerminalEngine.script("  ls -l  ", "/sdcard")
        )
        assertTrue(TerminalEngine.shellArgs("ls", "/").contains("/system/bin/sh"))
        assertEquals(
            listOf("/system/bin/sh", "-c", "cd '/' && ( id )"),
            TerminalEngine.shellArgs("id", "/")
        )
    }

    @Test
    fun cdDetectionNeedsTheCommandWord() {
        assertTrue(TerminalEngine.isCd("cd"))
        assertTrue(TerminalEngine.isCd("cd .."))
        assertTrue(!TerminalEngine.isCd("cdrom"))
        assertTrue(!TerminalEngine.isCd("ls cd"))
    }

    @Test
    fun resolvePathHandlesRelativeHomeAndParentSegments() {
        val home = "/data/user/0/dev.droidos/files"
        assertEquals("/data/user/0/dev.droidos/files", TerminalEngine.resolvePath("", "/sdcard", home))
        assertEquals(home, TerminalEngine.resolvePath("~", "/sdcard", home))
        assertEquals("$home/tmp", TerminalEngine.resolvePath("~/tmp", "/sdcard", home))
        assertEquals("/sdcard/Download", TerminalEngine.resolvePath("Download", "/sdcard", home))
        assertEquals("/sdcard", TerminalEngine.resolvePath("..", "/sdcard/Download", home))
        assertEquals("/", TerminalEngine.resolvePath("/../../..", "/sdcard", home))
        assertEquals("/system/bin", TerminalEngine.resolvePath("../bin", "/system/bin", home))
        assertEquals("/system/system", TerminalEngine.resolvePath("./../system/", "/system/bin", home))
    }

    @Test
    fun normalizeCollapsesSeparatorsDotsAndParentSegments() {
        assertEquals("/a/b", TerminalEngine.normalize("/a//b/."))
        assertEquals("/a", TerminalEngine.normalize("/a/b/.."))
        assertEquals("/", TerminalEngine.normalize("/"))
        assertEquals("/", TerminalEngine.normalize("/../.."))
        assertEquals("relative/path", TerminalEngine.normalize("relative//path"))
    }

    @Test
    fun promptShortensHomeAndExitLabelKeepsTheCode() {
        assertEquals("~", TerminalEngine.prompt("/data/files", "/data/files"))
        assertEquals("/sdcard", TerminalEngine.prompt("/sdcard", "/data/files"))
        assertEquals("exit 0", TerminalEngine.exitLabel(0))
        assertEquals("exit 127", TerminalEngine.exitLabel(127))
    }
}
