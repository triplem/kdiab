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
FROM nginx:alpine@sha256:8b1e78743a03dbb2c95171cc58639fef29abc8816598e27fb910ed2e621e589a
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/templates/default.conf.template
RUN chown -R nginx:nginx /usr/share/nginx/html /var/cache/nginx /var/log/nginx /etc/nginx/conf.d \
    && touch /var/run/nginx.pid \
    && chown nginx:nginx /var/run/nginx.pid
USER nginx
EXPOSE 8080
