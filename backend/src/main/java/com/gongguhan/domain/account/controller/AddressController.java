package com.gongguhan.domain.account.controller;

import com.gongguhan.domain.account.dto.AddressCreateRequest;
import com.gongguhan.domain.account.dto.AddressResponse;
import com.gongguhan.domain.account.dto.AddressUpdateRequest;
import com.gongguhan.domain.account.service.AddressService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 배송지 API (USR-03~06). */
@RestController
@RequestMapping("/users/me/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AddressResponse>>> getAddresses(
            @AuthenticationPrincipal Long userId, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                addressService.getAddresses(userId), TraceIdUtils.from(httpRequest)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AddressResponse>> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody AddressCreateRequest request,
            HttpServletRequest httpRequest) {
        AddressResponse response = addressService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, TraceIdUtils.from(httpRequest)));
    }

    @PatchMapping("/{addressId}")
    public ResponseEntity<ApiResponse<AddressResponse>> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long addressId,
            @Valid @RequestBody AddressUpdateRequest request,
            HttpServletRequest httpRequest) {
        AddressResponse response = addressService.update(userId, addressId, request);
        return ResponseEntity.ok(ApiResponse.success(response, TraceIdUtils.from(httpRequest)));
    }

    @DeleteMapping("/{addressId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long addressId,
            HttpServletRequest httpRequest) {
        addressService.delete(userId, addressId);
        return ResponseEntity.ok(ApiResponse.success(null, TraceIdUtils.from(httpRequest)));
    }
}
