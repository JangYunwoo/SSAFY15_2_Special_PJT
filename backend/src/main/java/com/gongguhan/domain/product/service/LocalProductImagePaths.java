package com.gongguhan.domain.product.service;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class LocalProductImagePaths {

    private final Path root;

    public LocalProductImagePaths(
            @Value("${app.storage.local.directory:${java.io.tmpdir}/gongguhan-product-images}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    public Path resolve(String storedName) {
        Path candidate = root.resolve(storedName).normalize();
        if (!candidate.startsWith(root)) {
            throw new IllegalArgumentException("허용되지 않은 이미지 경로입니다.");
        }
        return candidate;
    }
}
