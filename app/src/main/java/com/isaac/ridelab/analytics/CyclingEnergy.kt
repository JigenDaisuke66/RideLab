package com.isaac.ridelab.analytics

/** Rounded reference portions, not nutritional advice. Sources: RIDE_ENERGY_VERIFICATION.md. */
enum class EnergyFood(val kcalPerPortion: Double) {
    BANANA(105.0), BOILED_EGG(78.0), COOKED_RICE_CUP(242.0)
}

data class FoodEquivalent(val food: EnergyFood, val portions: Double)
data class CyclingEnergyEstimate(
    val kcal: Double,
    val lowerKcal: Double,
    val upperKcal: Double,
    val foodEquivalents: List<FoodEquivalent>,
)

/** Fixed model: metabolic kcal = recorded mechanical kJ / (4.184 * gross efficiency).
 * 24% is an explicit approximation, not a measured personal efficiency. The 20–25%
 * sensitivity range is not a confidence interval. No separate resting/coasting
 * expenditure is modelled. Existing work integration excludes missing data/gaps.
 */
object CyclingEnergyCalculator {
    private const val KJ_PER_KCAL = 4.184
    private const val ASSUMED_EFFICIENCY = 0.24

    fun estimate(workKilojoules: Double?): CyclingEnergyEstimate? {
        if (workKilojoules == null || !workKilojoules.isFinite() || workKilojoules < 0) return null
        val kcal = workKilojoules / (KJ_PER_KCAL * ASSUMED_EFFICIENCY)
        return CyclingEnergyEstimate(kcal,
            workKilojoules / (KJ_PER_KCAL * 0.25),
            workKilojoules / (KJ_PER_KCAL * 0.20),
            EnergyFood.entries.map { FoodEquivalent(it, kcal / it.kcalPerPortion) })
    }
}
