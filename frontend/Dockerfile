# ---------- 构建阶段 ----------
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json ./
# npm ci 需要 lockfile；没有则退化为 npm install
RUN npm install --no-audit --no-fund
COPY . .
RUN npm run build

# ---------- 运行阶段 ----------
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
