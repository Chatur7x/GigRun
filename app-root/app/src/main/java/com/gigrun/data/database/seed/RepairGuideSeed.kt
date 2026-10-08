package com.gigrun.data.database.seed

/**
 * A single self-help repair guide for a petrol two-wheeler used in Indian
 * delivery work. Costs are indicative INR ranges for a local workshop or
 * part purchase and are not a quote.
 */
data class RepairGuideData(
    val symptom: String,
    val title: String,
    val steps: List<String>,
    val difficulty: String,
    val estimatedCostMinInr: Int,
    val estimatedCostMaxInr: Int,
    val toolsNeeded: List<String>
)

object RepairGuideSeed {

    val guides: List<RepairGuideData> = listOf(
        RepairGuideData(
            symptom = "Bike won't start",
            title = "Bike won't start",
            steps = listOf(
                "Park the bike on the centre stand and switch off the engine.",
                "Confirm the kill switch on the handlebar is in the OFF position.",
                "Confirm the gear selector is in neutral for a self-start bike.",
                "Press the brake lever firmly while attempting a self-start.",
                "Check that the fuel indicator light comes on when the key is turned.",
                "Open the fuel tap and check there is fuel in the tank.",
                "Inspect the battery terminals for looseness or green corrosion.",
                "If the starter clicks without turning, inspect the battery voltage with a multimeter."
            ),
            difficulty = "Medium",
            estimatedCostMinInr = 100,
            estimatedCostMaxInr = 600,
            toolsNeeded = listOf("Multimeter", "10mm spanner", "Battery terminal cleaner")
        ),
        RepairGuideData(
            symptom = "Chain noise",
            title = "Chain noise",
            steps = listOf(
                "Stop the bike, place it on the centre stand and let the chain cool.",
                "Wear gloves and keep your fingers clear of the chain while you inspect it.",
                "Check the chain slack at the mid point between the sprockets.",
                "Tighten the adjuster bolt by quarter turns until the slack is within limits.",
                "Clean the chain and sprockets with a degreaser and a stiff brush.",
                "Re-check the alignment marks on the swingarm after adjusting.",
                "Apply chain lubricant and wipe off the excess.",
                "Ride for two minutes and re-check the slack once more."
            ),
            difficulty = "Easy",
            estimatedCostMinInr = 0,
            estimatedCostMaxInr = 200,
            toolsNeeded = listOf("Chain lube", "Degreaser", "Stiff brush", "6mm spanner")
        ),
        RepairGuideData(
            symptom = "Weak brakes",
            title = "Weak brakes",
            steps = listOf(
                "Let the engine and exhaust cool before touching the brake components.",
                "Wear gloves and keep oil and grease away from the brake disc.",
                "Squeeze the front brake lever and check the cable is taut at the lever.",
                "Check the brake pad thickness at the caliper with a feeler gauge.",
                "Inspect the brake disc for scoring, warping and a minimum thickness.",
                "Check the brake fluid level in the reservoir for the hydraulic front brake.",
                "Look for fluid weeping at the caliper or along the hose.",
                "If the pads and fluid are fine, get the caliper and shoes checked by a mechanic."
            ),
            difficulty = "Hard",
            estimatedCostMinInr = 400,
            estimatedCostMaxInr = 2500,
            toolsNeeded = listOf("Feeler gauge", "Torque wrench", "Allen key set")
        ),
        RepairGuideData(
            symptom = "Puncture",
            title = "Puncture",
            steps = listOf(
                "Move the bike off the road before beginning the repair.",
                "Remove the wheel and lay it on a clean cloth.",
                "Deflate the tube fully and press the rim bead down on both sides.",
                "Find the puncture by submerging the tube in water and looking for bubbles.",
                "Mark the spot with a chalk mark on the tube.",
                "Rasp the tube surface, apply solution and patch the tube.",
                "Inflate the tube slowly and check for leaks.",
                "Fit the tube and tyre, inflate to the pressure marked on the sidewall and refit the wheel."
            ),
            difficulty = "Medium",
            estimatedCostMinInr = 100,
            estimatedCostMaxInr = 600,
            toolsNeeded = listOf("Puncture repair kit", "Tyre levers", "Pump with gauge", "Rasp")
        ),
        RepairGuideData(
            symptom = "Flat battery",
            title = "Flat battery",
            steps = listOf(
                "Switch off all lights and accessories to reduce the load on the battery.",
                "Let the engine and exhaust cool and wear gloves before working near the battery.",
                "Clean the battery terminals with a terminal cleaner and a stiff brush.",
                "Tighten the terminal clamp and check the earth connection.",
                "Measure the resting voltage with a multimeter.",
                "Fit the correct battery charger and charge it at the slow rate overnight.",
                "Check the voltage again the next morning before fitting the battery.",
                "Replace the battery with the same specification if the voltage is still low."
            ),
            difficulty = "Hard",
            estimatedCostMinInr = 400,
            estimatedCostMaxInr = 2500,
            toolsNeeded = listOf("Multimeter", "Battery charger", "10mm spanner")
        ),
        RepairGuideData(
            symptom = "Spark plug fouled",
            title = "Spark plug fouled",
            steps = listOf(
                "Let the engine cool completely before removing the spark plug.",
                "Wear gloves and keep grit away from the plug hole.",
                "Remove the spark plug cap and then loosen the plug with a spark plug socket.",
                "Inspect the plug tip for heavy black carbon, oil or white powder deposits.",
                "Check the electrode gap against the service manual value.",
                "Clean the plug with a brass brush or replace it with a new one.",
                "Refit the plug, tighten it firmly and reconnect the cap.",
                "Start the engine and confirm the revs stay steady."
            ),
            difficulty = "Medium",
            estimatedCostMinInr = 100,
            estimatedCostMaxInr = 600,
            toolsNeeded = listOf("Spark plug socket", "Feeler gauge", "Brass brush")
        ),
        RepairGuideData(
            symptom = "Air filter clogged",
            title = "Air filter clogged",
            steps = listOf(
                "Turn off the engine and let it cool.",
                "Remove the seat and open the air filter box cover.",
                "Undo the wing nut or clip holding the air filter element.",
                "Take out the filter element and check it against light.",
                "Wash the element in kerosene and let it dry fully in shade.",
                "Wipe out the air box and check for dirt or oil inside.",
                "Refit the clean element and close the air box cover.",
                "Refit the seat and start the engine."
            ),
            difficulty = "Easy",
            estimatedCostMinInr = 0,
            estimatedCostMaxInr = 200,
            toolsNeeded = listOf("Kerosene", "Air brush", "Clean cloth")
        ),
        RepairGuideData(
            symptom = "Oil low",
            title = "Oil low",
            steps = listOf(
                "Place the bike upright on level ground and let the engine cool for ten minutes.",
                "Remove the dipstick and wipe it clean with a cloth.",
                "Reinsert the dipstick fully and pull it out again.",
                "Check that the oil sits between the minimum and maximum marks.",
                "Add engine oil of the grade stated in the manual, a little at a time.",
                "Refit the dipstick and start the engine for one minute.",
                "Switch off, wait and re-check the level with the dipstick.",
                "Top up again if the level is still below the maximum mark."
            ),
            difficulty = "Easy",
            estimatedCostMinInr = 0,
            estimatedCostMaxInr = 200,
            toolsNeeded = listOf("Dipstick", "Engine oil", "Clean cloth")
        ),
        RepairGuideData(
            symptom = "Clutch slipping",
            title = "Clutch slipping",
            steps = listOf(
                "Let the engine and exhaust cool and wear gloves before touching the clutch.",
                "Check the clutch lever free play against the manual limit.",
                "Check the clutch cable for slack, fraying and a broken end.",
                "Adjust the clutch cable so that the lever feels firm but does not drag.",
                "Check that the engine oil level and grade are correct.",
                "Inspect the clutch plates for oil contamination and excess wear.",
                "Check for a stretched or worn clutch spring.",
                "If plates, springs or the basket are worn, get the clutch plate replaced by a mechanic."
            ),
            difficulty = "Hard",
            estimatedCostMinInr = 400,
            estimatedCostMaxInr = 2500,
            toolsNeeded = listOf("10mm spanner", "Feeler gauge", "Screwdriver set")
        ),
        RepairGuideData(
            symptom = "Headlight out",
            title = "Headlight out",
            steps = listOf(
                "Turn off the engine and let the headlight assembly cool.",
                "Wear gloves before reaching near the bulb socket.",
                "Remove the headlight cover or the visor as per the model.",
                "Pull out the bulb socket and inspect the bulb filament for a break.",
                "Check the bulb rating against the one printed on the lens.",
                "Check the battery voltage with a multimeter while the headlight is switched on.",
                "Refit a new bulb, seat the cover and check the beam aim on a wall.",
                "Disconnect the battery before doing any wiring work on the headlight harness."
            ),
            difficulty = "Medium",
            estimatedCostMinInr = 100,
            estimatedCostMaxInr = 600,
            toolsNeeded = listOf("Multimeter", "Screwdriver set", "Replacement bulb")
        ),
        RepairGuideData(
            symptom = "Tyre pressure",
            title = "Tyre pressure",
            steps = listOf(
                "Check the pressure when the tyres are cold, before the first ride of the day.",
                "Read the recommended pressures marked on the tyre sidewall or the manual.",
                "Remove the valve cap from the tyre valve.",
                "Check the pressure with a tyre gauge fitted to the valve.",
                "Add air in short bursts if the pressure is low.",
                "Let air out gently if the pressure is high.",
                "Refit the valve cap and repeat the check on the other tyre.",
                "Re-check both pressures once a month and note the reading."
            ),
            difficulty = "Easy",
            estimatedCostMinInr = 0,
            estimatedCostMaxInr = 200,
            toolsNeeded = listOf("Tyre pressure gauge", "Foot pump")
        ),
        RepairGuideData(
            symptom = "Carburetor flooding",
            title = "Carburetor flooding",
            steps = listOf(
                "Turn off the engine and let it cool for at least fifteen minutes.",
                "Wear gloves and eye protection as fuel may spill from the carburetor.",
                "Turn off the fuel tap before opening the carburetor.",
                "Remove the carburetor and check that the float valve needle is not stuck open.",
                "Clean the float valve seat and the needle with carburetor cleaner.",
                "Check the float level against the manual marking and adjust the float tab if needed.",
                "Fit a new float valve gasket if the old one is torn.",
                "Refit the carburetor, open the fuel tap and start the engine."
            ),
            difficulty = "Hard",
            estimatedCostMinInr = 400,
            estimatedCostMaxInr = 2500,
            toolsNeeded = listOf("Carburetor cleaner", "Screwdriver set", "Float setting gauge")
        )
    )
}