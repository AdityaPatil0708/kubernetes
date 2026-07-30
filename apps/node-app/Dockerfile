FROM node:20-alpine

WORKDIR /app
COPY server.js .

# node:* images ship a non-root `node` user (uid 1000) already.
USER node

EXPOSE 8080
CMD ["node", "server.js"]
