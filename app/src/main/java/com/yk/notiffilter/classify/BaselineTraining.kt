package com.yk.notiffilter.classify

/**
 * Baseline training examples that anchor the Naive Bayes model.
 *
 * Solves the cold-start and one-sided training problem:
 * If a user only labels spam as HIDE (the most common user behavior),
 * the model would otherwise have zero KEEP examples, breaking Laplace odds calculations.
 *
 * These baseline examples represent universally recognized transactional KEEP notifications
 * (OTP, order tracking, transit, banking) and standard promotional HIDE notifications.
 */
object BaselineTraining {

    val BASELINE: List<Labelled> = listOf(
        // Transactional / Security KEEP baselines
        Labelled(
            Tokenizer.features("security", "Your OTP is 482910", "Use one time password to approve payment"),
            keep = true,
        ),
        Labelled(
            Tokenizer.features("orders", "Order Out for Delivery", "Your delivery partner is on the way"),
            keep = true,
        ),
        Labelled(
            Tokenizer.features("orders", "Order Confirmed", "Your order has been placed successfully"),
            keep = true,
        ),
        Labelled(
            Tokenizer.features("transit", "Ride Arriving", "Your driver is arriving in 2 minutes"),
            keep = true,
        ),
        Labelled(
            Tokenizer.features("banking", "Account Alert", "INR 500 debited from your account"),
            keep = true,
        ),

        // Promotional HIDE baselines
        Labelled(
            Tokenizer.features("marketing", "Mega Sale", "Flat 50% OFF use coupon code now"),
            keep = false,
        ),
        Labelled(
            Tokenizer.features("promo", "Cashback Alert", "Get 20% cashback points today only"),
            keep = false,
        ),
        Labelled(
            Tokenizer.features("marketing", "Limited Time Offer", "Special discount deal ends tonight"),
            keep = false,
        ),
    )
}
