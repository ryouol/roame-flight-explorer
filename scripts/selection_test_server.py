#!/usr/bin/env python3
"""Serve fixtures with one delayed empty refresh for the iOS destination-sheet test.

Stop the normal API, then run: python3 scripts/selection_test_server.py
This test-only server binds to localhost and reuses the production fixture handler.
"""

from pathlib import Path
import sys
from threading import Event
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from server.server import make_server


def main():
    with make_server() as server:
        empty_next = Event()
        fixture_handler = server.RequestHandlerClass

        class SelectionHandler(fixture_handler):
            def do_POST(self):
                if self.path != "/test/empty-next-refresh":
                    self.send_error(404)
                    return
                empty_next.set()
                self.send_response(204)
                self.end_headers()

            def do_GET(self):
                if self.path != "/flights" or not empty_next.is_set():
                    super().do_GET()
                    return
                empty_next.clear()
                # Allow the native test to open the destination while refresh is in flight.
                time.sleep(8)
                payload = b'{"flights":[]}'
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(payload)))
                self.send_header("Cache-Control", "no-store")
                self.end_headers()
                self.wfile.write(payload)

        server.RequestHandlerClass = SelectionHandler
        print("Selection test API: http://localhost:8080/flights", flush=True)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass


if __name__ == "__main__":
    main()
