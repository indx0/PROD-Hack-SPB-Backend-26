#!/usr/bin/env python3
"""Query dashboard expressions against a local integration-test metrics snapshot.

Requires Docker and `./mvnw -Dit.test=ObservabilityIntegrationTest verify` first.
Starts temporary local-only servers; stops its own container on exit. No Sage access.
"""
import http.server
import json
import os
from pathlib import Path
import socket
import subprocess
import tempfile
import threading
import time
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
SNAPSHOT = ROOT / 'target/observability/scrape.prom'
IMAGE = os.environ.get('PROMETHEUS_VALIDATION_IMAGE', 'prom/prometheus:latest')


def main():
    data = SNAPSHOT.read_bytes()

    class Handler(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            self.send_response(200)
            self.send_header('Content-Type', 'text/plain; version=0.0.4')
            self.end_headers()
            self.wfile.write(data)

        def log_message(self, *args):
            pass

    server = http.server.HTTPServer(('127.0.0.1', 0), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', 0))
        port = listener.getsockname()[1]
    container = None
    try:
        with tempfile.TemporaryDirectory(prefix='movermate-metrics-') as directory:
            config = Path(directory) / 'prometheus.yml'
            config.write_text(f'''global:
  scrape_interval: 1s
scrape_configs:
  - job_name: movermate
    honor_labels: true
    static_configs:
      - targets: ["127.0.0.1:{server.server_port}"]
        labels:
          environment: prod
''')
            container = subprocess.check_output([
                'docker', 'run', '--rm', '-d', '--network', 'host',
                '-v', f'{config}:/etc/prometheus/prometheus.yml:ro,Z', IMAGE,
                '--config.file=/etc/prometheus/prometheus.yml',
                f'--web.listen-address=127.0.0.1:{port}', '--storage.tsdb.path=/prometheus'
            ], text=True).strip()
            base = f'http://127.0.0.1:{port}'

            def query(expression):
                url = base + '/api/v1/query?' + urllib.parse.urlencode({'query': expression})
                with urllib.request.urlopen(url, timeout=3) as response:
                    result = json.load(response)
                assert result['status'] == 'success', result
                return result['data']['result']

            for attempt in range(30):
                try:
                    if query('jvm_memory_used_bytes{application="movermate",environment="prod"}'):
                        break
                except OSError:
                    pass
                time.sleep(0.5)
            else:
                raise AssertionError('Snapshot was not scraped')
            time.sleep(2)  # A second sample is required by rate().
            dashboard = json.loads((ROOT / 'deploy/observability/grafana-dashboard.json').read_text())
            for panel in dashboard['panels']:
                for target in panel['targets']:
                    result = query(target['expr'].replace('$environment', 'prod'))
                    assert result, f"No series for {panel['title']}"
                print('OK:', panel['title'])
            for name in ('http_server_requests_seconds_bucket', 'movermate_llm_operation_seconds_bucket',
                         'hikaricp_connections_active', 'movermate_llm_active'):
                assert query(name), f'Metric missing: {name}'
            print('All dashboard queries and required metric families verified locally.')
    finally:
        if container:
            subprocess.run(['docker', 'stop', container], check=True, stdout=subprocess.DEVNULL)
        server.shutdown()
        server.server_close()


if __name__ == '__main__':
    main()
