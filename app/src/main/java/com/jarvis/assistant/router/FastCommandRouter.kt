package com.jarvis.assistant.router

import com.jarvis.assistant.ai.AiMode

/**
 * The fast path: recognises simple phone commands in English, Tanglish and Tamil script so they run in
 * milliseconds without a language model. Anything it does not recognise returns null and goes to the model as before.
 */
object FastCommandRouter {
    private const val MAX_WORDS = 12

    private val MODE = Regex("""^(?:go |switch to |switch |use |set |turn on )?(offline|online|auto|automatic)(?: mode)?(?: on| pannu)?$""")
    private val LANG = Regex("""^(?:please )?(?:(?:speak|listen|talk|understand|reply|pesu|kelu|sol)(?: to me)?(?: in| la)? )?(tamil|tanglish|english)(?: mode)?(?: la| on| pannu| pesu| kelu)*$""")

    private val BACK = Regex("""^(?:go |press |navigate |take me )?back(?: button| pannu| po| ponga)?$|^(?:pinnaadi|pinnadi|pinadi|back)(?: po| ponga| pannu)?$""")
    private val HOME = Regex("""^(?:go |press |take me |navigate |show )?(?:to )?(?:the )?home(?: screen| button| page)?(?: ku)?(?: po| ponga| pannu| kaattu)?$""")
    private val RECENTS = Regex("""^(?:open |show |go to )?(?:the )?(?:recent apps?|recents?|app switcher)(?: open pannu| thira| kaattu| pannu)?$""")
    private val NOTIFICATIONS = Regex("""^(?:open |show |pull down |check )?(?:the )?(?:my )?notifications?(?: panel| shade| bar| drawer)?(?: open pannu| thira| kaattu| pannu)?$""")
    private val QUICK_SETTINGS = Regex("""^(?:open |show |pull down )?(?:the )?quick settings?(?: panel)?(?: open pannu| thira| kaattu)?$""")
    private val SCREENSHOT = Regex("""^(?:take |capture |grab )?(?:a )?(?:screenshot|screen shot|screen capture)(?: edu| edunga| pannu| podu)?$""")
    private val LOCK = Regex("""^lock(?: the)?(?: phone| screen| device| mobile)?(?: pannu)?$|^(?:phone|screen) lock(?: pannu)?$""")

    private val SCROLL_OK = setOf("scroll", "swipe", "down", "up", "left", "right", "keezhe", "kizhe", "mele", "mela", "pannu", "the", "page", "screen", "a", "little", "bit", "konjam", "innum", "more", "ah")

    private val VOLUME_WORDS = setOf("volume", "satham", "sound", "sathatha", "oliyai", "oli")
    private val UP_WORDS = setOf("up", "increase", "raise", "louder", "higher", "aethu", "yaethu", "ethu", "kootu", "adhigam", "athigam", "full", "max", "maximum", "perusu")
    private val DOWN_WORDS = setOf("down", "decrease", "lower", "reduce", "quieter", "softer", "korai", "kurai", "kammi", "chinna", "low", "min", "minimum")

    private val TORCH_WORDS = setOf("flashlight", "torch", "flash", "tourch", "tarch", "flashlite", "light", "velakku", "vilakku")
    private val ON_WORDS = setOf("on", "enable", "podu", "start", "shuru", "activate")
    private val OFF_WORDS = setOf("off", "disable", "moodu", "niruthu", "stop", "shut", "deactivate", "anai")

    private val MUSIC_WORDS = setOf("music", "song", "songs", "paattu", "paatu", "paadal", "audio", "track", "tune")
    private val MEDIA_FILLER = setOf("the", "some", "my", "pannu", "oru", "ah", "ku", "la")
    private val PLAY_WORDS = setOf("play", "resume", "start", "podu", "paada", "vidu")
    private val PAUSE_WORDS = setOf("pause", "stop", "niruthu", "moodu", "off")
    private val NEXT_WORDS = setOf("next", "skip", "adutha", "nextu")
    private val PREV_WORDS = setOf("previous", "prev", "munthaiya", "muthal", "last")

    private val TIME = Regex("""^(?:what(?: s|s| is)? the time(?: now| right now)?|what time is it(?: now| right now)?|tell me the time|current time|time enna|mani enna|ippo mani enna|time sollu|mani sollu)$""")
    private val DATE = Regex("""^(?:what(?: s|s| is)? (?:the )?(?:date|day)(?: today| is it)?|what day is it(?: today)?|today s date|todays date|inniki enna date|inniki date enna|date enna|inniki enna naal|which day is it)$""")
    private val BATTERY = Regex("""^(?:what(?: s|s| is)? (?:the )?battery(?: level| percentage| percent)?|how much battery(?: is left| do i have| left)?|battery (?:level|percentage|percent|status)|battery evlo(?: irukku)?|battery level enna|en phone la battery evlo(?: irukku)?|battery charge evlo|charge evlo(?: irukku)?|battery ethana percent)$""")
    private val VOLUME_QUERY = Regex("""^(?:what(?: s|s| is)? (?:the )?(?:current )?volume(?: level)?|how loud is it|volume (?:status|level|check)|tell me the volume|volume evlo(?: irukku)?|satham evlo(?: irukku)?)$""")

    private val MEDIA_COUNT = Regex("""^(?:count|how many) (videos?|photos?|images?|pictures?)(?: do i have| are there)?$""")
    private val FILE_LIST = Regex("""^(?:list|show|get)(?: all)? (?:my )?files$""")
    private val FILE_FIND = Regex("""^(?:find|search|locate)(?: the)? file (.+)$""")
    private val FILE_READ = Regex("""^(?:read|open|show|cat)(?: the)? file (.+)$|^(?:indha )?file(?: ah)? (?:open|read|kaattu|thira)(?: pannu)?(?: (.+))?$""")
    private val FILE_CREATE = Regex("""^(?:create|write|make|new)(?: file)? (.+?) with (.+)$""")

    private val DURATION = Regex("""(\d+)\s*(seconds?|secs?|minutes?|mins?|nimisham|nimudam|hours?|hrs?)""")
    private val CLOCK = Regex("""\b(\d{1,2})(?::(\d{2}))?\s*(am|pm|mani)?(?=\s|$)""")
    private val DIAL_A = Regex("""^(?:call|dial|phone)\s+(\+?[\d\s]{6,18})(?: pannu)?$""")
    private val DIAL_B = Regex("""^(\+?[\d\s]{6,18})(?: ku)? (?:call|phone|dial)(?: pannu)?$""")

    private val CALL_A = Regex("""^(?:make a call to|place a call to|give a call to|call|phone|ring|dial) (.+)$""")
    private val CALL_B = Regex("""^(.+?) (?:ku )?(?:call|phone|dial)(?: pannu| podu)?$""")
    private val NOT_A_NAME = setOf("me", "him", "her", "them", "us", "back", "again", "it", "someone", "somebody", "anyone", "a", "the", "number", "now", "later")

    private val PAGES = linkedMapOf(
        "wifi" to "wifi", "wi fi" to "wifi", "bluetooth" to "bluetooth", "battery" to "battery", "display" to "display",
        "brightness" to "display", "sound" to "sound", "location" to "location", "gps" to "location", "airplane" to "airplane",
        "flight" to "airplane", "hotspot" to "hotspot", "mobile data" to "data", "data" to "data", "apps" to "apps",
        "storage" to "storage", "accessibility" to "accessibility", "developer" to "developer", "notification" to "notification",
        "date" to "date", "language" to "language",
    )
    private val TOGGLE_PAGES = setOf("wifi", "bluetooth", "airplane", "hotspot", "data", "location")
    private val TOGGLE_OK = setOf("turn", "switch", "on", "off", "enable", "disable", "the", "wifi", "wi", "fi", "bluetooth", "airplane", "flight", "mode", "hotspot", "mobile", "data", "location", "gps", "pannu", "podu", "moodu", "thira")

    private val RUNTIME_DOWNLOAD = Regex("""^(?:download|install|get|set up|setup) (?:the )?(?:offline|local|on device|ondevice) (?:model|ai|brain)$""")
    private val RUNTIME_STATUS = Regex("""^(?:is )?(?:the )?(?:offline|local|on device|ondevice) (?:model|ai|brain) (?:status|running|ready|working)$|^(?:offline|local|on device|ondevice) (?:model|ai|brain) status$""")

    private val CAMERA = Regex("""^(?:open |launch |start |thira )?(?:the )?camera(?: app)?(?: open pannu| thira| podu| open)?$""")

    private val YT_A = Regex("""^(?:open |launch |start )?youtube(?: app)? (?:and |then )?(?:play|watch|podu) (.+)$""")
    private val YT_B = Regex("""^(?:play|watch|podu) (.+?) (?:on|in|from|la) youtube$""")
    private val YT_C = Regex("""^youtube (?:la|in|on) (.+?) (?:podu|play pannu|kaattu)$""")
    private val YT_D = Regex("""^(.+?) youtube (?:la|in|on) (?:podu|play pannu|kaattu)$|^(.+?) (?:podu|play pannu) youtube (?:la|in|on)$""")

    private val SEARCH_PREFIX_SITE = Regex("""^(youtube|google) (?:la|on|in) (?:search )?(.+?)(?: search pannu| thedu)?$""")
    private val SEARCH_A = Regex("""^(?:search|google|look up|thedu)(?: for)? (.+)$""")
    private val SEARCH_B = Regex("""^(.+?) (?:search pannu|thedu|google pannu)$""")
    private val SEARCH_SITE = Regex("""^(.+?) (?:on|in|la) (google|youtube)(?: search)?$""")
    private val IN_APP_WORDS = setOf("spotify", "amazon", "flipkart", "zomato", "swiggy", "instagram", "whatsapp", "telegram", "facebook", "twitter", "netflix", "hotstar", "gmail", "maps", "paytm", "phonepe", "uber", "ola")

    private val TYPE_RAW = Regex("""^\W*(?:(?:hey|ok|okay)\s+(?:jarvis|buddy)\W*)?(?:please\s+)?type\s+(.+?)[\s.]*$""", RegexOption.IGNORE_CASE)
    private val TAP_A = Regex("""^(?:tap|click|press|touch|select)(?: on)?(?: the)? (.{1,30}?)(?: button| option| icon| tab)?$""")
    private val TAP_B = Regex("""^(.{1,30}?) (?:click pannu|tap pannu|press pannu)$""")
    private val TAP_GENERIC = setOf("it", "this", "that", "screen", "anywhere", "here", "there", "something")

    private val OPEN_A = Regex("""^(?:open|launch|start|run|go to|switch to|thira)(?: up)?(?: the)? (.+?)(?: app| application)?$""")
    private val OPEN_B = Regex("""^(.+?)(?: app| application)? (?:open pannu|open|thira|launch pannu|start pannu|podu)$""")
    private val OPEN_BLOCKED = setOf("it", "this", "that", "something", "anything", "a", "an", "one", "up")

    /** Returns the command if [raw] is a complete simple command, otherwise null. */
    fun parse(raw: String, lastCommand: FastCommand? = null): FastCommand? {
        val t = Lexicon.clean(raw)
        if (t.isEmpty()) return null
        val words = t.split(' ')
        if (words.size > MAX_WORDS) return null
        if (lastCommand != null) {
            followUp(t, lastCommand)?.let { return it }
        }
        return mode(t) ?: language(t, words) ?: runtime(t)
            ?: press(t) ?: screenshotOrLock(t) ?: scroll(words)
            ?: volume(t, words) ?: flashlight(words, t) ?: media(words)
            ?: query(t) ?: timerOrAlarm(t, words) ?: dial(t) ?: callContact(t)
            ?: settingsPage(t, words) ?: camera(t)
            ?: mediaCount(t) ?: fileOp(t)
            ?: playYouTube(t)
            ?: open(t)
            ?: search(t)
            ?: typeText(raw) ?: tap(t)
    }

    private fun followUp(t: String, lastCommand: FastCommand): FastCommand? = when (lastCommand) {
        is FastCommand.Flashlight -> when (t) {
            "turn it off", "turn off", "turn off flashlight", "turn off torch", "turn off light", "shut it off", "switch it off", "stop", "off",
            "off pannu", "ippo atha off pannu", "atha off pannu", "atha anai", "light ah anai" ->
                FastCommand.Flashlight(false)
            "turn it on", "turn on", "turn on flashlight", "turn on torch", "turn on light", "switch it on", "start", "on",
            "on pannu", "ippo atha on pannu", "atha on pannu", "light ah podu" ->
                FastCommand.Flashlight(true)
            else -> null
        }
        is FastCommand.Media -> when (t) {
            "turn it off", "stop", "pause it", "pause", "stop it", "niruthu", "off pannu", "paatta niruthu" ->
                FastCommand.Media(MediaAction.PAUSE)
            "play", "resume", "continue", "start", "podu", "paada vidu" ->
                FastCommand.Media(MediaAction.PLAY)
            "next", "skip", "next song", "next track", "adutha paattu" ->
                FastCommand.Media(MediaAction.NEXT)
            "previous", "prev", "previous song", "munthaiya paattu" ->
                FastCommand.Media(MediaAction.PREVIOUS)
            else -> null
        }
        is FastCommand.Volume -> when (t) {
            "louder", "more", "a bit more", "increase", "higher", "turn it up", "up", "aethu", "kootu", "satham aethu" ->
                FastCommand.Volume(VolumeChange.UP)
            "quieter", "less", "softer", "a bit less", "decrease", "lower", "turn it down", "down", "korai", "kammi pannu" ->
                FastCommand.Volume(VolumeChange.DOWN)
            else -> null
        }
        else -> null
    }

    fun earlyAcceptDelayMs(raw: String): Long? = when (parse(raw)) {
        null -> null
        is FastCommand.OpenApp -> 800L
        is FastCommand.Press, is FastCommand.Scroll, is FastCommand.Flashlight, is FastCommand.Media,
        is FastCommand.Ask, is FastCommand.SetMode, is FastCommand.SetLanguage, is FastCommand.OpenSettings, is FastCommand.RuntimeControl,
        FastCommand.Screenshot, FastCommand.LockScreen, FastCommand.OpenCamera,
        is FastCommand.CountMedia, is FastCommand.FileOp,
        -> 600L
        is FastCommand.Volume -> 600L
        else -> null
    }

    private fun mode(t: String): FastCommand? {
        val m = MODE.matchEntire(t) ?: return null
        val explicit = "mode" in t || t.startsWith("go ") || t.startsWith("switch") || t.startsWith("use ") ||
            t.startsWith("set ") || "pannu" in t
        if (!explicit) return null
        return FastCommand.SetMode(if (m.groupValues[1].startsWith("auto")) AiMode.AUTO else AiMode.parse(m.groupValues[1]))
    }

    private fun runtime(t: String): FastCommand? = when {
        RUNTIME_DOWNLOAD.matches(t) -> FastCommand.RuntimeControl(RuntimeAction.DOWNLOAD)
        RUNTIME_STATUS.matches(t) -> FastCommand.RuntimeControl(RuntimeAction.STATUS)
        else -> null
    }

    private fun language(t: String, words: List<String>): FastCommand? {
        val m = LANG.matchEntire(t) ?: return null
        if (words.size == 1) return null
        return FastCommand.SetLanguage(if (m.groupValues[1] == "english") "en" else "ta")
    }

    private fun press(t: String): FastCommand? = when {
        BACK.matches(t) -> FastCommand.Press("back")
        HOME.matches(t) -> FastCommand.Press("home")
        RECENTS.matches(t) -> FastCommand.Press("recents")
        NOTIFICATIONS.matches(t) -> FastCommand.Press("notifications")
        QUICK_SETTINGS.matches(t) -> FastCommand.Press("quick_settings")
        else -> null
    }

    private fun screenshotOrLock(t: String): FastCommand? = when {
        SCREENSHOT.matches(t) -> FastCommand.Screenshot
        LOCK.matches(t) -> FastCommand.LockScreen
        else -> null
    }

    private fun scroll(words: List<String>): FastCommand? {
        if (words.size > 6 || ("scroll" !in words && "swipe" !in words)) return null
        if (words.any { it !in SCROLL_OK }) return null
        val dir = when {
            "up" in words || "mele" in words || "mela" in words -> "up"
            "left" in words -> "left"
            "right" in words -> "right"
            else -> "down"
        }
        return FastCommand.Scroll(dir)
    }

    private fun volume(t: String, words: List<String>): FastCommand? {
        when (t) {
            "mute", "mute pannu", "silent", "silent pannu", "mute phone", "mute the phone" ->
                return FastCommand.Volume(VolumeChange.MUTE)
            "unmute", "unmute pannu" -> return FastCommand.Volume(VolumeChange.UNMUTE)
            "louder" -> return FastCommand.Volume(VolumeChange.UP)
            "quieter" -> return FastCommand.Volume(VolumeChange.DOWN)
        }
        if (words.none { it in VOLUME_WORDS }) return null
        val number = Regex("""\b(\d{1,3})\b""").find(t)?.groupValues?.get(1)?.toIntOrNull()
        if (number != null) return FastCommand.Volume(VolumeChange.SET, number.coerceIn(0, 100))
        return when {
            words.any { it in UP_WORDS } -> FastCommand.Volume(VolumeChange.UP)
            words.any { it in DOWN_WORDS } -> FastCommand.Volume(VolumeChange.DOWN)
            else -> null
        }
    }

    private fun flashlight(words: List<String>, t: String = ""): FastCommand? {
        if (t == "turn it off" || t == "turn off" || t == "off pannu" || t == "ippo atha off pannu" || t == "atha off pannu") return FastCommand.Flashlight(false)
        if (t == "turn it on" || t == "turn on" || t == "on pannu" || t == "ippo atha on pannu" || t == "atha on pannu") return FastCommand.Flashlight(true)
        if (words.size > 6 || words.none { it in TORCH_WORDS }) return null
        val on = words.any { it in ON_WORDS }
        val off = words.any { it in OFF_WORDS }
        return when {
            on && !off -> FastCommand.Flashlight(true)
            off && !on -> FastCommand.Flashlight(false)
            !on && !off -> FastCommand.Flashlight(null)
            else -> null
        }
    }

    private fun media(words: List<String>): FastCommand? {
        if (words.size > 5) return null
        val hasMusic = words.any { it in MUSIC_WORDS }
        val rest = words.filterNot { it in MUSIC_WORDS || it in MEDIA_FILLER }
        if (rest.isEmpty()) return null
        if (rest.all { it in NEXT_WORDS } && (hasMusic || words.size <= 2)) return FastCommand.Media(MediaAction.NEXT)
        if (rest.all { it in PREV_WORDS } && (hasMusic || words.size <= 2)) return FastCommand.Media(MediaAction.PREVIOUS)
        if (!hasMusic) return null
        if (rest.all { it in PAUSE_WORDS }) return FastCommand.Media(MediaAction.PAUSE)
        if (rest.all { it in PLAY_WORDS }) return FastCommand.Media(MediaAction.PLAY)
        return null
    }

    private fun query(t: String): FastCommand? = when {
        TIME.matches(t) -> FastCommand.Ask(QueryKind.TIME)
        DATE.matches(t) -> FastCommand.Ask(QueryKind.DATE)
        BATTERY.matches(t) -> FastCommand.Ask(QueryKind.BATTERY)
        VOLUME_QUERY.matches(t) -> FastCommand.Ask(QueryKind.VOLUME)
        else -> null
    }

    private fun mediaCount(t: String): FastCommand? {
        val m = MEDIA_COUNT.matchEntire(t) ?: return null
        return FastCommand.CountMedia(m.groupValues[1])
    }

    private fun fileOp(t: String): FastCommand? {
        if (FILE_LIST.matches(t)) return FastCommand.FileOp("list")
        FILE_FIND.matchEntire(t)?.let { return FastCommand.FileOp("find", filename = it.groupValues[1].trim()) }
        FILE_READ.matchEntire(t)?.let { m ->
            val name = (m.groupValues.getOrNull(1) ?: m.groupValues.getOrNull(2)).orEmpty().trim()
            return FastCommand.FileOp("read", filename = name)
        }
        FILE_CREATE.matchEntire(t)?.let { return FastCommand.FileOp("create", filename = it.groupValues[1].trim(), content = it.groupValues[2].trim()) }
        return null
    }

    private fun durationSeconds(t: String): Int {
        var total = 0L
        for (m in DURATION.findAll(t)) {
            val n = m.groupValues[1].toLongOrNull() ?: continue
            val unit = m.groupValues[2]
            total += n * when {
                unit.startsWith("sec") -> 1
                unit.startsWith("min") || unit.startsWith("nim") -> 60
                else -> 3600
            }
        }
        return total.coerceAtMost(24L * 3600).toInt()
    }

    private fun timerOrAlarm(t: String, words: List<String>): FastCommand? {
        val isTimer = "timer" in words
        val isAlarm = "alarm" in words || ("wake" in words && "me" in words) || "eழுப்பு" in words
        if (!isTimer && !isAlarm) return null
        val seconds = durationSeconds(t)
        if (seconds > 0) return FastCommand.SetTimer(seconds)
        if (isTimer) return null
        val m = CLOCK.find(t) ?: return null
        var hour = m.groupValues[1].toIntOrNull() ?: return null
        val minute = m.groupValues[2].toIntOrNull() ?: 0
        if (minute !in 0..59 || hour !in 0..23) return null
        val explicit = m.groupValues[3]
        val afternoon = words.any { it in setOf("evening", "afternoon", "sayanthram", "madhiyam", "maalai") }
        val night = words.any { it in setOf("night", "iravu") }
        val pm = explicit == "pm" || (explicit != "am" && (afternoon || night))
        if (hour <= 12) {
            if (pm && hour < 12) hour += 12
            if (!pm && hour == 12 && (explicit == "am" || night || words.any { it in setOf("kaalai", "morning") })) hour = 0
            if (night && !pm && hour == 12) hour = 0
        }
        return FastCommand.SetAlarm(hour, minute)
    }

    private fun dial(t: String): FastCommand? {
        val m = DIAL_A.matchEntire(t) ?: DIAL_B.matchEntire(t) ?: return null
        val number = m.groupValues[1].filter { it.isDigit() || it == '+' }
        return if (number.count { it.isDigit() } >= 6) FastCommand.Dial(number) else null
    }

    private fun callContact(t: String): FastCommand? {
        val m = CALL_A.matchEntire(t) ?: CALL_B.matchEntire(t) ?: return null
        val name = m.groupValues.drop(1).firstOrNull { it.isNotEmpty() }?.trim().orEmpty()
        val nw = name.split(' ')
        if (name.isEmpty() || nw.size > 3 || nw.first() in NOT_A_NAME || nw.first() == "me") return null
        if (name.any { it.isDigit() }) return null
        return FastCommand.CallContact(name)
    }

    private fun settingsPage(t: String, words: List<String>): FastCommand? {
        if (words.size > 6) return null
        val page = PAGES.entries.firstOrNull { (k, _) -> " $t ".contains(" $k ") || " $t ".contains(" ${k}s ") }?.value ?: return null
        val mentionsSettings = "settings" in words || "setting" in words
        if (mentionsSettings) return FastCommand.OpenSettings(page)
        if (page in TOGGLE_PAGES && words.all { it in TOGGLE_OK } && words.any { it in ON_WORDS || it in OFF_WORDS || it == "enable" || it == "disable" }) {
            return FastCommand.OpenSettings(page, toggleRequested = true)
        }
        return null
    }

    private fun camera(t: String): FastCommand? = if (CAMERA.matches(t)) FastCommand.OpenCamera else null

    private fun playYouTube(t: String): FastCommand? {
        val m = YT_A.matchEntire(t) ?: YT_B.matchEntire(t) ?: YT_C.matchEntire(t) ?: YT_D.matchEntire(t) ?: return null
        val q = m.groupValues.drop(1).firstOrNull { it.isNotEmpty() }?.trim().orEmpty()
        if (q.isEmpty() || q.split(' ').size > 8) return null
        return FastCommand.PlayYouTube(q)
    }

    private fun open(t: String): FastCommand? {
        val target = (OPEN_A.matchEntire(t) ?: OPEN_B.matchEntire(t))?.groupValues?.get(1)?.trim() ?: return null
        val tw = target.split(' ')
        if (target.isEmpty() || tw.size > 4 || target in OPEN_BLOCKED) return null
        if (tw.any { it.all(Char::isDigit) }) return null
        if (" and " in " $target " || " marrum " in " $target ") return null
        return FastCommand.OpenApp(target)
    }

    private fun search(t: String): FastCommand? {
        SEARCH_PREFIX_SITE.matchEntire(t)?.let { m ->
            return searchOf(m.groupValues[2], m.groupValues[1])
        }
        val q = (SEARCH_A.matchEntire(t) ?: SEARCH_B.matchEntire(t))?.groupValues?.get(1)?.trim() ?: return null
        SEARCH_SITE.matchEntire(q)?.let { m -> return searchOf(m.groupValues[1], m.groupValues[2]) }
        val lastWord = q.substringAfterLast(' ')
        if (lastWord in IN_APP_WORDS && q.contains(' ')) return null
        return searchOf(q, "google")
    }

    private fun searchOf(query: String, site: String): FastCommand? {
        val q = query.trim()
        if (q.isEmpty() || q.split(' ').size > 8) return null
        return FastCommand.WebSearch(q, site)
    }

    private fun typeText(raw: String): FastCommand? {
        val text = TYPE_RAW.matchEntire(raw.trim())?.groupValues?.get(1)?.trim().orEmpty()
        return if (text.isNotEmpty() && text.length <= 300) FastCommand.TypeText(text) else null
    }

    private fun tap(t: String): FastCommand? {
        val label = (TAP_A.matchEntire(t) ?: TAP_B.matchEntire(t))?.groupValues?.get(1)?.trim() ?: return null
        if (label.isEmpty() || label in TAP_GENERIC) return null
        return FastCommand.TapText(label)
    }
}
