# node 26-alpine (linux/amd64) — update digest after Node.js patch releases
FROM node:26-alpine@sha256:7c6af15abe4e3de859690e7db171d0d711bf37d27528eddfe625b2fe89e097f8 AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci --silent
COPY . .
ARG VITE_OIDC_AUTHORITY=http://localhost:8081/realms/kdiab
ENV VITE_OIDC_AUTHORITY=$VITE_OIDC_AUTHORITY
ARG VITE_OIDC_CLIENT_ID=kdiab-ui
ENV VITE_OIDC_CLIENT_ID=$VITE_OIDC_CLIENT_ID
ARG VITE_FOOD_DATABASE_ENABLED=false
ENV VITE_FOOD_DATABASE_ENABLED=$VITE_FOOD_DATABASE_ENABLED
RUN npm run build

# nginx:alpine (linux/amd64) — update digest after nginx patch releases
# Updated 2026-06-04: libxml2 2.13.9-r1 fixes CVE-2026-6732 (HIGH, DoS)
FROM nginx:alpine@sha256:31db9045b34376493fd4fb695d8a8d03bbfffaf6297fcde82d0f925ce2dd329a
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY error.html /usr/share/nginx/html/error.html
RUN chown -R nginx:nginx /usr/share/nginx/html /var/cache/nginx /var/log/nginx /etc/nginx/conf.d \
    && touch /var/run/nginx.pid \
    && chown nginx:nginx /var/run/nginx.pid
USER nginx
EXPOSE 8080
