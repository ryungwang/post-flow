# Build the SPA from the monorepo and serve the static bundle via Caddy on internal :8080.
# Domain routing + HTTPS are handled by the server-side Caddy (not here). Build context = repo root.
FROM node:22-alpine AS build
RUN corepack enable
WORKDIR /repo
# Workspace manifests first (better layer caching for installs)
COPY pnpm-workspace.yaml pnpm-lock.yaml package.json turbo.json ./
COPY apps/web/package.json apps/web/package.json
RUN pnpm install --frozen-lockfile --filter @postflow/web...
# Source + build-time public config (Vite inlines VITE_* at build time)
COPY apps/web apps/web
ARG VITE_API_BASE_URL=/api
ARG VITE_GOOGLE_CLIENT_ID=
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_GOOGLE_CLIENT_ID=$VITE_GOOGLE_CLIENT_ID
RUN pnpm --filter @postflow/web build

FROM caddy:2-alpine
COPY --from=build /repo/apps/web/dist /srv
# SPA fallback: unknown routes serve index.html so client-side routing works.
RUN printf ':8080 {\n\troot * /srv\n\tencode gzip\n\ttry_files {path} /index.html\n\tfile_server\n}\n' > /etc/caddy/Caddyfile
EXPOSE 8080
