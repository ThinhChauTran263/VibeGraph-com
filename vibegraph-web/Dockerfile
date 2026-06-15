# Multi-stage build for Vue frontend

FROM node:22-alpine AS builder
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
RUN npm run build

FROM nginx:alpine
COPY --from=builder /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
