# syntax=docker/dockerfile:1
# Frontend React compilado con Vite y servido como sitio estático por nginx.
# Contexto de construcción: raíz del repositorio.

FROM node:22-alpine AS build
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
# URL pública del backend tal como la ve el navegador; queda dentro del bundle,
# así que nunca debe contener credenciales.
ARG VITE_API_URL=http://localhost:8080
ENV VITE_API_URL=${VITE_API_URL}
RUN npm run build

FROM nginx:1.27-alpine
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=10s --timeout=5s --retries=6 \
    CMD wget -qO- http://127.0.0.1/ >/dev/null || exit 1
