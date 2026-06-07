from http.server import BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs
import yt_dlp
import json

class handler(BaseHTTPRequestHandler):

    def do_GET(self):
        params = parse_qs(urlparse(self.path).query)
        video_id = params.get('id', [''])[0]

        if not video_id:
            self._json(400, {'error': 'Missing id'})
            return

        try:
            ydl_opts = {
                'format': 'bestaudio[ext=m4a]/bestaudio[ext=webm]/bestaudio',
                'quiet': True,
                'no_warnings': True,
                'extractor_args': {'youtube': {'skip': ['dash', 'hls']}},
            }

            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(
                    f'https://www.youtube.com/watch?v={video_id}',
                    download=False
                )

            # Cari URL stream
            stream_url = info.get('url', '')
            if not stream_url and info.get('requested_formats'):
                stream_url = info['requested_formats'][0].get('url', '')

            if not stream_url:
                self._json(500, {'error': 'No stream URL found'})
                return

            self._json(200, {
                'url':       stream_url,
                'title':     info.get('title', ''),
                'artist':    info.get('uploader', ''),
                'duration':  info.get('duration', 0),
                'thumbnail': info.get('thumbnail', ''),
            })

        except Exception as e:
            self._json(500, {'error': str(e)})

    def do_OPTIONS(self):
        self.send_response(200)
        self._cors()
        self.end_headers()

    def _json(self, code, data):
        body = json.dumps(data).encode()
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self._cors()
        self.end_headers()
        self.wfile.write(body)

    def _cors(self):
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', '*')
