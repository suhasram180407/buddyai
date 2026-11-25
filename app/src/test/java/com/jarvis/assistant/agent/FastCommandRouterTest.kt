package com.jarvis.assistant.agent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FastCommandRouterTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun matchesTorchCommands() {
        val onResult = FastCommandRouter.execute(context, "turn on the flashlight")
        assertTrue("turn on flashlight should be handled", onResult.handled)

        val onTamil = FastCommandRouter.execute(context, "டார்ச் லைட் ஆன் பண்ணு")
        assertTrue("Tamil torch light on should be handled", onTamil.handled)

        val offResult = FastCommandRouter.execute(context, "torch light off pannu")
        assertTrue("torch light off pannu should be handled", offResult.handled)
    }

    @Test
    fun matchesVolumeCommands() {
        val up = FastCommandRouter.execute(context, "volume up")
        assertTrue("volume up should be handled", up.handled)

        val downTanglish = FastCommandRouter.execute(context, "volume kammi pannu")
        assertTrue("volume kammi pannu should be handled", downTanglish.handled)

        val percentTamil = FastCommandRouter.execute(context, "Volume ah 70 percent ku set pannu")
        assertTrue("volume 70 percent should be handled", percentTamil.handled)

        val mute = FastCommandRouter.execute(context, "mute")
        assertTrue("mute should be handled", mute.handled)
    }

    @Test
    fun matchesMediaCommands() {
        val play = FastCommandRouter.execute(context, "paattu podu")
        assertTrue("paattu podu should be handled", play.handled)

        val pause = FastCommandRouter.execute(context, "pause music")
        assertTrue("pause music should be handled", pause.handled)
    }

    @Test
    fun matchesAlarmAndTimerCommands() {
        val alarm = FastCommandRouter.execute(context, "set alarm for 7:30 am")
        assertTrue("set alarm for 7:30 am should be handled", alarm.handled)

        val timer = FastCommandRouter.execute(context, "set timer for 15 minutes")
        assertTrue("set timer for 15 minutes should be handled", timer.handled)
    }

    @Test
    fun matchesMemoryLearningCommands() {
        val mem = FastCommandRouter.execute(context, "remember that I prefer short answers")
        assertTrue("remember that should be handled", mem.handled)
        assertTrue(mem.summary.contains("remember"))

        val alias = FastCommandRouter.execute(context, "when I say yt open YouTube")
        assertTrue("alias should be handled", alias.handled)
    }

    @Test
    fun matchesAppLaunchCommands() {
        val yt = FastCommandRouter.execute(context, "open YouTube")
        assertTrue("open YouTube should be handled", yt.handled)

        val ytTanglish = FastCommandRouter.execute(context, "YouTube open pannu")
        assertTrue("YouTube open pannu should be handled", ytTanglish.handled)

        val ytTamil = FastCommandRouter.execute(context, "யூடியூப் திற")
        assertTrue("யூடியூப் திற should be handled", ytTamil.handled)

        val directYt = FastCommandRouter.execute(context, "YouTube")
        assertTrue("direct YouTube should be handled", directYt.handled)
    }

    @Test
    fun ignoresComplexConversationalQuestions() {
        val result = FastCommandRouter.execute(context, "What is the capital of France?")
        assertFalse("General question should not be intercepted by fast command router", result.handled)
    }
}
