package com.gigrun.core.utils

/**
 * High-level, navigational walkthrough of the Government of India e-Shram
 * (Shram Shakti Seva) gig-worker registration flow.
 *
 * This is an unofficial helper. The official portal is the source of truth and
 * the official guidelines prevail over anything stated here. Steps, eligibility
 * rules and the registration process can change, so always confirm current
 * requirements on [portalUrl] before guiding a user.
 *
 * The content is deliberately non-committal: it names no form fields, no
 * deadlines and makes no legal claims.
 */
object EShramGuide {

    /** One short navigational step in the registration flow. */
    data class GuideStep(
        val title: String,
        val detail: String
    )

    /** The official e-Shram portal address. */
    val portalUrl: String = "https://www.eshram.gov.in"

    /** Ordered, high-level steps of the e-Shram registration flow. */
    val steps: List<GuideStep> = listOf(
        GuideStep(
            "Access the portal",
            "Open the official e-Shram website on your phone or computer to begin registration."
        ),
        GuideStep(
            "Verify your mobile number",
            "Enter your mobile number and complete the one-time password verification sent to that number."
        ),
        GuideStep(
            "Verify your Aadhaar",
            "Complete the Aadhaar based one-time password check so your identity is linked to the registration."
        ),
        GuideStep(
            "Add gig platform IDs",
            "Provide the gig platform identifiers for the apps you work on so your work records can be mapped."
        ),
        GuideStep(
            "Add your work history",
            "Enter or confirm the work details you have already submitted through those gig platforms."
        ),
        GuideStep(
            "Link your bank account",
            "Add your bank account details so eligible benefits can be credited to you directly."
        ),
        GuideStep(
            "Review and submit",
            "Check every entered detail carefully on the review screen and then submit your registration."
        ),
        GuideStep(
            "Download your certificate",
            "After registration is recorded, use the portal to view and download your e-Shram certificate."
        )
    )
}