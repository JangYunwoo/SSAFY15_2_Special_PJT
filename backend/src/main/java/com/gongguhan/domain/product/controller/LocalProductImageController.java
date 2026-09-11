package com.gongguhan.domain.product.controller;

import com.gongguhan.domain.product.service.LocalProductImagePaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local")
@RequestMapping("/product-images")
public class LocalProductImageController {

    private static final Pattern STORED_NAME = Pattern.compile("[0-9a-f-]+\\.(?:jpg|png)");

    private final LocalProductImagePaths paths;

    public LocalProductImageController(LocalProductImagePaths paths) {
        this.paths = paths;
    }

    @GetMapping("/{storedName}")
    public ResponseEntity<Resource> get(@PathVariable String storedName) throws IOException {
        if (!STORED_NAME.matcher(storedName).matches()) {
            return ResponseEntity.notFound().build();
        }
        Path file = paths.resolve(storedName);
        if (!Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }
        MediaType mediaType = storedName.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(mediaType)
                .contentLength(Files.size(file))
                .body(new FileSystemResource(file));
    }
}
