#!/usr/bin/env python3
"""A tiny stand-in for the system under test.

Load tests need a target. Pointing CI (or a local smoke run) at a real third-party API is both
unreliable and rude, so this serves the handful of endpoints the shipped plans call. It is
deliberately trivial: the point is to exercise the runner end to end, not to benchmark anything.

    python3 scripts/mock-target.py --port 8099
"""

import argparse
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

# Endpoints the shipped plans request, with responses shaped enough to satisfy their extractors.
RESPONSES = {
    "/posts": [{"userId": 1, "id": 1, "title": "first post", "body": "..."},
               {"userId": 2, "id": 2, "title": "second post", "body": "..."}],
    "/users": [{"id": 1, "name": "Alice"}, {"id": 2, "name": "Bob"}],
    "/comments": [{"postId": 1, "id": 1, "name": "a comment"}],
    "/albums": [{"userId": 1, "id": 1, "title": "an album"}],
}


class Handler(BaseHTTPRequestHandler):
    """Serves canned JSON for the plan endpoints."""

    protocol_version = "HTTP/1.1"

    def do_GET(self):  # noqa: N802 - name fixed by BaseHTTPRequestHandler
        """Returns canned JSON, ignoring any query string."""
        path = self.path.split("?", 1)[0]
        payload = RESPONSES.get(path)
        if payload is None:
            # /posts/1 and similar single-resource reads.
            parent = "/" + path.strip("/").split("/")[0]
            collection = RESPONSES.get(parent)
            payload = collection[0] if collection else {"ok": True}

        body = json.dumps(payload).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        """Silences per-request logging, which is pure noise under load."""


def main():
    """Parses arguments and serves until interrupted."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8099)
    parser.add_argument("--host", default="127.0.0.1")
    args = parser.parse_args()

    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"mock target listening on http://{args.host}:{args.port}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        server.shutdown()


if __name__ == "__main__":
    main()
