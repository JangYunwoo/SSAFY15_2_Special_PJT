package com.gongguhan.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 로컬 수동 검증용 OpenAPI 문서 구성.
 *
 * <p>전체 API와 함께 김태민 담당 도메인만 골라 볼 수 있는 별도 그룹을 제공한다.
 * Swagger UI의 Authorize에는 로그인 응답의 accessToken만 입력하면 된다.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI gongguhanOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("공구한 API")
                        .description("Controller와 DTO에서 생성한 실제 백엔드 계약입니다. "
                                + "김태민 담당 그룹에서는 상품·공동구매·카카오페이·반품 API만 확인할 수 있습니다.")
                        .version("v1"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("/auth/login 응답의 accessToken 입력")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    @Bean
    public GroupedOpenApi kimTaeminApi() {
        return GroupedOpenApi.builder()
                .group("kim-taemin")
                .displayName("김태민 담당 + 테스트 인증")
                .pathsToMatch(
                        "/auth/login",
                        "/categories",
                        "/sellers/products/**",
                        "/withdrawal-restriction-reasons",
                        "/admin/products/**",
                        "/group-buys/**",
                        "/sellers/group-buys/**",
                        "/payments/**/kakaopay/**",
                        "/payments/kakaopay/**",
                        "/users/me/participations/**/returns/**",
                        "/users/me/returns/**",
                        "/sellers/returns/**",
                        "/admin/returns/**")
                .build();
    }
}
