package com.yk.notiffilter

import com.yk.notiffilter.classify.ClassifyRequest
import com.yk.notiffilter.classify.Decision
import com.yk.notiffilter.classify.Labelled
import com.yk.notiffilter.classify.NaiveBayesClassifier
import com.yk.notiffilter.classify.Tokenizer
import com.yk.notiffilter.classify.TrainingSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NaiveBayesClassifierTest {

    private class Fake(
        private val app: List<Labelled> = emptyList(),
        private val global: List<Labelled> = app,
    ) : TrainingSource {
        override suspend fun forApp(packageName: String) = app
        override suspend fun global() = global
    }

    private fun labelled(channel: String?, title: String, text: String, keep: Boolean) =
        Labelled(Tokenizer.features(channel, title, text), keep)

    private fun ask(channel: String?, title: String, text: String) =
        ClassifyRequest("in.swiggy.android", channel, title, text)

    /** A realistic Swiggy-shaped training set: marketing copy versus order updates. */
    private fun swiggy(): List<Labelled> = listOf(
        labelled("promo", "FLAT 60% OFF", "Order now and save ₹250!", keep = false),
        labelled("promo", "🔥 Buy 1 Get 1", "Only today, grab it fast!", keep = false),
        labelled("promo", "Your favourite is back", "Order now for 40% off", keep = false),
        labelled("promo", "Hungry? 🍕", "Flat ₹150 off your next order!", keep = false),
        labelled("promo", "LAST CHANCE", "50% off ends tonight!", keep = false),
        labelled("orders", "Order Delivered", "Enjoy your meal", keep = true),
        labelled("orders", "Order Confirmed", "Your order has been placed", keep = true),
        labelled("orders", "Refund Processed", "Your refund has been issued", keep = true),
        labelled("orders", "Order Delayed", "Your order is running late", keep = true),
        labelled("orders", "Arriving soon", "Your rider is nearby", keep = true),
    )

    @Test
    fun `untrained model is unsure about everything`() = runTest {
        val classifier = NaiveBayesClassifier(Fake())
        val result = classifier.classify(ask("promo", "FLAT 60% OFF", "Order now!"))

        assertEquals(Decision.UNSURE, result.decision)
        assertTrue(result.reason.contains("Not enough"))
    }

    @Test
    fun `a handful of labels is still not enough to act`() = runTest {
        // Two examples produce arithmetically large log-odds that mean nothing. The
        // minimum-examples gate is what stops the first week being confidently wrong.
        val classifier = NaiveBayesClassifier(Fake(swiggy().take(2)))
        assertEquals(
            Decision.UNSURE,
            classifier.classify(ask("promo", "FLAT 70% OFF", "Save now!")).decision,
        )
    }

    @Test
    fun `recognises an unseen promo from wording it has never read`() = runTest {
        val classifier = NaiveBayesClassifier(Fake(swiggy()))
        val result = classifier.classify(
            ask("promo", "MEGA SAVER 🎉", "Grab 70% off before midnight!"),
        )

        assertEquals(Decision.HIDE, result.decision)
        assertTrue("score should point at hide", result.score > 0)
    }

    @Test
    fun `recognises an unseen order update`() = runTest {
        val classifier = NaiveBayesClassifier(Fake(swiggy()))
        val result = classifier.classify(ask("orders", "Order Picked Up", "Your rider has collected it"))

        assertEquals(Decision.KEEP, result.decision)
        assertTrue("score should point at keep", result.score < 0)
    }

    @Test
    fun `genuinely ambiguous wording is queued rather than guessed`() = runTest {
        val classifier = NaiveBayesClassifier(Fake(swiggy()))
        val result = classifier.classify(ask(null, "Update", "Something happened"))

        assertEquals(Decision.UNSURE, result.decision)
    }

    @Test
    fun `an app's own labels outweigh the global prior`() = runTest {
        // Globally "order" is promotional; in this app the user has repeatedly said
        // the opposite. The per-app weighting must let their labels win.
        val globalPromo = List(6) {
            labelled("promo", "Order now", "Big discounts inside!", keep = false)
        }
        val appSays = List(6) {
            labelled("alerts", "Order now", "Your standing order needs attention", keep = true)
        }

        val classifier = NaiveBayesClassifier(Fake(app = appSays, global = globalPromo + appSays))
        val result = classifier.classify(ask("alerts", "Order now", "Your standing order needs attention"))

        assertEquals(Decision.KEEP, result.decision)
    }

    @Test
    fun `a new app with no labels of its own still inherits the pooled prior`() = runTest {
        // Cold start without a cliff: an app that has taught the model nothing is
        // still classified by what every other app taught it.
        val classifier = NaiveBayesClassifier(Fake(app = emptyList(), global = swiggy()))
        val result = classifier.classify(
            ClassifyRequest("com.other.app", "promo", "FLAT 50% OFF", "Order now and save!"),
        )

        assertEquals(Decision.HIDE, result.decision)
    }

    @Test
    fun `user real session data with only HIDE verdicts can still classify strong marketing spam`() = runTest {
        val userSessionData = listOf(
            labelled("marketing_notification_id", "Meetings are hard enough. 😭", "Make lunch easy with 70% OFF!", keep = false),
            labelled("marketing_notification_id", "Running on chai & hope? 🫠", "Please get yourself something proper to eat.", keep = false),
            labelled("your channel id", "Tune in to Nifty Ki Baat", "", keep = false),
            labelled("your channel id", "When Income Grows, Investments Should Too ", "", keep = false),
            labelled("moe_default_channel", "Treats, with plot twists!", "Explore naturally sweetened & unsweetened options.", keep = false),
        )
        val classifier = NaiveBayesClassifier(
            Fake(
                app = userSessionData,
                global = userSessionData + com.yk.notiffilter.classify.BaselineTraining.BASELINE,
            ),
        )
        val result = classifier.classify(ask("marketing_notification_id", "Make lunch easy with 50% OFF!", "Explore sweetened treats"))
        assertEquals("score=${result.score}, reason=${result.reason}", Decision.HIDE, result.decision)
    }

    @Test
    fun `the reason names the evidence that moved the verdict`() = runTest {
        val classifier = NaiveBayesClassifier(Fake(swiggy()))
        val result = classifier.classify(ask("promo", "FLAT 70% OFF", "Grab it now!"))

        assertTrue("reason should be checkable, was: ${result.reason}", result.reason.contains(":"))
    }

    @Test
    fun `raising the margin turns decisions into questions`() = runTest {
        val cautious = NaiveBayesClassifier(Fake(swiggy()), confidenceMargin = 1000.0)
        assertEquals(
            Decision.UNSURE,
            cautious.classify(ask("promo", "MEGA SAVER 🎉", "Grab 70% off!")).decision,
        )
    }

    @Test
    fun `lean is reported even when the verdict is unsure`() = runTest {
        val cautious = NaiveBayesClassifier(Fake(swiggy()), confidenceMargin = 1000.0)
        val promo = cautious.classify(ask("promo", "MEGA SAVER 🎉", "Grab 70% off!"))
        val order = cautious.classify(ask("orders", "Order Packed", "Your food is ready"))

        assertEquals(Decision.UNSURE, promo.decision)
        assertEquals(Decision.HIDE, promo.lean)
        assertEquals(Decision.UNSURE, order.decision)
        assertEquals(Decision.KEEP, order.lean)
    }

    /**
     * The shipped-bug regression.
     *
     * A build seeded five HIDE verdicts and no KEEP one. Every feature of an unseen
     * notification was then absent from an empty KEEP side, so each one contributed a
     * large negative weight and the total cleared any sane margin. The model called
     * genuinely novel notifications a confident KEEP, the engine cancelled them and
     * re-posted lossy copies, and the user stopped receiving real notifications.
     */
    @Test
    fun `one-sided training never produces a decision however large the score`() = runTest {
        val hideOnly = listOf(
            labelled("promo", "Meetings are hard enough 😭", "Make lunch easy with 70% OFF!", keep = false),
            labelled("promo", "Running on chai & hope? 🫠", "Get something proper to eat.", keep = false),
            labelled("promo", "Tune in to Nifty Ki Baat", "", keep = false),
            labelled("promo", "When Income Grows, Investments Should Too", "", keep = false),
            labelled("promo", "Treats, with plot twists!", "Explore sweetened options 💗🧁", keep = false),
            labelled("promo", "LAST CHANCE", "50% off ends tonight!", keep = false),
            labelled("promo", "Hungry? 🍕", "Flat ₹150 off your next order!", keep = false),
            labelled("promo", "🔥 Buy 1 Get 1", "Only today, grab it fast!", keep = false),
        )
        val classifier = NaiveBayesClassifier(Fake(hideOnly))

        // Past minExamples, and the raw log-odds are enormous — which is precisely
        // the artefact the gate exists to disbelieve.
        val novel = classifier.classify(ask("orders", "Order Delivered", "Enjoy your meal"))
        assertTrue("expected a large artefact score, got ${novel.score}", novel.score < -4.0)
        assertEquals(Decision.UNSURE, novel.decision)
        assertTrue(novel.reason.contains("keep"))
    }

    @Test
    fun `a balanced set of the same size does decide`() = runTest {
        val decisive = NaiveBayesClassifier(Fake(swiggy()))
        assertEquals(
            Decision.HIDE,
            decisive.classify(ask("promo", "MEGA SAVER 🎉", "Grab 70% off before midnight!")).decision,
        )
    }

    @Test
    fun `bigrams distinguish order now from order confirmed`() = runTest {
        val training = listOf(
            labelled("food", "Order Now", "Get 50% discount", keep = false),
            labelled("food", "Order Confirmed", "Your meal is being prepared", keep = true),
            labelled("food", "Order Delivered", "Enjoy your dinner", keep = true),
            labelled("food", "Special Deal", "Order now to save", keep = false),
            labelled("food", "Limited Offer", "Order now for promo", keep = false),
            labelled("food", "Driver Assigned", "Your order has been picked up", keep = true),
        )
        val classifier = NaiveBayesClassifier(Fake(training), confidenceMargin = 2.0, minExamples = 3)
        val promo = classifier.classify(ask("food", "Order Now", "Don't miss out"))
        assertEquals(Decision.HIDE, promo.decision)

        val update = classifier.classify(ask("food", "Order Confirmed", "Arriving soon"))
        assertEquals(Decision.KEEP, update.decision)
    }
}
