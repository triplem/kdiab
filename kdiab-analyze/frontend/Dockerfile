# Stage 1: Build the frontend
FROM node:25-alpine AS build

# Install Java required by openapi-generator-cli
RUN apk add --no-cache openjdk17-jre

WORKDIR /app
COPY frontend/package*.json ./frontend/
WORKDIR /app/frontend
RUN npm ci

WORKDIR /app
COPY api/ ./api/
COPY frontend/ ./frontend/

WORKDIR /app/frontend
ARG VITE_OIDC_AUTHORITY
ARG VITE_OIDC_CLIENT_ID
ENV VITE_OIDC_AUTHORITY=$VITE_OIDC_AUTHORITY
ENV VITE_OIDC_CLIENT_ID=$VITE_OIDC_CLIENT_ID
RUN npm run build

# Stage 2: Serve with Nginx
FROM nginx:alpine
COPY --from=build /app/frontend/dist /usr/share/nginx/html
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf

RUN chown -R nginx:nginx /usr/share/nginx/html /etc/nginx/conf.d \
    && mkdir -p /var/cache/nginx /var/run /var/log/nginx \
    && chown -R nginx:nginx /var/cache/nginx /var/run /var/log/nginx \
    && chmod -R 777 /var/run /var/cache/nginx /var/log/nginx

RUN sed -i 's/^user/#user/' /etc/nginx/nginx.conf \
    && sed -i -e '/^pid/s/.*/pid \/var\/run\/nginx.pid;/' /etc/nginx/nginx.conf

USER nginx

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -qO- http://localhost:8080/ || exit 1
CMD ["nginx", "-g", "daemon off;"]
