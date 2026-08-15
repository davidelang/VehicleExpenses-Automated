package com.davidlang.vehicleexpensesautomated.ui.experiment

import java.io.File

/**
 * Coverage subset for experiment "Selected sample" buttons.
 *
 * Source: gallery built from complete pump run 2026-08-08_14-45-15 + alignment
 * 2026-08-06_07-47-54, using [ground_truth_pump.json] / [ground_truth_odo.json]
 * (same as pump_deep_analysis / deep_analysis). Sorted small→large text (pump) /
 * low→high raw_scale (dash). Cost **or** vol detection is enough for pump size.
 *
 * Host gallery:
 * `dev-ai-interaction/latest-report/sample_gallery_pump_dash_20260810/`
 *
 * Use the domain-appropriate list only (no dash names on pump experiments).
 */
object SelectedSamplePhotos {

    /** Pump coverage set (34): small→large cost/vol text + energy-trace extras. */
    val PUMP: List<String> = listOf(
        "fuel_1783829858019.jpg",
        "PXL_20230705_105304742.dng",
        "PXL_20230430_042620930.dng",
        "PXL_20230902_175803321.dng",
        "PXL_20231221_210417588.jpg",
        "PXL_20221230_182006230.dng",
        "PXL_20230101_055935720.dng",
        "PXL_20240718_000403216.jpg",
        "PXL_20230520_194628805.dng",
        "PXL_20240808_211542775.jpg",
        "PXL_20230414_023123861.dng",
        "PXL_20250426_042852976.jpg",
        "PXL_20240521_025057693.jpg",
        "PXL_20250626_205528017.jpg",
        "PXL_20230625_225655795.dng",
        "PXL_20230621_073220076.dng",
        "PXL_20221126_210421897.dng",
        "PXL_20221228_165217774.dng",
        "PXL_20221020_220049868.dng",
        "PXL_20241222_024130766.jpg",
        "PXL_20231120_210527402.dng",
        "PXL_20221221_210212750.dng",
        "PXL_20220701_020625793.dng",
        "PXL_20231120_002742785.dng",
        "PXL_20231008_001628193.dng",
        // Full-run pump line 10 (same capture as line 11 .jpg); was missing from coverage set.
        "PXL_20221128_172956178.dng",
        // Line 11: jpeg twin of line 10 (7-seg half-digit). Energy-trace extras:
        "PXL_20221128_172956178.jpg",
        "PXL_20241213_220345190.jpg",
        "PXL_20250709_003251317.jpg",
        "PXL_20250830_221843009.jpg",
        "PXL_20260411_201506380.jpg",
        "PXL_20230902_175948030.jpg",
        "PXL_20221227_164720280.jpg",
        "PXL_20250811_211835846.jpg",
    )

    /** Dash coverage set (~22): low→high alignment raw_scale (zoom). */
    val DASH: List<String> = listOf(
        "PXL_20231226_204408016.jpg",
        "PXL_20230625_225229998.dng",
        "PXL_20221121_195143240.dng",
        "PXL_20250521_001438093.jpg",
        "PXL_20221227_164550455.jpg",
        "PXL_20230318_232803060.dng",
        "PXL_20230113_231330881.dng",
        "PXL_20231120_210311358.dng",
        "PXL_20241123_194434912.jpg",
        "PXL_20230225_040513730.jpg",
        "PXL_20250709_002855745.jpg",
        "PXL_20230520_193148871.dng",
        "PXL_20240722_200247194.jpg",
        "PXL_20231008_001502088.dng",
        "PXL_20221221_205939873.dng",
        "PXL_20230411_215003744.dng",
        "PXL_20230827_000020282.dng",
        "PXL_20221020_215546513.dng",
        "PXL_20220701_020707365.dng",
        "PXL_20220821_051055938.dng",
        "PXL_20221029_002946498.dng",
        "PXL_20240717_235836312.jpg",
    )

    /** Pump + dash basenames for multi-domain experiments (no expense). */
    val PUMP_AND_DASH: List<String> = PUMP + DASH

    /**
     * Seeded expense receipt used by multi-scale det (APK asset → expense_photos/).
     * Same basename as [MultiScaleDetRunner] EXPENSE_BASENAME.
     */
    val EXPENSE: List<String> = listOf(
        "PXL_20260809_094107925.jpg",
    )

    /**
     * Multi-scale det "Selected sample": coverage pump+dash **plus** expense seed
     * (expense exercises the higher heatmap box cap).
     */
    val MULTI_SCALE: List<String> = PUMP + DASH + EXPENSE

    private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "dng")

    fun isImageFile(f: File): Boolean =
        f.isFile && f.extension.lowercase() in IMAGE_EXTS

    /**
     * Names from [want] that exist under [dir], preserving [want] order.
     * Exact filename match only (same as other experiment subset buttons).
     */
    fun presentInOrder(dir: File, want: List<String>): List<String> {
        val onDevice = dir.listFiles { f -> isImageFile(f) }
            ?.map { it.name }
            ?.toSet()
            ?: emptySet()
        return want.filter { it in onDevice }
    }

    fun presentCount(dir: File, want: List<String>): Pair<Int, Int> {
        val n = presentInOrder(dir, want).size
        return n to want.size
    }

    /** Line-number map for alignment reports (1-based in list order among present). */
    fun subsetMapPresent(dir: File, want: List<String>): Map<String, Int> {
        return presentInOrder(dir, want).mapIndexed { i, name -> name to (i + 1) }.toMap()
    }
}
