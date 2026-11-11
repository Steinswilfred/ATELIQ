package com.clothingplatform.storage;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LocalStorageServiceTest {
    @Test
    void detectsImagesByContentNotName() {
        assertEquals("jpg", LocalStorageService.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0}));
        assertEquals("png", LocalStorageService.detect(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0}));
        assertEquals("gif", LocalStorageService.detect("GIF89a......".getBytes()));
        assertEquals("webp", LocalStorageService.detect("RIFF....WEBP".getBytes()));
    }

    @Test
    void refusesScriptsAndSvg() {
        assertNull(LocalStorageService.detect("<svg xmlns=\"http".getBytes()));
        assertNull(LocalStorageService.detect("<script>alert(1)".getBytes()));
        assertNull(LocalStorageService.detect(new byte[0]));
    }

    @Test
    void recognisesGlbHeaderOnly() {
        assertTrue(LocalStorageService.isGlb("glTF....".getBytes()));
        assertFalse(LocalStorageService.isGlb("<html>".getBytes()));
        assertFalse(LocalStorageService.isGlb(new byte[2]));
    }
}
