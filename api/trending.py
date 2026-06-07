from http.server import BaseHTTPRequestHandler
import json, urllib.request

YTKEY = 'AIzaSyA2PIJSWMqWZMmPBaVyV42HZWNE05e1ZIQ'

class handler(BaseHTTPRequestHandler):
    def do_GET(self):
        try:
            url = ('https://www.googleapis.com/youtube/v3/videos'
                   '?part=snippet,contentDetails'
                   '&chart=mostPopular&videoCategoryId=10'
                   f'&maxResults=30&key={YTKEY}')
            with urllib.request.urlopen(url, timeout=15) as r:
                data = json.loads(r.read())
            songs = []
            for item in data.get('items', []):
                s = item.get('snippet', {})
                cd = item.get('contentDetails', {})
                t = s.get('thumbnails', {})
                th = (t.get('medium') or t.get('default') or {}).get('url', '')
                songs.append({
                    'id': item['id'],
                    'title': s.get('title', ''),
                    'artist': s.get('channelTitle', ''),
                    'thumbnail': th,
                    'duration': _iso(cd.get('duration', '')),
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

def _iso(s):
    if not s: return ''
    try:
        h=m=sec=0; t=s.replace('PT','')
        if 'H' in t: h,t=int(t[:t.index('H')]),t[t.index('H')+1:]
        if 'M' in t: m,t=int(t[:t.index('M')]),t[t.index('M')+1:]
        if 'S' in t: sec=int(t.replace('S',''))
        return f'{h}:{m:02d}:{sec:02d}' if h else f'{m}:{sec:02d}'
    except: return ''
