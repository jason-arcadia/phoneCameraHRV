package com.example.phonecamerahrv

data class HRVScores(
    val stress: Int,    // 壓力: 0–100, higher = less stressed = better
    val energy: Int,    // 能量: 0–100, higher = more energy = better
    val recovery: Int,  // 恢復力: 0–100, higher = better recovery
    val rmssd: Double,
    val heartRate: Double
)

fun calculateHRVScores(
    rmssd: Double,
    heartRate: Double,
    baselineRmssd: Double
): HRVScores {
    val ref = baselineRmssd.coerceAtLeast(20.0)

    // 壓力: RMSSD ratio to baseline; higher RMSSD → less stress → higher score
    val stressScore = ((rmssd / ref) * 100.0).coerceIn(0.0, 100.0).toInt()

    // 能量: HR in healthy zone + RMSSD relative to baseline
    val hrFactor = when {
        heartRate <= 0 -> 0.5
        heartRate in 55.0..75.0 -> 1.0
        heartRate < 55.0 -> 0.8
        heartRate <= 90.0 -> 0.8 - (heartRate - 75.0) / 75.0
        else -> 0.3
    }.coerceIn(0.0, 1.0)
    val rmssdFactor = (rmssd / ref).coerceIn(0.0, 1.5) / 1.5
    val energyScore = ((hrFactor * 0.4 + rmssdFactor * 0.6) * 100.0).coerceIn(0.0, 100.0).toInt()

    // 恢復力: RMSSD vs personal baseline percentile
    val recoveryScore = ((rmssd / ref).coerceIn(0.0, 1.5) / 1.5 * 100.0).coerceIn(0.0, 100.0).toInt()

    return HRVScores(stressScore, energyScore, recoveryScore, rmssd, heartRate)
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
