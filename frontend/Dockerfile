FROM node:24-alpine
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# rewrites /api → backend được ghi vào bản build, nên BACKEND_URL phải có lúc build.
ARG BACKEND_URL=http://backend:8080
ENV BACKEND_URL=$BACKEND_URL
RUN npm run build
EXPOSE 3000
CMD ["npm", "start"]
