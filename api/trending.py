from http.server import BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs
import json, urllib.request

YTKEY = 'AIzaSyA2PIJSWMqWZMmPBaVyV42HZWNE05e1ZIQ'

class handler(BaseHTTPRequestHandler):

    def do_GET(self):
        try:
            url = (f'https://www.googleapis.com/youtube/v3/videos'
                   f'?part=snippet,contentDetails'
                   f'&chart=mostPopular&videoCategoryId=10'
                   f'&maxResults=30&key={YTKEY}')
            with urllib.request.urlopen(url, timeout=10) as r:
                data = json.loads(r.read())
            songs = []
            for item in data.get('items', []):
                s  = item.get('snippet', {})
                cd = item.get('contentDetails', {})
                t  = s.get('thumbnails', {})
                th = t.get('medium', t.get('default', {})).get('url', '')
                songs.append({
                    'id':       item['id'],
                    'title':    s.get('title', ''),
                    'artist':   s.get('channelTitle', ''),
                    'thumbnail': th,
                    'duration': _iso(cd.get('duration', '')),
                })
            self._json(200, {'songs': songs})
        except Exception as e:
            self._json(500, {'error': str(e)})

    def _json(self, code, data):
        body = json.dumps(data).encode()
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Access-Control-Allow-Origin', '*')
        self.end_headers()
        self.wfile.write(body)

def _iso(s):
    if not s: return ''
    try:
        h=m=sec=0; t=s.replace('PT','')
        if 'H' in t: h,t = int(t[:t.index('H')]), t[t.index('H')+1:]
        if 'M' in t: m,t = int(t[:t.index('M')]), t[t.index('M')+1:]
        if 'S' in t: sec = int(t.replace('S',''))
        return f'{h}:{m:02d}:{sec:02d}' if h else f'{m}:{sec:02d}'
    except: return ''
