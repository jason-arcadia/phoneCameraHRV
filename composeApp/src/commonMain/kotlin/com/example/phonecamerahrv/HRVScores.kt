package com.example.phonecamerahrv

data class HRVScores(
    val stress: Int,        // 壓力: 0–100, higher = less stressed = better
    val energy: Int,        // 能量: 0–100, higher = more energy = better
    val recovery: Int,      // 恢復力: 0–100, higher = better recovery
    val rmssd: Double,
    val heartRate: Double,
    val lastRR: Double,
    val sampleCount: Int,   // number of historical records used for baseline
    val hasBaseline: Boolean // false if fewer than 5 prior readings
)

/**
 * Calculate scores relative to a personal baseline.
 *
 * When [hasBaseline] is true the [baseline] reflects the user's historical average,
 * so 100 % means "at your typical level".  When false the current reading is used as
 * the reference (first measurement), giving all scores near 100.
 *
 * Scores are capped at 100 so they read as clean percentages.
 */
fun calculateHRVScores(
    rmssd: Double,
    heartRate: Double,
    lastRR: Double,
    baseline: BaselineStats?,
    currentAsBaseline: BaselineStats  // fallback when no history yet
): HRVScores {
    val ref = baseline ?: currentAsBaseline
    val hasBaseline = baseline != null

    val refRmssd = ref.rmssd.coerceAtLeast(10.0)
    val refHR    = ref.heartRate.coerceAtLeast(40.0)

    // 壓力 (calm/stress): higher RMSSD vs baseline → less stress → higher score
    val stressScore = ((rmssd / refRmssd) * 100.0).coerceIn(0.0, 100.0).toInt()

    // 能量 (energy): RMSSD ratio (60 %) + how close HR is to your baseline HR (40 %)
    val rmssdRatio = (rmssd / refRmssd).coerceIn(0.0, 1.5) / 1.5
    val hrProximity = (1.0 - (kotlin.math.abs(heartRate - refHR) / refHR).coerceIn(0.0, 1.0))
    val energyScore = ((rmssdRatio * 0.6 + hrProximity * 0.4) * 100.0).coerceIn(0.0, 100.0).toInt()

    // 恢復力 (recovery/health): RMSSD vs baseline with a 130 % ceiling so small
    // improvements are visible without inflating the scale
    val recoveryScore = ((rmssd / refRmssd).coerceIn(0.0, 1.3) / 1.3 * 100.0)
        .coerceIn(0.0, 100.0).toInt()

    return HRVScores(
        stress      = stressScore,
        energy      = energyScore,
        recovery    = recoveryScore,
        rmssd       = rmssd,
        heartRate   = heartRate,
        lastRR      = lastRR,
        sampleCount = ref.sampleCount,
        hasBaseline = hasBaseline
    )
}

fun scoreInterpretation(label: String, score: Int): String = when (label) {
    "壓力" -> when {
        score >= 70 -> "壓力低，身心狀態良好"
        score >= 40 -> "壓力中等，注意適當休息"
        else        -> "壓力偏高，建議放鬆身心"
    }
    "能量" -> when {
        score >= 70 -> "精力充沛，狀態極佳"
        score >= 40 -> "能量適中，維持良好習慣"
        else        -> "能量不足，建議充分休息"
    }
    "恢復力" -> when {
        score >= 70 -> "恢復力強，自律神經平衡"
        score >= 40 -> "恢復力中等，持續觀察"
        else        -> "恢復力較弱，建議增加睡眠"
    }
    else -> ""
}
