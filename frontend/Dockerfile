# Image frontend: build bằng Node, chạy bằng nginx (không root, port 8080). Build context = thư mục frontend/:
#   docker build -t frontend frontend
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.27-alpine
LABEL org.opencontainers.image.source="https://github.com/MarkusKeithThomas/unbutu-platform" \
      org.opencontainers.image.title="frontend"
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
