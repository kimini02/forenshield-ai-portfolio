#!/usr/bin/env python3
"""Loopback-only Blockchain HTTP stub for the isolated C4 Gatling verification."""

import json
import os
from http.server import BaseHTTPRequestHandler, HTTPServer


class Handler(BaseHTTPRequestHandler):
    calls = 0

    def do_POST(self):
        Handler.calls += 1
        self.rfile.read(int(self.headers.get("Content-Length", "0")))
        body = json.dumps({
            "transactionHash": f"0xc4-gatling-{Handler.calls}",
            "blockNumber": 6000 + Handler.calls,
        }).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)
        print(f"C4_BLOCKCHAIN_STUB_CALL\t{Handler.calls}", flush=True)

    def log_message(self, *_args):
        return


if __name__ == "__main__":
    port = int(os.environ.get("C4_BLOCKCHAIN_STUB_PORT", "18090"))
    print(f"C4_BLOCKCHAIN_STUB_READY\t{port}", flush=True)
    HTTPServer(("127.0.0.1", port), Handler).serve_forever()
