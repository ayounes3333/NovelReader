package my.noveldokusha.ui.browse.extractor.utils

import kotlin.Throws
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import android.util.Xml

object XmlParser {
    @Throws(XmlPullParserException::class)
    fun buildPullParser(): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        factory.isValidating = false
        factory.setFeature(Xml.FEATURE_RELAXED, true)
        // factory.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false);
        // factory.setFeature(XmlPullParser.FEATURE_VALIDATION, false);
        return factory.newPullParser()
    }
}