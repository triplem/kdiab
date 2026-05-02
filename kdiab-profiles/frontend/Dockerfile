# Stage 1: Build the React app
FROM node:25-alpine AS builder

# Mirror the repo layout so '../api/openapi.yaml' in package.json scripts resolves correctly
WORKDIR /app/frontend

# openapi-generator-cli requires a JRE at code-generation time
RUN apk add --no-cache openjdk21-jre-headless

# Install dependencies in a separate layer for caching.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

# Copy the OpenAPI spec (referenced as ../api/openapi.yaml from the frontend workdir)
COPY api/openapi.yaml /app/api/openapi.yaml

# Copy the rest of the frontend source
COPY frontend/ ./

# Generate the API client and produce the production build
ARG VITE_OIDC_AUTHORITY
ARG VITE_OIDC_CLIENT_ID
ENV VITE_OIDC_AUTHORITY=$VITE_OIDC_AUTHORITY
ENV VITE_OIDC_CLIENT_ID=$VITE_OIDC_CLIENT_ID
RUN npm run build

# Stage 2: Serve with Nginx
FROM nginx:alpine

COPY --from=builder /app/frontend/dist /usr/share/nginx/html
# Template is processed by the nginx entrypoint using envsubst at container startup.
# Required env vars: KEYCLOAK_URL (browser-facing Keycloak origin), BACKEND_URL (internal backend address).
COPY frontend/nginx.conf.template /etc/nginx/templates/default.conf.template

# Grant permissions for rootless execution
RUN chown -R nginx:nginx /usr/share/nginx/html /etc/nginx/conf.d /etc/nginx/templates \
    && mkdir -p /var/cache/nginx /var/run /var/log/nginx \
    && chown -R nginx:nginx /var/cache/nginx /var/run /var/log/nginx \
    && chmod -R 777 /var/run /var/cache/nginx /var/log/nginx

# Write the PID to a directory accessible by the nginx user
RUN sed -i 's/^user/#user/' /etc/nginx/nginx.conf \
    && sed -i -e '/^pid/s/.*/pid \/var\/run\/nginx.pid;/' /etc/nginx/nginx.conf

USER nginx

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -qO- http://localhost:8080/ || exit 1
CMD ["nginx", "-g", "daemon off;"]
