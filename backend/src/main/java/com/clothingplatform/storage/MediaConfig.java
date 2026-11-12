
package com.clothingplatform.storage;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final SupabaseStorageService storageService;

    public MediaController(SupabaseStorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "catalog") String folder
    ) throws IOException {

        String url = storageService.upload(file, folder);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "url", url
        ));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<?> delete(
            @RequestParam("path") String path
    ) {

        storageService.delete(path);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "File deleted successfully"
        ));
    }
}
