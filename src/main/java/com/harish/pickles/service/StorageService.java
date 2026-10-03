package com.harish.pickles.service;

import com.harish.pickles.config.AppProperties;
import com.harish.pickles.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Writes uploaded product photos to a folder on disk and reads them back.
 *
 * <p>Two rules keep this safe: the stored name is always generated here (never
 * anything the browser sent, so {@code ../../etc/passwd} is impossible), and only
 * a short allow-list of image types is accepted.</p>
 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private static final Map<String, String> ALLOWED = Map.of(
            "image/jpeg", "jpg",
            "image/pjpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif");

    private final AppProperties props;
    private Path root;

    public StorageService(AppProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        this.root = Paths.get(props.getStorage().getUploadDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            log.info("Product photos will be stored in {}", root);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not create the upload folder " + root, ex);
        }
    }

    public Path getRoot() {
        return root;
    }

    /**
     * @return the generated filename to store against the product
     * @throws BusinessException when the file is empty, too large or not an image we accept
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("The selected file is empty");
        }
        long max = props.getStorage().getMaxFileSizeBytes();
        if (file.getSize() > max) {
            throw new BusinessException(displayName(file) + " is larger than "
                    + (max / (1024 * 1024)) + " MB");
        }
        String contentType = file.getContentType() == null
                ? "" : file.getContentType().toLowerCase(Locale.ROOT).trim();
        String extension = ALLOWED.get(contentType);
        if (extension == null) {
            throw new BusinessException(displayName(file)
                    + " is not a supported image. Use JPG, PNG, WEBP or GIF.");
        }

        String filename = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path target = root.resolve(filename).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException("Rejected an unsafe file name");
        }
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BusinessException("Could not save " + displayName(file) + ": " + ex.getMessage());
        }
        log.debug("Stored upload {} as {}", displayName(file), filename);
        return filename;
    }

    /** Resolves a stored filename to a path, refusing anything that escapes the root. */
    public Path resolve(String filename) {
        if (filename == null || filename.isBlank() || filename.contains("/") || filename.contains("\\")
                || filename.contains("..")) {
            throw new BusinessException("Invalid file name");
        }
        Path path = root.resolve(filename).normalize();
        if (!path.startsWith(root)) {
            throw new BusinessException("Invalid file name");
        }
        return path;
    }

    /** Best-effort removal. A missing file is not an error — the DB row is what matters. */
    public void delete(String filename) {
        try {
            Files.deleteIfExists(resolve(filename));
        } catch (IOException | BusinessException ex) {
            log.warn("Could not delete stored file {}: {}", filename, ex.getMessage());
        }
    }

    public String contentTypeFor(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        return "image/jpeg";
    }

    private static String displayName(MultipartFile file) {
        String name = file.getOriginalFilename();
        return (name == null || name.isBlank()) ? "The file" : name;
    }
}
