from http.server import BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs, quote
import json, urllib.request

YTKEY = 'AIzaSyA2PIJSWMqWZMmPBaVyV42HZWNE05e1ZIQ'

class handler(BaseHTTPRequestHandler):
    def do_GET(self):
        params = parse_qs(urlparse(self.path).query)
        q = params.get('q', [''])[0]
        if not q:
            self._ok({'songs': [], 'error': 'Missing q'}); return
        try:
            url = ('https://www.googleapis.com/youtube/v3/search'
                   f'?part=snippet&type=video&videoCategoryId=10'
                   f'&q={quote(q)}&maxResults=25&key={YTKEY}')
            with urllib.request.urlopen(url, timeout=15) as r:
                data = json.loads(r.read())
            songs = []
            for item in data.get('items', []):
                s = item.get('snippet', {})
                t = s.get('thumbnails', {})
                th = (t.get('medium') or t.get('default') or {}).get('url', '')
                songs.append({
                    'id':        item['id']['videoId'],
                    'title':     s.get('title', ''),
                    'artist':    s.get('channelTitle', ''),
                    'thumbnail': th,
                    'duration':  '',
                })
            self._ok({'songs': songs})
        except Exception as e:
            self._ok({'songs': [], 'error': str(e)})

    def _ok(self, data):
        body = json.dumps(data, ensure_ascii=False).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Access-Control-Allow-Origin', '*')
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args): pass
