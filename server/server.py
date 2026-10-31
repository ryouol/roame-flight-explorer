#!/usr/bin/env python3
"""Local, read-only Flight Explorer fixture API. No third-party packages."""

import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
from urllib.parse import urlsplit

DEFAULT_FIXTURE = Path(__file__).resolve().parents[1] / "fixtures" / "flights.json"


def make_server(host="127.0.0.1", port=8080, fixture=DEFAULT_FIXTURE):
    payload = Path(fixture).read_bytes()
    document = json.loads(payload)
    if not isinstance(document, dict) or not isinstance(document.get("flights"), list):
        raise ValueError("Fixture must contain a JSON object with a flights array")

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            if urlsplit(self.path).path != "/flights":
                self.send_error(404, "Use GET /flights")
                return
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(payload)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(payload)

    return ThreadingHTTPServer((host, port), Handler)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8080)
    parser.add_argument("--fixture", type=Path, default=DEFAULT_FIXTURE)
    args = parser.parse_args()
    with make_server(port=args.port, fixture=args.fixture) as server:
        print(f"Flight API: http://localhost:{server.server_port}/flights", flush=True)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            print("\nStopped.")


if __name__ == "__main__":
    main()
