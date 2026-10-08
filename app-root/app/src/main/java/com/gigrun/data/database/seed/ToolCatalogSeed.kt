package com.gigrun.data.database.seed

/**
 * A single tool or consumable a delivery rider may buy for roadside and
 * roadside-emergency use. Prices are indicative Indian retail approximations
 * in whole rupees and are not a quote.
 */
data class ToolData(
    val name: String,
    val category: String,
    val description: String,
    val priceInr: Int,
    val buyUrl: String?
)

object ToolCatalogSeed {

    val tools: List<ToolData> = listOf(
        ToolData(
            name = "Puncture repair kit",
            category = "Puncture",
            description = "Plug and patch patches a tube puncture without removing the wheel.",
            priceInr = 650,
            buyUrl = null
        ),
        ToolData(
            name = "8mm combination spanner",
            category = "Hand Tools",
            description = "Tightens clutch and engine bolts where a hex key will not hold.",
            priceInr = 90,
            buyUrl = null
        ),
        ToolData(
            name = "10mm combination spanner",
            category = "Hand Tools",
            description = "Fits axle nut and carburetor bolts on most two-wheelers.",
            priceInr = 110,
            buyUrl = null
        ),
        ToolData(
            name = "12mm combination spanner",
            category = "Hand Tools",
            description = "Used for swingarm, footrest and side-stand fasteners.",
            priceInr = 130,
            buyUrl = null
        ),
        ToolData(
            name = "14mm combination spanner",
            category = "Hand Tools",
            description = "Handles larger nuts like front axle and head-strip bolts.",
            priceInr = 160,
            buyUrl = null
        ),
        ToolData(
            name = "Allen key set (metric)",
            category = "Hand Tools",
            description = "Six sizes for internal hex bolts on panels and disc parts.",
            priceInr = 140,
            buyUrl = null
        ),
        ToolData(
            name = "Multi-bit screwdriver",
            category = "Hand Tools",
            description = "One handle with interchangeable bits for everyday bike jobs.",
            priceInr = 220,
            buyUrl = null
        ),
        ToolData(
            name = "Mini LED torch",
            category = "Electronics",
            description = "Small rechargeable light for checking punctures at night.",
            priceInr = 350,
            buyUrl = null
        ),
        ToolData(
            name = "Cable tie assortment",
            category = "Maintenance",
            description = "Ties loose wiring and mudguard parts back in place securely.",
            priceInr = 80,
            buyUrl = null
        ),
        ToolData(
            name = "Electrical tape",
            category = "Electrical",
            description = "Insulates exposed wiring and holds sensor leads while riding.",
            priceInr = 40,
            buyUrl = null
        ),
        ToolData(
            name = "First-aid kit",
            category = "Safety",
            description = "Bandages, antiseptic and dressings for road rash and cuts.",
            priceInr = 450,
            buyUrl = null
        ),
        ToolData(
            name = "Tyre pressure gauge",
            category = "Maintenance",
            description = "Checks cold tyre pressure to avoid mid-ride punctures.",
            priceInr = 380,
            buyUrl = null
        ),
        ToolData(
            name = "Fuel system cleaner",
            category = "Fluids",
            description = "Cleans carburetor jets and restores mileage after bad fuel.",
            priceInr = 180,
            buyUrl = null
        ),
        ToolData(
            name = "BIS-certified helmet",
            category = "Safety",
            description = "ISI-rated helmet that replaces a cracked or strap-broken one.",
            priceInr = 1800,
            buyUrl = null
        ),
        ToolData(
            name = "Reflective safety vest",
            category = "Safety",
            description = "High-visibility vest for night shifts and poor light roads.",
            priceInr = 350,
            buyUrl = null
        ),
        ToolData(
            name = "Riding gloves",
            category = "Safety",
            description = "Protects palms from heat, vibration and handlebar abrasions.",
            priceInr = 500,
            buyUrl = null
        ),
        ToolData(
            name = "Handlebar rain cover",
            category = "Safety",
            description = "Keeps grips and controls dry in heavy monsoon rain.",
            priceInr = 220,
            buyUrl = null
        ),
        ToolData(
            name = "Phone mount (handlebar)",
            category = "Electronics",
            description = "Holds the phone upright for navigation at a glance.",
            priceInr = 400,
            buyUrl = null
        ),
        ToolData(
            name = "Power bank 10000mAh",
            category = "Electronics",
            description = "Keeps the phone alive through long shifts without a socket.",
            priceInr = 1300,
            buyUrl = null
        ),
        ToolData(
            name = "Jump start cables",
            category = "Electrical",
            description = "Boots a dead bike battery using a working rider's bike.",
            priceInr = 700,
            buyUrl = null
        )
    )
}