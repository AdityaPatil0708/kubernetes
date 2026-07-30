const http = require('node:http');
const os = require('node:os');

const PORT = process.env.PORT || 8080;
const APP = 'node-app';

const server = http.createServer((req, res) => {
  if (req.url === '/healthz') {
    res.writeHead(200, { 'Content-Type': 'text/plain' });
    res.end('ok\n');
    return;
  }

  const body = JSON.stringify({
    app: APP,
    version: process.env.APP_VERSION || 'unset',
    greeting: process.env.GREETING || 'unset',
    pod: os.hostname(),
  });

  res.writeHead(200, { 'Content-Type': 'application/json' });
  res.end(body + '\n');
});

server.listen(PORT, () => console.log(`${APP} listening on :${PORT}`));

// Without this, kubectl delete/rollout waits the full terminationGracePeriodSeconds
// before SIGKILL, which makes every rolling update look 30s slower than it is.
for (const sig of ['SIGTERM', 'SIGINT']) {
  process.on(sig, () => server.close(() => process.exit(0)));
}
