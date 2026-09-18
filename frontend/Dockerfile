# 프론트 이미지 (Vite 빌드 결과를 nginx 가 서빙)
#
# 빌드 결과는 정적 파일이라 Node 런타임이 필요 없습니다. nginx 로 바꾸면 이미지가
# 수백 MB 에서 수십 MB 로 줄고, 서버에 Node 취약점 표면이 남지 않습니다.

# ---------- 빌드 ----------
FROM node:22-alpine AS build
WORKDIR /app

# 잠금 파일 기준으로 정확히 같은 버전을 설치합니다. npm install 은 잠금 파일을
# 갱신할 수 있어 "내 PC 에서는 됐는데" 가 생깁니다.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---------- 실행 ----------
FROM nginx:1.27-alpine

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
