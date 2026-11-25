package com.jarvis.assistant.router

import java.text.Normalizer

/**
 * Turns what the speech recognizer heard (English, Tanglish in Roman letters, or Tamil Unicode) into one plain,
 * lower-case Roman form the command parser can match. Pure logic, no Android classes.
 */
object Lexicon {
    private const val TAMIL_DIGITS = "௦௧௨௩௪௫௬௭௮௯"
    private val AM_PM = Regex("""\b([ap])\.\s?m\b\.?""")
    private val PUNCTUATION = Regex("""[^\p{L}\p{M}\p{N}:+\s]""")
    private val LEADING_PHRASES = listOf("can you", "could you", "would you", "will you", "can u", "hey buddy", "ok buddy", "okay buddy", "hi buddy", "hey jarvis", "ok jarvis", "okay jarvis", "hi jarvis")
    private val LEADING = setOf("hey", "hi", "ok", "okay", "buddy", "jarvis", "please", "plz", "pls", "kindly", "dayavuseidhu", "thayavusenju", "konjam", "enakku", "en", "oru", "chinna")
    private val TRAILING = setOf("please", "plz", "pls", "pa", "ppa", "ma", "da", "nga", "nu", "la", "ku", "irukku", "koodiya", "podhum", "now", "ippo", "seekiram", "udane")

    private val WORDS: Map<String, String> = mapOf(
        // Tamil verbs and helpers to normalized English/Tanglish keys
        "திற" to "open", "திறக்கவும்" to "open", "திறக்க" to "open", "திறந்துவிடு" to "open",
        "மூடு" to "close", "மூடவும்" to "close", "நிறுத்து" to "stop", "நிறுத்தவும்" to "stop",
        "ஆன்" to "on", "ஆஃப்" to "off", "ஆப்" to "off",
        "பண்ணு" to "pannu", "பண்ணவும்" to "pannu", "பண்ணுங்க" to "pannu", "செய்" to "pannu", "செய்யவும்" to "pannu",
        "போடு" to "podu", "போடவும்" to "podu", "போடுங்க" to "podu", "இயக்கு" to "podu",
        "போ" to "po", "போங்க" to "ponga", "செல்" to "po",
        "கூட்டு" to "up", "ஏற்று" to "up", "அதிகமாக்கு" to "up", "அதிகப்படுத்து" to "up", "அதிகம்" to "up",
        "குறை" to "down", "குறைக்கவும்" to "down", "கம்மி" to "down", "கம்மியாக்கு" to "down",
        "காட்டு" to "show", "காட்டவும்" to "show", "எடு" to "take", "எடுக்கவும்" to "take",
        "பூட்டு" to "lock", "லாக்" to "lock", "அணை" to "off",
        "அனுப்பு" to "send", "அனுப்பவும்" to "send", "அனுப்புங்க" to "send",
        "தேடு" to "search", "தேடவும்" to "search", "சொல்" to "tell", "சொல்லு" to "tell", "சொல்லுங்க" to "tell",
        "கேள்" to "listen", "கேளு" to "listen", "கேளுங்க" to "listen",
        "வாபஸ்" to "back", "பின்னால்" to "back", "பின்னே" to "back", "ஹோம்" to "home", "முகப்பு" to "home",
        "மேலே" to "up", "கீழே" to "down",
        "ஸ்க்ரோல்" to "scroll", "உருட்டு" to "scroll", "உருட்டவும்" to "scroll", "ஸ்கிரீன்ஷாட்" to "screenshot",
        "கால்" to "call", "டயல்" to "dial", "போன்" to "phone",
        "வை" to "vai", "வைக்கவும்" to "vai", "மாற்று" to "set", "செட்" to "set",
        "என்ன" to "enna", "எவ்வளவு" to "evlo", "எவ்ளோ" to "evlo", "எத்தனை" to "ethana", "இருக்கு" to "irukku",
        "இப்போ" to "ippo", "இப்போது" to "ippo", "இதை" to "idhai", "அதை" to "adhai",
        "நேரம்" to "time", "மணி" to "time", "தேதி" to "date", "நாள்" to "date",
        // Device words
        "சத்தம்" to "volume", "ஒலி" to "volume", "வால்யூம்" to "volume", "மியூட்" to "mute", "அன்மியூட்" to "unmute",
        "டார்ச்" to "torch", "பிளாஷ்லைட்" to "flashlight", "ஃபிளாஷ்லைட்" to "flashlight", "விளக்கு" to "flashlight",
        "பாட்டு" to "music", "பாடல்" to "music", "இசை" to "music", "மியூசிக்" to "music", "அடுத்த" to "next", "முந்தைய" to "previous",
        "டைமர்" to "timer", "அலாரம்" to "alarm", "நிமிடம்" to "minute", "நிமிஷம்" to "minute", "மணி" to "hour", "வினாடி" to "second",
        "காலை" to "morning", "மாலை" to "evening", "இரவு" to "night", "மதியம்" to "afternoon",
        "பேட்டரி" to "battery", "சார்ஜ்" to "battery",
        "வைஃபை" to "wifi", "வை-ஃபை" to "wifi", "புளூடூத்" to "bluetooth",
        "செய்தி" to "message", "மெசேஜ்" to "message",
        "கோப்பு" to "file", "ஃபைல்" to "file",
        // Popular Apps
        "யூடியூப்" to "youtube", "வாட்ஸ்அப்" to "whatsapp", "வாட்ஸ்ஆப்" to "whatsapp", "வாட்ஸப்" to "whatsapp",
        "இன்ஸ்டாகிராம்" to "instagram", "ஃபேஸ்புக்" to "facebook", "குரோம்" to "chrome", "கேமரா" to "camera",
        "செட்டிங்ஸ்" to "settings", "அமைப்புகள்" to "settings", "கூகிள்" to "google", "மேப்ஸ்" to "maps",
        "ஜிமெயில்" to "gmail", "டெலிகிராம்" to "telegram", "ஸ்பாட்டிஃபை" to "spotify", "கால்குலேட்டர்" to "calculator",
        "கேலரி" to "gallery", "போட்டோ" to "photos", "போட்டோஸ்" to "photos",
        "நெட்பிளிக்ஸ்" to "netflix", "அமேசான்" to "amazon",
    )

    /** Lower-case, no punctuation, Tamil digits and Unicode converted, single spaces. */
    fun normalize(raw: String): String {
        var t = Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFC)
        t = buildString(t.length) {
            for (c in t) {
                val d = TAMIL_DIGITS.indexOf(c)
                append(if (d >= 0) '0' + d else c)
            }
        }
        t = AM_PM.replace(t) { "${it.groupValues[1]}m" }
        t = PUNCTUATION.replace(t, " ")
        return t.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ") { WORDS[it] ?: it }
    }

    /** [normalize] plus politeness words dropped from the start and end ("hey buddy, can you open youtube please"). */
    fun clean(raw: String): String {
        val words = normalize(raw).split(' ').filter { it.isNotEmpty() }.toMutableList()
        var changed = true
        while (changed && words.isNotEmpty()) {
            changed = false
            val head2 = words.take(2).joinToString(" ")
            if (head2 in LEADING_PHRASES) {
                repeat(2) { words.removeAt(0) }
                changed = true
            } else if (words.first() in LEADING) {
                words.removeAt(0)
                changed = true
            }
        }
        while (words.isNotEmpty() && words.last() in TRAILING) words.removeAt(words.lastIndex)
        return words.joinToString(" ")
    }

    /** True if the text contains Tamil letters. */
    fun hasTamil(text: String) = text.any { it in '\u0B80'..'\u0BFF' }
}
