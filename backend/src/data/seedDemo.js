/**
 * Data demo — dipakai saat DEMO_MODE=true atau untuk akun baru, supaya produk
 * langsung terasa "hidup" walau belum upload video / belum setel AI key sendiri.
 */
const { id, now } = require('../lib/db');

function buildDemoData(userId) {
  const t = now();
  const projects = [
    {
      id: 'demo_proj_podcast', userId, name: 'Podcast Ep. 42 — Growth Marketing',
      sourceType: 'upload', durationSec: 2415, status: 'ready',
      thumbnail: '/assets/demo/podcast-thumb.svg', createdAt: t, updatedAt: t,
    },
    {
      id: 'demo_proj_gaming', userId, name: 'Ranked Valorant — Clutch Round',
      sourceType: 'url', durationSec: 1830, status: 'ready',
      thumbnail: '/assets/demo/gaming-thumb.svg', createdAt: t, updatedAt: t,
    },
    {
      id: 'demo_proj_tutorial', userId, name: 'Cara Setup Home Studio Murah',
      sourceType: 'upload', durationSec: 905, status: 'processing',
      thumbnail: '/assets/demo/tutorial-thumb.svg', createdAt: t, updatedAt: t,
    },
  ];

  const clips = [
    { id: 'demo_clip_1', projectId: 'demo_proj_podcast', title: '"Ini yang bikin 90% startup gagal scaling"', duration: 42, viralScore: 94, category: 'best', transcriptPreview: '...jadi kesalahan paling umum itu scaling sebelum product-market fit beneran ketemu, orang kira udah PMF padahal cuma...', thumbnail: '/assets/demo/clip1.svg', aspectRatio: '9:16' },
    { id: 'demo_clip_2', projectId: 'demo_proj_podcast', title: 'Rahasia CAC turun 60% dalam 3 bulan', duration: 58, viralScore: 88, category: 'educational', transcriptPreview: '...kita ubah funnel-nya total, dari yang tadinya cold outbound jadi content-led, hasilnya CAC kita...', thumbnail: '/assets/demo/clip2.svg', aspectRatio: '9:16' },
    { id: 'demo_clip_3', projectId: 'demo_proj_podcast', title: 'Momen host ketawa ngakak soal investor', duration: 24, viralScore: 79, category: 'funny', transcriptPreview: '...dia bilang gini ke investor, "kalau lo gak percaya sama gue, ya udah gak usah invest" — dan itu literally...', thumbnail: '/assets/demo/clip3.svg', aspectRatio: '1:1' },
    { id: 'demo_clip_4', projectId: 'demo_proj_gaming', title: 'CLUTCH 1v4 di round terakhir!!', duration: 35, viralScore: 97, category: 'trending', transcriptPreview: '[teriakan] GAK NYANGKA GUA — итог round paling gila musim ini, full clutch tanpa util...', thumbnail: '/assets/demo/clip4.svg', aspectRatio: '9:16' },
    { id: 'demo_clip_5', projectId: 'demo_proj_gaming', title: 'Reaksi kocak pas ketipu fake plant', duration: 19, viralScore: 82, category: 'funny', transcriptPreview: '...gua kira beneran ada orang di sana, taunya cuma plant doang, anjay ketipu mentah-mentah...', thumbnail: '/assets/demo/clip5.svg', aspectRatio: '9:16' },
  ];

  const transcripts = [
    { id: 'demo_tr_1', projectId: 'demo_proj_podcast', language: 'id', segments: [
      { start: 0, end: 4.2, text: 'Oke jadi hari ini kita bahas topik yang menurut gua paling underrated.' },
      { start: 4.2, end: 9.8, text: 'Kesalahan paling umum itu scaling sebelum product-market fit beneran ketemu.' },
      { start: 9.8, end: 15.1, text: 'Orang kira udah PMF padahal cuma kebetulan viral doang seminggu.' },
    ]},
  ];

  const templates = [
    { id: 'tpl_podcast', name: 'Podcast', description: 'Layout waveform + judul besar, cocok buat clip ngobrol.', preview: '/assets/demo/tpl-podcast.svg', category: 'podcast' },
    { id: 'tpl_gaming', name: 'Gaming', description: 'Caption bold + efek kill-feed, cocok buat highlight game.', preview: '/assets/demo/tpl-gaming.svg', category: 'gaming' },
    { id: 'tpl_education', name: 'Education', description: 'Caption jelas + poin-poin, cocok buat konten edukasi.', preview: '/assets/demo/tpl-education.svg', category: 'education' },
    { id: 'tpl_news', name: 'News', description: 'Lower-third ala berita, cocok buat rangkuman berita.', preview: '/assets/demo/tpl-news.svg', category: 'news' },
    { id: 'tpl_story', name: 'Storytelling', description: 'Karaoke caption pelan, cocok buat cerita personal.', preview: '/assets/demo/tpl-story.svg', category: 'storytelling' },
    { id: 'tpl_reaction', name: 'Reaction', description: 'Webcam bubble + caption besar, cocok buat konten reaksi.', preview: '/assets/demo/tpl-reaction.svg', category: 'reaction' },
  ];

  const brandKit = {
    id: 'demo_brand_1', userId, name: 'Brand Kit Utama',
    logo: null, colors: ['#7C5CFF', '#22D3EE', '#0B0B12'],
    fontHeading: 'Space Grotesk', fontBody: 'Inter',
    captionStyle: 'bold', watermark: null, createdAt: t, updatedAt: t,
  };

  const analytics = {
    totalViews: 284500, clipsCreated: 47, avgClipLength: 36,
    exportCount: 31, bestPerforming: 'demo_clip_4',
    viewsByDay: [12000, 15400, 9800, 22100, 31000, 28700, 41200],
  };

  return { projects, clips, transcripts, templates, brandKit, analytics };
}

module.exports = { buildDemoData };
