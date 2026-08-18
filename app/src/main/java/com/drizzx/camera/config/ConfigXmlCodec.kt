package com.drizzx.camera.config

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlSerializer
import java.io.StringReader
import java.io.StringWriter

/**
 * Turns [CameraConfig] into the XML "plugin" file Dirga asked for, and back.
 * Anyone can hand-edit the exported file (add a `<Filter>` line, tweak a
 * number) and re-import it - no code changes needed, same spirit as sharing
 * a GCam config.
 */
object ConfigXmlCodec {

    fun encode(config: CameraConfig): String {
        val writer = StringWriter()
        val serializer = Xml.newSerializer()
        serializer.setOutput(writer)
        serializer.startDocument("utf-8", true)
        serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)

        serializer.startTag(null, "DrizzxCamConfig")
        serializer.attribute(null, "version", config.version.toString())

        serializer.startTag(null, "Pro")
        writeControl(serializer, "Iso", config.pro.isoAuto, config.pro.isoValue.toString())
        writeControl(serializer, "ShutterSpeedNs", config.pro.shutterAuto, config.pro.shutterSpeedNs.toString())
        serializer.startTag(null, "WhiteBalance")
        serializer.attribute(null, "preset", config.pro.whiteBalancePreset)
        serializer.endTag(null, "WhiteBalance")
        writeControl(serializer, "FocusDistanceDiopters", config.pro.focusAuto, config.pro.focusDistanceDiopters.toString())
        serializer.startTag(null, "ExposureCompensationIndex")
        serializer.text(config.pro.exposureCompensationIndex.toString())
        serializer.endTag(null, "ExposureCompensationIndex")
        serializer.endTag(null, "Pro")

        serializer.startTag(null, "ImageProcessing")
        serializer.attribute(null, "jpegQuality", config.imageProcessing.jpegQuality.toString())
        serializer.endTag(null, "ImageProcessing")

        serializer.startTag(null, "Filters")
        for (filter in config.filters) {
            serializer.startTag(null, "Filter")
            serializer.attribute(null, "name", filter.name)
            serializer.attribute(null, "saturation", filter.saturation.toString())
            serializer.attribute(null, "contrast", filter.contrast.toString())
            serializer.attribute(null, "warmth", filter.warmth.toString())
            serializer.endTag(null, "Filter")
        }
        serializer.endTag(null, "Filters")

        serializer.endTag(null, "DrizzxCamConfig")
        serializer.endDocument()
        return writer.toString()
    }

    private fun writeControl(serializer: XmlSerializer, tag: String, auto: Boolean, value: String) {
        serializer.startTag(null, tag)
        serializer.attribute(null, "auto", auto.toString())
        serializer.attribute(null, "value", value)
        serializer.endTag(null, tag)
    }

    /** @throws Exception if the XML is malformed - callers decide how to surface that. */
    fun decode(xml: String): CameraConfig {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        var version = CameraConfig.CURRENT_VERSION
        var isoAuto = true
        var isoValue = 100
        var shutterAuto = true
        var shutterSpeedNs = 16_666_666L
        var whiteBalancePreset = "auto"
        var focusAuto = true
        var focusDist = 0f
        var exposureCompensationIndex = 0
        var jpegQuality = 95
        var readingExposureText = false
        val filters = mutableListOf<FilterPreset>()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "DrizzxCamConfig" -> {
                            version = parser.getAttributeValue(null, "version")?.toIntOrNull()
                                ?: CameraConfig.CURRENT_VERSION
                        }
                        "Iso" -> {
                            isoAuto = parser.getAttributeValue(null, "auto")?.toBooleanStrictOrNull() ?: true
                            isoValue = parser.getAttributeValue(null, "value")?.toIntOrNull() ?: isoValue
                        }
                        "ShutterSpeedNs" -> {
                            shutterAuto = parser.getAttributeValue(null, "auto")?.toBooleanStrictOrNull() ?: true
                            shutterSpeedNs = parser.getAttributeValue(null, "value")?.toLongOrNull() ?: shutterSpeedNs
                        }
                        "WhiteBalance" -> {
                            whiteBalancePreset = parser.getAttributeValue(null, "preset") ?: whiteBalancePreset
                        }
                        "FocusDistanceDiopters" -> {
                            focusAuto = parser.getAttributeValue(null, "auto")?.toBooleanStrictOrNull() ?: true
                            focusDist = parser.getAttributeValue(null, "value")?.toFloatOrNull() ?: focusDist
                        }
                        "ExposureCompensationIndex" -> readingExposureText = true
                        "ImageProcessing" -> {
                            jpegQuality = parser.getAttributeValue(null, "jpegQuality")?.toIntOrNull() ?: jpegQuality
                        }
                        "Filter" -> {
                            val name = parser.getAttributeValue(null, "name")
                            if (!name.isNullOrBlank()) {
                                val sat = parser.getAttributeValue(null, "saturation")?.toFloatOrNull() ?: 1f
                                val con = parser.getAttributeValue(null, "contrast")?.toFloatOrNull() ?: 1f
                                val warm = parser.getAttributeValue(null, "warmth")?.toFloatOrNull() ?: 0f
                                filters.add(FilterPreset(name, sat, con, warm))
                            }
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (readingExposureText) {
                        parser.text?.trim()?.toIntOrNull()?.let { exposureCompensationIndex = it }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "ExposureCompensationIndex") readingExposureText = false
                }
            }
            eventType = parser.next()
        }

        return CameraConfig(
            version = version,
            pro = ProDefaults(
                isoAuto = isoAuto,
                isoValue = isoValue,
                shutterAuto = shutterAuto,
                shutterSpeedNs = shutterSpeedNs,
                whiteBalancePreset = whiteBalancePreset,
                exposureCompensationIndex = exposureCompensationIndex,
                focusAuto = focusAuto,
                focusDistanceDiopters = focusDist
            ),
            imageProcessing = ImageProcessingSettings(jpegQuality = jpegQuality),
            filters = filters.ifEmpty { FilterPreset.builtIns() }
        )
    }
}
