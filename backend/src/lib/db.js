/**
 * Database abstraction — implementasi default pakai file JSON lokal (supaya
 * jalan tanpa PostgreSQL). Method-nya sengaja meniru bentuk query DB sungguhan
 * supaya gampang diganti ke Postgres asli nanti (lihat README "Ganti ke Postgres").
 */
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const DB_PATH = path.join(__dirname, '..', '..', 'data', 'db.json');
let writeQueue = Promise.resolve(); // hindari korup file kalau ada write bersamaan

function emptyDb() {
  return {
    users: [], projects: [], videos: [], clips: [], transcripts: [],
    captions: [], templates: [], brandKits: [], processingJobs: [], exports: [],
  };
}

function readDb() {
  try {
    return JSON.parse(fs.readFileSync(DB_PATH, 'utf8'));
  } catch (_) {
    return emptyDb();
  }
}

function writeDbSync(data) {
  fs.mkdirSync(path.dirname(DB_PATH), { recursive: true });
  const tmp = DB_PATH + '.tmp';
  fs.writeFileSync(tmp, JSON.stringify(data, null, 2));
  fs.renameSync(tmp, DB_PATH);
}

/** Semua mutasi dijalankan lewat sini secara berurutan (dijadikan antrian) supaya aman dari race condition. */
function mutate(fn) {
  writeQueue = writeQueue.then(async () => {
    const db = readDb();
    const result = await fn(db);
    writeDbSync(db);
    return result;
  });
  return writeQueue;
}

function id(prefix) {
  return `${prefix}_${crypto.randomBytes(8).toString('hex')}`;
}

function now() {
  return new Date().toISOString();
}

// ---- Helper query generik ----
function findById(collection, itemId) {
  return readDb()[collection].find((x) => x.id === itemId) || null;
}
function findAllBy(collection, predicate) {
  return readDb()[collection].filter(predicate);
}
function insert(collection, item) {
  return mutate((db) => { db[collection].push(item); return item; });
}
function update(collection, itemId, patch) {
  return mutate((db) => {
    const idx = db[collection].findIndex((x) => x.id === itemId);
    if (idx === -1) return null;
    db[collection][idx] = { ...db[collection][idx], ...patch, updatedAt: now() };
    return db[collection][idx];
  });
}
function remove(collection, itemId) {
  return mutate((db) => {
    const before = db[collection].length;
    db[collection] = db[collection].filter((x) => x.id !== itemId);
    return db[collection].length < before;
  });
}

module.exports = { readDb, writeDbSync, mutate, id, now, findById, findAllBy, insert, update, remove, DB_PATH, emptyDb };
