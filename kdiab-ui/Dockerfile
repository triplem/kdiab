# node 26-alpine (linux/amd64) — update digest after Node.js patch releases
FROM node:26-alpine@sha256:95034e722cecec716c00830160848aab85c7b8180a131bb4f4fed9d5278f0989 AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci --silent
COPY . .
RUN npm run build

# nginx:alpine (linux/amd64) — update digest after nginx patch releases
FROM nginx:alpine@sha256:31db9045b34376493fd4fb695d8a8d03bbfffaf6297fcde82d0f925ce2dd329a
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/templates/default.conf.template
RUN chown -R nginx:nginx /usr/share/nginx/html /var/cache/nginx /var/log/nginx /etc/nginx/conf.d \
    && touch /var/run/nginx.pid \
    && chown nginx:nginx /var/run/nginx.pid
USER nginx
EXPOSE 8080
