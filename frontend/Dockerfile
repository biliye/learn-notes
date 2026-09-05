# ---------- 构建阶段 ----------
FROM node:20-alpine AS build
WORKDIR /app
# lockfile 必须进构建：npm ci 锁定依赖版本，防 install 时 transitive dependency 漂移/投毒
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# ---------- 运行阶段 ----------
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
