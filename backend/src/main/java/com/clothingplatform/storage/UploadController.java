package com.clothingplatform.storage;

import com.clothingplatform.common.ApiResponse;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/uploads")
public class UploadController {
    private final StorageService storage;
    public UploadController(StorageService storage) { this.storage = storage; }

    @PostMapping("/images")
    @PreAuthorize("hasAnyAuthority('PRODUCT_WRITE','CATEGORY_MANAGE','FABRIC_MANAGE','CUSTOMIZATION_MANAGE','SETTINGS_MANAGE')")
    public ApiResponse<Map<String, String>> image(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(Map.of("url", storage.storeImage("catalog", file)));
    }

    @PostMapping("/models")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public ApiResponse<Map<String, String>> model(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(Map.of("url", storage.storeModel(file)));
    }
}
