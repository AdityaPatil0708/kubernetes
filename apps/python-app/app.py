import json
import os
import socket
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(os.environ.get("PORT", 8080))
APP = "python-app"


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/healthz":
            self._respond(200, "text/plain", b"ok\n")
            return

        body = json.dumps(
            {
                "app": APP,
                "version": os.environ.get("APP_VERSION", "unset"),
                "greeting": os.environ.get("GREETING", "unset"),
                "pod": socket.gethostname(),
            }
        ).encode()
        self._respond(200, "application/json", body + b"\n")

    def _respond(self, status, content_type, body):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        if os.environ.get("LOG_LEVEL", "info") == "debug":
            super().log_message(fmt, *args)


if __name__ == "__main__":
    # No SIGTERM handler here on purpose: Python's default action for SIGTERM
    # already exits immediately, and calling server.shutdown() from the same
    # thread that runs serve_forever() deadlocks until SIGKILL.
    server = ThreadingHTTPServer(("", PORT), Handler)
    print(f"{APP} listening on :{PORT}", flush=True)
    server.serve_forever()
