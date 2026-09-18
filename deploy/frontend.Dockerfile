# syntax=docker/dockerfile:1
#
# 프론트엔드(React + Vite) 프로덕션 이미지.
#
# SPA 는 정적 파일이라 "서버"가 필요 없다. 그래서 이 이미지는 곧 웹서버(Nginx)이고,
# 리버스 프록시 역할도 같은 Nginx 가 함께 맡는다(= 이 서비스가 유일한 공개 진입점).
#
# 빌드 컨텍스트는 frontend/ 디렉터리 기준이다.
# API 주소는 상대경로(/api/v1)이므로 별도 빌드 인자가 필요 없다 —
# 브라우저는 자기 오리진(https://j15b205.p.ssafy.io)의 /api 로 요청하고 Nginx 가 백엔드로 넘긴다.

# ---- 1단계: 정적 번들 빌드 ----
FROM node:22-bookworm-slim AS build
WORKDIR /app

# 재현 가능한 설치를 위해 lockfile 기준 ci 사용.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# tsc 타입체크 + vite build → dist/ 생성. VITE_API_BASE_URL 기본값(/api/v1)을 사용.
ENV VITE_API_MOCKING=false
RUN npm run build

# ---- 2단계: Nginx 정적 서빙 + 리버스 프록시 ----
FROM nginx:1.27-alpine AS run
# 기본 설정을 지우고 우리 설정으로 대체(정적 서빙 + /api 프록시 + 보안 헤더).
RUN rm /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
# default.conf 는 compose 에서 볼륨으로 마운트한다(TLS 인증서 갱신 시 이미지 재빌드 불필요).
EXPOSE 80 443
