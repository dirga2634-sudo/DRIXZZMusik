module.exports = async (req, res) => {
  res.status(200).json({ stage: 'done', projectStatus: 'ready' });
};
