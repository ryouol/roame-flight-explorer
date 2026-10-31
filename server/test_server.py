import json
from pathlib import Path
import tempfile
import threading
import unittest
from urllib.error import HTTPError
from urllib.request import urlopen

from server.server import DEFAULT_FIXTURE, make_server


class FlightApiTests(unittest.TestCase):
    def setUp(self):
        self.server = make_server(port=0)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.url = f"http://127.0.0.1:{self.server.server_port}"

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()

    def test_returns_exact_fixture_and_json_content_type(self):
        with urlopen(self.url + "/flights") as response:
            self.assertEqual(response.status, 200)
            self.assertEqual(response.headers.get_content_type(), "application/json")
            payload = response.read()
        self.assertEqual(payload, DEFAULT_FIXTURE.read_bytes())
        self.assertEqual(len(json.loads(payload)["flights"]), 1000)

    def test_unknown_path_is_404(self):
        with self.assertRaises(HTTPError) as caught:
            urlopen(self.url + "/private")
        self.assertEqual(caught.exception.code, 404)
        caught.exception.close()

    def test_alternate_empty_fixture(self):
        with tempfile.TemporaryDirectory() as directory:
            fixture = Path(directory) / "empty.json"
            fixture.write_text('{"flights": []}')
            with make_server(port=0, fixture=fixture) as server:
                thread = threading.Thread(target=server.handle_request)
                thread.start()
                with urlopen(f"http://127.0.0.1:{server.server_port}/flights") as response:
                    self.assertEqual(json.load(response), {"flights": []})
                thread.join()


if __name__ == "__main__":
    unittest.main()
