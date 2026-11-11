package com.clothingplatform.storage;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    /** @return a URL path the browser can load */
    String storeImage(String folder, MultipartFile file);

    /** Stores a binary glTF (.glb) 3D model after checking its header. */
    String storeModel(MultipartFile file);
}
