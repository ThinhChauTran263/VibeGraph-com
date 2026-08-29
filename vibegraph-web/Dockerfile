# Multi-stage build for Vue frontend

# D-M2: pin the minor/patch tag so builds are reproducible.
FROM node:22.12.0-alpine AS builder
WORKDIR /build
ARG VITE_API_URL
ARG VITE_WS_URL
ARG VIBEGRAPH_DOCKER_BUILD=false
ENV VITE_API_URL=$VITE_API_URL
ENV VITE_WS_URL=$VITE_WS_URL
ENV VIBEGRAPH_DOCKER_BUILD=$VIBEGRAPH_DOCKER_BUILD
COPY package*.json ./
RUN npm ci
COPY . .
# Import every public VITE_* build setting from the deployment .env without
# copying backend credentials into the build context, cache, or final image.
RUN --mount=type=secret,id=frontend_env,required=false \
    if [ -f /run/secrets/frontend_env ]; then \
      while IFS='=' read -r key value; do \
        value="$(printf '%s' "$value" | tr -d '\r')"; \
        case "$key" in VITE_*) export "$key=$value" ;; esac; \
      done < /run/secrets/frontend_env; \
    fi; \
    npm run build

FROM nginx:1.27-alpine
COPY --from=builder /build/dist /usr/share/nginx/html
# Rendered by the image entrypoint so ${CSP_CONNECT_SRC} can name the API origin
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
