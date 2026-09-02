const { MODELS, DEFAULT_MODEL_ID, MAX_IMAGE_BYTES, VIDEO_DISABLED_REASON, getModel } = require('../lib/vercel-shared');

module.exports = (req, res) => {
  // Sengaja HANYA mengekspos "Roum AI Pro" ke frontend — model asli di balik
  // layar tetap lengkap di lib/vercel-shared.js dan tetap dipakai oleh
  // buildFallbackCandidates, cuma tidak ditampilkan ke user.
  res.status(200).json({
    models: [getModel(DEFAULT_MODEL_ID)],
    default: DEFAULT_MODEL_ID,
    maxImageBytes: MAX_IMAGE_BYTES,
    maxVideoBytes: 0,
    videoDisabledReason: VIDEO_DISABLED_REASON,
  });
};
