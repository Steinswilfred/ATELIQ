package com.clothingplatform.storage;

import com.clothingplatform.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Development storage on local disk. The file type is decided from the file's own bytes, never from the
 * client-supplied name or content type, and files get random names. SVG is refused (it can carry scripts).
 */
@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {
    private final Path root;

    public LocalStorageService(@Value("${app.storage.local-dir}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    public Path root() { return root; }

    @Override
    public String storeImage(String folder, MultipartFile file) {
        if (file == null || file.isEmpty()) throw bad("Choose an image to upload.");
        if (file.getSize() > MAX_IMAGE_BYTES) throw bad("The image is larger than 5 MB.");
        if (!folder.matches("^[a-z0-9-]{1,40}$")) throw new IllegalArgumentException("bad folder");
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(16);
            String ext = detect(head);
            if (ext == null) throw bad("Only JPEG, PNG, WebP or GIF images are allowed.");
            Path dir = root.resolve(folder).normalize();
            Files.createDirectories(dir);
            String name = UUID.randomUUID() + "." + ext;
            Path target = dir.resolve(name).normalize();
            if (!target.startsWith(root)) throw bad("Invalid upload.");
            try (InputStream again = file.getInputStream()) { Files.copy(again, target, StandardCopyOption.REPLACE_EXISTING); }
            return "/media/" + folder + "/" + name;
        } catch (IOException e) {
            throw new BusinessException("UPLOAD_FAILED", "The image could not be saved.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final long MAX_MODEL_BYTES = 20L * 1024 * 1024;

    @Override
    public String storeModel(MultipartFile file) {
        if (file == null || file.isEmpty()) throw bad("Choose a .glb model to upload.");
        if (file.getSize() > MAX_MODEL_BYTES) throw bad("The model is larger than 20 MB.");
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(4);
            if (!isGlb(head)) throw bad("That is not a binary glTF (.glb) file.");
            Path dir = root.resolve("models").normalize();
            Files.createDirectories(dir);
            String name = UUID.randomUUID() + ".glb";
            Path target = dir.resolve(name).normalize();
            if (!target.startsWith(root)) throw bad("Invalid upload.");
            try (InputStream again = file.getInputStream()) { Files.copy(again, target, StandardCopyOption.REPLACE_EXISTING); }
            return "/media/models/" + name;
        } catch (IOException e) {
            throw new BusinessException("UPLOAD_FAILED", "The model could not be saved.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    static boolean isGlb(byte[] b) { return b.length >= 4 && b[0] == 'g' && b[1] == 'l' && b[2] == 'T' && b[3] == 'F'; }

    static String detect(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "jpg";
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "png";
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') return "gif";
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "webp";
        return null;
    }

    private BusinessException bad(String m) { return new BusinessException("INVALID_FILE", m, HttpStatus.BAD_REQUEST); }
}
