package com.davidlang.vehicleexpensesautomated.ui.experiment

/** Trusted cost/vol seed boxes for the seed-ink probe (1-based harvest order). */
object SeedInkProbeKeep {
    private const val PACK = """
PXL_20220701_020625793.dng|aabb-tight:1,3|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20221020_220049868.dng|aabb-tight:1,3|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20221029_003255537.dng|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20221121_021250335.dng|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20221121_195449335.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20221126_021759797.dng|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20221126_021759797.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20221126_210421897.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20221128_172956178.dng|aabb-tight:1|aabb-large:1,4|rot-tight:1|rot-large:1
PXL_20221128_172956178.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20221221_210212750.dng|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20221222_211812872.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20221227_164720280.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20221228_165217774.dng|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20221230_182006230.dng|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20230101_055935720.dng|aabb-tight:2,3|aabb-large:2,3|rot-tight:2,3|rot-large:2,3
PXL_20230113_231616307.dng|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20230128_022551423.dng|aabb-tight:2,3|aabb-large:2,3|rot-tight:2,4|rot-large:2,4
PXL_20230206_014546051.dng|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20230225_040459673.dng|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20230318_232827961.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20230411_173710801.dng|aabb-tight:1|aabb-large:1|rot-tight:1|rot-large:1
PXL_20230411_215053637.jpg|aabb-tight:1,4|aabb-large:1,4|rot-tight:1,4|rot-large:1,4
PXL_20230414_023123861.dng|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20230520_194221428.dng|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20230520_194628805.dng|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20230612_180633478.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20230621_073220076.dng|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20230625_225655795.dng|aabb-tight:1,3|aabb-large:2,3|rot-tight:1,3|rot-large:1,3
PXL_20230705_105304742.dng|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20230714_203504848.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20230806_235553394.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20230827_000357771.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20230902_175948030.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20230924_205918100.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20231008_022308667.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20231025_020522463.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20231120_002742785.dng|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20231120_002920554.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20231120_210527402.dng|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20231127_200208050.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20231211_004047524.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20231221_210417588.jpg|aabb-tight:1,4|aabb-large:1,3|rot-tight:1,3|rot-large:1,3
PXL_20231221_213627643.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20231223_075001042.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20231226_184548623.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20231226_204458990.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20240101_025220348.NIGHT.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20240131_013430374.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20240228_211544792.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20240325_035731504.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20240326_014922448.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20240408_003215875.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20240414_010954673.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20240521_025057693.jpg|aabb-tight:1,3|aabb-large:1,3|rot-tight:1,2|rot-large:1,3
PXL_20240608_005034875.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20240630_011346680.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20240708_222637707.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20240718_000403216.jpg|aabb-tight:1,3|aabb-large:1,3|rot-tight:1,2|rot-large:1,2
PXL_20240722_200504113.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20240807_024257557.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20240808_211542775.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20241007_023801663.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20241104_014027473.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20241123_194900843.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20241127_072233060.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20241130_183108905.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20241202_143338144.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20241213_220345190.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20241222_024130766.jpg|aabb-tight:1|aabb-large:1|rot-tight:1|rot-large:1
PXL_20241222_085849852.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20241230_191439866.jpg|aabb-tight:2|aabb-large:2|rot-tight:2|rot-large:2
PXL_20250101_020218807.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20250202_042340658.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20250224_001547856.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20250303_172259346.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20250408_223113314.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20250415_213030478.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20250425_030838626.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20250426_024053319.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20250426_042852976.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20250426_084222634.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20250428_210041857.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20250501_084603153.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20250501_160616426.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20250503_222128862.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20250511_013436322.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20250516_042722105.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20250521_001522852.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20250528_213707194.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20250617_052502070.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20250626_205528017.jpg|aabb-tight:4,5|aabb-large:4,5|rot-tight:1,2|rot-large:1,2
PXL_20250702_022811999.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20250703_032207597.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20250706_185716830.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20250706_213056077.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20250709_003251317.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20250717_212509652.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20250726_211343400.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20250808_234426044.jpg|aabb-tight:1,6|aabb-large:1,5|rot-tight:1,3|rot-large:1,3
PXL_20250811_211835846.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20250814_231431109.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20250822_062416579.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20250823_031728508.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20250830_221843009.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20250906_001113787.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20250911_214550967.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20250926_022815882.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20250926_031353327.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20250926_215222541.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20250930_065746276.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20250930_091616986.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20251004_023935813.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20251016_032043998.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20251022_023545423.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20251029_033716937.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20251029_033721576.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20251103_024204090.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20251107_035310198.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20251107_064636287.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20251108_025727627.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20251108_025912900.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20251111_013744030.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20251111_071903596.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20251120_015319617.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20251220_040853040.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20251223_044233818.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20260114_020053675.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20260131_023636224.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20260131_030237621.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20260202_204443784.jpg|aabb-tight:1,3|aabb-large:1,3|rot-tight:1,2|rot-large:1,2
PXL_20260202_225555167.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20260214_204206758.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20260219_033516545.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20260219_050715169.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20260220_043453305.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20260220_061303418.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20260302_000113349.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20260311_180433036.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20260312_033525235.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20260411_201506380.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20260425_044615955.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20260425_062059015.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20260426_081506806.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20260531_181110716.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20260617_033252085.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20260626_042657943.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20260626_225456569.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20260702_220704854.jpg|aabb-tight:1|aabb-large:1|rot-tight:1|rot-large:1
PXL_20260703_031558607.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20260703_044940562.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20260706_190516439.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20260706_214711042.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20260707_041630487.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
PXL_20260710_005548570.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
fuel_1783829858019.jpg|aabb-tight:1,2|aabb-large:1,2|rot-tight:1,2|rot-large:1,2
"""

    private val KEEP: Map<String, Map<String, Set<Int>>> = parsePack(PACK)

    fun keepBoxes(file: String, flow: String): Set<Int> {
        val key = file.substringAfterLast('/')
        var flowKey = flow.removePrefix("Set ").trim()
        if (flowKey.endsWith("-color")) flowKey = flowKey.removeSuffix("-color")
        return KEEP[key]?.get(flowKey) ?: emptySet()
    }

    private fun parsePack(raw: String): Map<String, Map<String, Set<Int>>> {
        val out = HashMap<String, Map<String, Set<Int>>>()
        for (line in raw.lineSequence()) {
            val s = line.trim()
            if (s.isEmpty()) continue
            val parts = s.split('|')
            if (parts.size < 2) continue
            val flows = HashMap<String, Set<Int>>()
            for (i in 1 until parts.size) {
                val p = parts[i]
                val colon = p.indexOf(':')
                if (colon <= 0) continue
                val fk = p.substring(0, colon)
                val boxes = p.substring(colon + 1).split(',')
                    .mapNotNull { it.trim().toIntOrNull() }
                    .toSet()
                flows[fk] = boxes
            }
            out[parts[0]] = flows
        }
        return out
    }
}
