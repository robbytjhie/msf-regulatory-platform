#!/usr/bin/env python3
"""
Simple dev server that serves static files and auto-appends .html
for extensionless URLs (e.g. /pages/login -> /pages/login.html)
Run: python serve_frontend.py [port]
"""
import sys
import os
from http.server import HTTPServer, SimpleHTTPRequestHandler
from pathlib import Path

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 3000

class HtmlFallbackHandler(SimpleHTTPRequestHandler):
    def do_GET(self):
        # Strip query string for path resolution
        path = self.path.split('?')[0].split('#')[0]

        # If path has no extension and doesn't end with /, try appending .html
        if '.' not in Path(path).name and not path.endswith('/'):
            candidate = path + '.html'
            full_path = self.translate_path(candidate)
            if os.path.isfile(full_path):
                # Redirect to the .html version so URL bar shows it correctly
                self.send_response(301)
                self.send_header('Location', candidate + ('?' + self.path.split('?',1)[1] if '?' in self.path else ''))
                self.end_headers()
                return

        super().do_GET()

    def log_message(self, format, *args):
        # Suppress favicon and devtools noise
        if '/favicon.ico' in args[0] or '.well-known' in args[0]:
            return
        super().log_message(format, *args)

if __name__ == '__main__':
    os.chdir(os.path.dirname(os.path.abspath(__file__)))
    print(f'Serving frontend at http://localhost:{PORT}')
    print(f'Login page: http://localhost:{PORT}/pages/login.html')
    print('Press Ctrl+C to stop.')
    HTTPServer(('', PORT), HtmlFallbackHandler).serve_forever()
