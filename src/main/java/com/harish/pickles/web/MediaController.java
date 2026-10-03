package com.harish.pickles.web;

import com.harish.pickles.service.StorageService;
import io.swagger.v3.oas.annotations.Hidden;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.time.Duration;

/**
 * Serves uploaded product photos. Kept as a controller rather than a static
 * resource handler so the folder can live anywhere on disk and a missing file
 * returns a clean 404 instead of an error page.
 */
@Hidden
@Controller
public class MediaController {

    private final StorageService storageService;

    public MediaController(StorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping("/media/products/{filename}")
    public ResponseEntity<Resource> product(@PathVariable String filename) {
        Path path;
        try {
            path = storageService.resolve(filename);
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().build();
        }
        if (!Files.isReadable(path)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(storageService.contentTypeFor(filename)))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(new FileSystemResource(path));
    }
}
