package com.gongguhan.domain.account.service;

import com.gongguhan.domain.account.dto.UserProfileResponse;
import com.gongguhan.domain.account.dto.UserUpdateRequest;
import com.gongguhan.domain.account.entity.User;
import com.gongguhan.domain.account.repository.UserRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용자 프로필 조회·수정 (USR-01·02, S15P21B205-63). */
@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserProfileResponse getProfile(Long userId) {
        return UserProfileResponse.from(findUser(userId));
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UserUpdateRequest request) {
        User user = findUser(userId);
        user.updateProfile(request.nickname(), request.phone(), request.ssafyAccountNo());
        return UserProfileResponse.from(user);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }
}
