FROM node:24-alpine
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# rewrites /api → backend và /agent → agent được ghi vào bản build, nên phải có lúc build.
ARG BACKEND_URL=http://backend:8080
ARG AGENT_URL=http://agent:8000
ENV BACKEND_URL=$BACKEND_URL
ENV AGENT_URL=$AGENT_URL
RUN npm run build
EXPOSE 3000
CMD ["npm", "start"]
