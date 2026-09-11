package com.gongguhan.domain.product.service;

import org.springframework.web.multipart.MultipartFile;

public interface ProductExtractionClient {

    AiModuleExtraction extract(MultipartFile representativeImage);
}
