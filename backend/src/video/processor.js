/**
 * Video processor — pembungkus FFmpeg/FFprobe asli lewat child_process.
 * Sengaja tanpa dependency npm (fluent-ffmpeg dll) supaya bisa jalan di mana
 * saja yang punya binary ffmpeg/ffprobe terpasang, tanpa install tambahan.
 */
const { spawn } = require('child_process');
const path = require('path');
const fs = require('fs');

function run(cmd, args) {
  return new Promise((resolve, reject) => {
    const proc = spawn(cmd, args);
    let stdout = '';
    let stderr = '';
    proc.stdout.on('data', (d) => { stdout += d.toString(); });
    proc.stderr.on('data', (d) => { stderr += d.toString(); });
    proc.on('error', (err) => reject(new Error(`Gagal menjalankan ${cmd}: ${err.message}. Pastikan ffmpeg/ffprobe terpasang & ada di PATH.`)));
    proc.on('close', (code) => {
      if (code === 0) resolve({ stdout, stderr });
      else reject(new Error(`${cmd} keluar dengan kode ${code}: ${(stderr || stdout).slice(-1500)}`));
    });
  });
}

async function ensureDir(filePath) {
  await fs.promises.mkdir(path.dirname(filePath), { recursive: true });
}

async function getMetadata(videoPath) {
  const { stdout } = await run('ffprobe', [
    '-v', 'error',
    '-select_streams', 'v:0',
    '-show_entries', 'format=duration:stream=width,height,r_frame_rate',
    '-of', 'json',
    videoPath,
  ]);
  const json = JSON.parse(stdout);
  const stream = (json.streams && json.streams[0]) || {};
  const [num, den] = (stream.r_frame_rate || '0/1').split('/').map(Number);
  return {
    duration: parseFloat(json.format && json.format.duration) || 0,
    width: stream.width || 0,
    height: stream.height || 0,
    fps: den ? Math.round((num / den) * 100) / 100 : 0,
  };
}

async function extractThumbnail(videoPath, timeSeconds, outputPath) {
  await ensureDir(outputPath);
  await run('ffmpeg', ['-y', '-ss', String(Math.max(0, timeSeconds)), '-i', videoPath, '-frames:v', '1', '-q:v', '3', outputPath]);
  return outputPath;
}

async function detectScenes(videoPath, threshold = 0.28) {
  let stderr = '';
  try {
    const result = await run('ffmpeg', [
      '-i', videoPath,
      '-filter:v', `select='gt(scene,${threshold})',showinfo`,
      '-f', 'null', '-',
    ]);
    stderr = result.stderr;
  } catch (err) {
    stderr = err.message;
  }
  const matches = [...stderr.matchAll(/pts_time:([\d.]+)/g)];
  return matches.map((m) => parseFloat(m[1]));
}

async function detectSilence(videoPath, noiseDb = -30, minDurationSec = 0.5) {
  let stderr = '';
  try {
    const result = await run('ffmpeg', ['-i', videoPath, '-af', `silencedetect=noise=${noiseDb}dB:d=${minDurationSec}`, '-f', 'null', '-']);
    stderr = result.stderr;
  } catch (err) {
    stderr = err.message;
  }
  const starts = [...stderr.matchAll(/silence_start:\s*([\d.]+)/g)].map((m) => parseFloat(m[1]));
  const ends = [...stderr.matchAll(/silence_end:\s*([\d.]+)/g)].map((m) => parseFloat(m[1]));
  return starts.map((start, i) => ({ start, end: ends[i] ?? start }));
}

async function cutClip(videoPath, startSeconds, endSeconds, outputPath) {
  await ensureDir(outputPath);
  const duration = Math.max(0.1, endSeconds - startSeconds);
  try {
    await run('ffmpeg', ['-y', '-ss', String(startSeconds), '-i', videoPath, '-t', String(duration), '-c', 'copy', '-avoid_negative_ts', 'make_zero', outputPath]);
  } catch (_) {
    await run('ffmpeg', ['-y', '-ss', String(startSeconds), '-i', videoPath, '-t', String(duration), '-c:v', 'libx264', '-preset', 'veryfast', '-c:a', 'aac', outputPath]);
  }
  return outputPath;
}

async function reframeToVertical(videoPath, outputPath, targetWidth = 1080, targetHeight = 1920) {
  await ensureDir(outputPath);
  const vf = `crop='min(iw,ih*9/16)':'min(ih,iw*16/9)',scale=${targetWidth}:${targetHeight}`;
  await run('ffmpeg', ['-y', '-i', videoPath, '-vf', vf, '-c:a', 'copy', outputPath]);
  return outputPath;
}

async function reframeToSquare(videoPath, outputPath, size = 1080) {
  await ensureDir(outputPath);
  const vf = `crop='min(iw,ih)':'min(iw,ih)',scale=${size}:${size}`;
  await run('ffmpeg', ['-y', '-i', videoPath, '-vf', vf, '-c:a', 'copy', outputPath]);
  return outputPath;
}

async function extractAudio(videoPath, outputPath) {
  await ensureDir(outputPath);
  await run('ffmpeg', ['-y', '-i', videoPath, '-vn', '-acodec', 'libmp3lame', '-q:a', '4', outputPath]);
  return outputPath;
}

async function burnSubtitles(videoPath, srtPath, outputPath) {
  await ensureDir(outputPath);
  const escapedSrt = srtPath.replace(/:/g, '\\:');
  await run('ffmpeg', ['-y', '-i', videoPath, '-vf', `subtitles='${escapedSrt}'`, '-c:a', 'copy', outputPath]);
  return outputPath;
}

async function changeSpeed(videoPath, factor, outputPath) {
  await ensureDir(outputPath);
  const atempo = Math.max(0.5, Math.min(2.0, factor));
  await run('ffmpeg', ['-y', '-i', videoPath, '-filter_complex', `[0:v]setpts=${1 / factor}*PTS[v];[0:a]atempo=${atempo}[a]`, '-map', '[v]', '-map', '[a]', outputPath]);
  return outputPath;
}

async function adjustVolume(videoPath, multiplier, outputPath) {
  await ensureDir(outputPath);
  await run('ffmpeg', ['-y', '-i', videoPath, '-af', `volume=${multiplier}`, '-c:v', 'copy', outputPath]);
  return outputPath;
}

module.exports = {
  run,
  getMetadata,
  extractThumbnail,
  detectScenes,
  detectSilence,
  cutClip,
  reframeToVertical,
  reframeToSquare,
  extractAudio,
  burnSubtitles,
  changeSpeed,
  adjustVolume,
};
