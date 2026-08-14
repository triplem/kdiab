# node 26-alpine (linux/amd64) — update digest after Node.js patch releases
FROM node:26-alpine@sha256:aadf416b2cdce311a8811ba3f0608a61b77dbf997500e2eafe781b51f6a0b019 AS build
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
FROM nginx:alpine@sha256:4a73073bd557c65b759505da037898b61f1be6cbcc3c2c3aeac22d2a470c1752
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY error.html /usr/share/nginx/html/error.html
RUN chown -R nginx:nginx /usr/share/nginx/html /var/cache/nginx /var/log/nginx /etc/nginx/conf.d \
    && touch /var/run/nginx.pid \
    && chown nginx:nginx /var/run/nginx.pid
USER nginx
EXPOSE 8080
