# Office Dreams — production image (no npm dependencies)
FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production NODE_NO_WARNINGS=1
COPY . .
RUN node build.js && node scripts/make-assets.js && mkdir -p data
EXPOSE 8080
VOLUME ["/app/data"]
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s CMD wget -qO- http://127.0.0.1:8080/healthz || exit 1
CMD ["node", "server/index.js"]
