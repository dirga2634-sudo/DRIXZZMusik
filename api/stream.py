from http.server import BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs
import json, yt_dlp

class handler(BaseHTTPRequestHandler):
    def do_GET(self):
        params = parse_qs(urlparse(self.path).query)
        vid = params.get('id', [''])[0]
        if not vid:
            self._ok({'error': 'Missing id'}); return
        try:
            opts = {
                'format': 'bestaudio[ext=m4a]/bestaudio[ext=webm]/bestaudio',
                'quiet': True,
                'no_warnings': True,
                'noplaylist': True,
            }
            with yt_dlp.YoutubeDL(opts) as ydl:
                info = ydl.extract_info(
                    f'https://www.youtube.com/watch?v={vid}',
                    download=False
                )
            url = info.get('url', '')
            if not url and info.get('requested_formats'):
                url = info['requested_formats'][0].get('url', '')
            self._ok({
                'url':       url,
                'title':     info.get('title', ''),
                'artist':    info.get('uploader', ''),
                'duration':  info.get('duration', 0),
                'thumbnail': info.get('thumbnail', ''),
            })
        except Exception as e:
            self._ok({'error': str(e), 'url': ''})

    def _ok(self, data):
        body = json.dumps(data, ensure_ascii=False).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Access-Control-Allow-Origin', '*')
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args): pass
