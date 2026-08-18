package com.drizzx.camera.config

import android.content.Context
import java.io.File

/**
 * Owns the single active [CameraConfig]. The file picker dialogs for
 * import/export live in the UI layer (Storage Access Framework needs an
 * Activity result launcher) - this class only knows how to read/write the
 * config itself, from internal storage or from raw XML text.
 */
class ConfigRepository(context: Context) {

    private val configFile: File = File(context.filesDir, "drizzxcam_config.xml")

    fun load(): CameraConfig {
        return try {
            if (configFile.exists()) {
                ConfigXmlCodec.decode(configFile.readText())
            } else {
                CameraConfig().also(::save)
            }
        } catch (e: Exception) {
            CameraConfig()
        }
    }

    fun save(config: CameraConfig) {
        configFile.writeText(ConfigXmlCodec.encode(config))
    }

    fun exportXmlText(config: CameraConfig): String = ConfigXmlCodec.encode(config)

    /** @throws Exception if the text isn't valid config XML - let the caller show a message. */
    fun parseImportedXml(xml: String): CameraConfig = ConfigXmlCodec.decode(xml)
}
