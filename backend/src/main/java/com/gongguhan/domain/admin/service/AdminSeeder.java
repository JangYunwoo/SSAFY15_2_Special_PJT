package com.gongguhan.domain.admin.service;

import com.gongguhan.domain.admin.entity.Admin;
import com.gongguhan.domain.admin.repository.AdminRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 초기 관리자 계정 시드.
 *
 * <p>관리자는 {@code admins} 테이블에 있어야 로그인할 수 있는데 시드가 없으면 아무도
 * 로그인할 수 없다(가입 경로도 없음). 계정이 하나도 없을 때만 설정값 기반으로 하나 만든다.
 * 비밀번호는 {@link PasswordEncoder}로 해싱하므로 마이그레이션에 해시를 박아둘 필요가 없다.
 * 운영에서는 {@code app.admin.seed-enabled=false}로 끄고 별도 절차로 만든다.
 */
@Component
public class AdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String email;
    private final String password;
    private final String name;

    public AdminSeeder(AdminRepository adminRepository, PasswordEncoder passwordEncoder,
                       @Value("${app.admin.seed-enabled:true}") boolean enabled,
                       @Value("${app.admin.seed-email:admin@gongguhan.dev}") String email,
                       @Value("${app.admin.seed-password:admin1234!}") String password,
                       @Value("${app.admin.seed-name:플랫폼 관리자}") String name) {
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.email = email;
        this.password = password;
        this.name = name;
    }

    @Override
    public void run(String... args) {
        if (!enabled || adminRepository.count() > 0) {
            return;
        }
        adminRepository.save(Admin.create(email, passwordEncoder.encode(password), name, "SUPER"));
        log.info("초기 관리자 계정 생성: {} (기본 비밀번호는 설정값 app.admin.seed-password)", email);
    }
}
