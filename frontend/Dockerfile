# ---- Stage 1: 빌드 ----
FROM node:22-alpine AS builder
WORKDIR /app
RUN corepack enable
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
ARG NEXT_PUBLIC_API_URL
ARG NEXT_PUBLIC_USE_MOCK_API=false
ARG NEXT_PUBLIC_AUTH_REFRESH_ENABLED=true
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL
ENV NEXT_PUBLIC_USE_MOCK_API=$NEXT_PUBLIC_USE_MOCK_API
ENV NEXT_PUBLIC_AUTH_REFRESH_ENABLED=$NEXT_PUBLIC_AUTH_REFRESH_ENABLED
RUN pnpm build

# ---- Stage 2: 실행 ----
FROM node:22-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
COPY --from=builder /app/.next/standalone ./
COPY --from=builder /app/.next/static ./.next/static
COPY --from=builder /app/public ./public
EXPOSE 3000
CMD ["node", "server.js"]
