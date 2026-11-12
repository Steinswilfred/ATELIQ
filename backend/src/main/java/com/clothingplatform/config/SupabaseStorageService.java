
package com.clothingplatform.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.UUID;

@Service
public class SupabaseStorageService {

    private final RestClient restClient;

    @Value("${app.storage.supabase.url}")
    private String supabaseUrl;

    @Value("${app.storage.supabase.service-role-key}")
    private String serviceRoleKey;

    @Value("${app.storage.supabase.bucket}")
    private String bucket;

    public SupabaseStorageService() {
        this.restClient = RestClient.create();
    }

    public String uploadFile(MultipartFile file, String folder)
            throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        String originalName = file.getOriginalFilename();

        String extension = "";

        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(
                    originalName.lastIndexOf(".")
            ).toLowerCase(Locale.ROOT);
        }

        if (!extension.matches("\\.(jpg|jpeg|png|webp|glb)$")) {
            throw new IllegalArgumentException(
                    "Only JPG, JPEG, PNG, WEBP and GLB files are allowed"
            );
        }

        if (!folder.matches("[a-zA-Z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid folder name");
        }

        String fileName = UUID.randomUUID() + extension;
        String filePath = folder + "/" + fileName;

        String contentType = file.getContentType();

        if (contentType == null || contentType.isBlank()) {
            contentType = extension.equals(".glb")
                    ? "model/gltf-binary"
                    : "application/octet-stream";
        }

        URI uploadUri = UriComponentsBuilder
                .fromUriString(supabaseUrl)
                .path("/storage/v1/object/")
                .path(bucket)
                .path("/")
                .path(filePath)
                .build()
                .encode()
                .toUri();

        restClient.post()
                .uri(uploadUri)
                .header("apikey", serviceRoleKey)
                .header("Authorization", "Bearer " + serviceRoleKey)
                .header("x-upsert", "true")
                .contentType(MediaType.parseMediaType(contentType))
                .body(file.getBytes())
                .retrieve()
                .toBodilessEntity();

        return UriComponentsBuilder
                .fromUriString(supabaseUrl)
                .path("/storage/v1/object/public/")
                .path(bucket)
                .path("/")
                .path(filePath)
                .build()
                .encode()
                .toUriString();
    }
}
