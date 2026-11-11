package com.clothingplatform.approval;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FabricSampleServiceTest {
    @Test
    void acceptsOnlyServerStoredSamplePhotos() {
        assertTrue(FabricSampleService.validPhotoUrl("/media/samples/123e4567-e89b-12d3-a456-426614174000.jpg"));
        assertTrue(FabricSampleService.validPhotoUrl("/media/samples/123e4567-e89b-12d3-a456-426614174000.webp"));
        assertFalse(FabricSampleService.validPhotoUrl("https://evil.example/x.jpg"));
        assertFalse(FabricSampleService.validPhotoUrl("/media/catalog/123e4567-e89b-12d3-a456-426614174000.jpg"));
        assertFalse(FabricSampleService.validPhotoUrl("/media/samples/../../etc/passwd"));
        assertFalse(FabricSampleService.validPhotoUrl("/media/samples/123e4567-e89b-12d3-a456-426614174000.svg"));
        assertFalse(FabricSampleService.validPhotoUrl(null));
    }
}
