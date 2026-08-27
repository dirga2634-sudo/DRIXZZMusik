const { MODELS, DEFAULT_MODEL_ID, MAX_IMAGE_BYTES, VIDEO_DISABLED_REASON } = require('../lib/vercel-shared');

module.exports = (req, res) => {
  res.status(200).json({
    models: MODELS,
    default: DEFAULT_MODEL_ID,
    maxImageBytes: MAX_IMAGE_BYTES,
    maxVideoBytes: 0,
    videoDisabledReason: VIDEO_DISABLED_REASON,
  });
};
