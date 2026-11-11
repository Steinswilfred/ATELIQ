package com.clothingplatform.common;

import static org.junit.jupiter.api.Assertions.*;

import com.clothingplatform.exception.BusinessException;
import org.junit.jupiter.api.Test;

class UrlsTest {
    @Test
    void acceptsBuiltinAndGlbModels() {
        assertEquals("builtin:shirt", Urls.cleanModel("builtin:shirt"));
        assertEquals("/media/models/a.glb", Urls.cleanModel("/media/models/a.glb"));
        assertNull(Urls.cleanModel(" "));
    }

    @Test
    void refusesOtherModelAddresses() {
        assertThrows(BusinessException.class, () -> Urls.cleanModel("javascript:alert(1)"));
        assertThrows(BusinessException.class, () -> Urls.cleanModel("builtin:evil"));
        assertThrows(BusinessException.class, () -> Urls.cleanModel("https://x.test/model.obj"));
    }
}
