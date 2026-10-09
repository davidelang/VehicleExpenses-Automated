package com.davidlang.vehicleexpensesautomated.ui.util

import android.graphics.Rect
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * Quick Fill copy of the pump label chooser.
 * This is the Quick Fill copy and it does not call the experiment chooser.
 */
object QuickFillPumpCostVolChooser {

    private val emptyCand = RedBoxOcrCandidate("", "", "")

    private val volumeRuns = setOf(
        "gal", "gallon", "gallons", "liter", "litre", "liters", "ltr",
    )

    private enum class Role { COST, VOL }

    private data class Readings(val cost: String?, val vol: String?, val partial: String? = null)

    private data class Parsed(
        val index: Int,
        val cand: RedBoxOcrCandidate,
        val digits: String,
        val readings: Readings?,
        val lockableCost: String?,
        val costInstr: Boolean,
        val volInstr: Boolean,
    )

    private data class Lock(val box: Parsed, val sameString: Boolean, val dist: Int)

    fun classify(candidates: List<RedBoxOcrCandidate>): CostVolClassifyResult {
        if (candidates.isEmpty()) return done("N/A", "N/A", null, null)
        val parsed = candidates.mapIndexed { i, c -> parse(i, c) }
        var costLock = pickLock(parsed, Role.COST)
        var volLock = pickLock(parsed, Role.VOL)
        if (costLock != null && volLock != null && costLock.box.index == volLock.box.index) {
            when {
                costLock.sameString && !volLock.sameString -> volLock = null
                volLock.sameString && !costLock.sameString -> costLock = null
                volLock.dist < costLock.dist -> costLock = null
                else -> volLock = null
            }
        }
        if (costLock != null && volLock != null) {
            val cs = roleString(costLock.box, Role.COST, costLocked = true)
            val vs = roleString(volLock.box, Role.VOL, costLocked = false)
            if (cs != null || vs != null) {
                return done(cs ?: "N/A", vs ?: "N/A", costLock.box.cand, volLock.box.cand)
            }
        }
        if (costLock != null && volLock == null) {
            return oneLocked(parsed, costLock.box, Role.COST)
        }
        if (volLock != null && costLock == null) {
            return oneLocked(parsed, volLock.box, Role.VOL)
        }
        return unlocked(parsed)
    }

    private fun oneLocked(parsed: List<Parsed>, locked: Parsed, role: Role): CostVolClassifyResult {
        val open = if (role == Role.COST) Role.VOL else Role.COST
        val lockedText = roleString(locked, role, costLocked = role == Role.COST)
        val other = otherInColumn(parsed, locked, open)
        val otherText = other?.let { roleString(it, open, costLocked = false) }
        val costText: String
        val volText: String
        val costCand: RedBoxOcrCandidate?
        val volCand: RedBoxOcrCandidate?
        if (role == Role.COST) {
            costText = lockedText ?: "N/A"
            volText = otherText ?: "N/A"
            costCand = locked.cand
            volCand = other?.cand
        } else {
            costText = otherText ?: "N/A"
            volText = lockedText ?: "N/A"
            costCand = other?.cand
            volCand = locked.cand
        }
        if (costText == "N/A" && volText == "N/A") return unlocked(parsed)
        return done(costText, volText, costCand, volCand)
    }

    private fun unlocked(parsed: List<Parsed>): CostVolClassifyResult {
        val amounts = parsed.filter { it.readings != null }
        if (amounts.isEmpty()) return done("N/A", "N/A", null, null)
        if (amounts.size == 1) {
            val a = amounts[0]
            val r = a.readings!!
            return when {
                r.cost != null && r.vol == null -> done(r.cost, "N/A", a.cand, null)
                r.vol != null && r.cost == null -> done("N/A", r.vol, null, a.cand)
                r.cost != null -> done(r.cost, "N/A", a.cand, null)
                else -> done("N/A", "N/A", null, null)
            }
        }
        val costOnly = amounts.filter { it.readings!!.cost != null && it.readings.vol == null }
        val volOnly = amounts.filter { it.readings!!.vol != null && it.readings.cost == null }
        if (costOnly.isNotEmpty() && volOnly.isNotEmpty()) {
            val pair = bestReadPair(costOnly, volOnly)
            return done(pair.first.readings!!.cost!!, pair.second.readings!!.vol!!, pair.first.cand, pair.second.cand)
        }
        val sorted = amounts.sortedWith(compareBy({ centerY(it) }, { it.index }))
        val upper = sorted[0]
        val lower = sorted[1]
        val cs = roleString(upper, Role.COST, costLocked = false) ?: "N/A"
        val vs = roleString(lower, Role.VOL, costLocked = false) ?: "N/A"
        if (cs == "N/A" && vs == "N/A") {
            val r = upper.readings
            if (r?.cost != null) return done(r.cost, "N/A", upper.cand, null)
            if (r?.vol != null) return done("N/A", r.vol, null, upper.cand)
        }
        return done(cs, vs, upper.cand, lower.cand)
    }

    private fun bestReadPair(costs: List<Parsed>, vols: List<Parsed>): Pair<Parsed, Parsed> {
        var bestC = costs[0]
        var bestV = vols[0]
        var bestRank = 3
        var bestDist = Int.MAX_VALUE
        var bestCi = Int.MAX_VALUE
        var bestVi = Int.MAX_VALUE
        for (c in costs) {
            for (v in vols) {
                val col = sharesColumn(c, v)
                val rank = if (col) 0 else 1
                val dist = yDist(c, v)
                val better = rank < bestRank ||
                    (rank == bestRank && dist < bestDist) ||
                    (rank == bestRank && dist == bestDist && c.index < bestCi) ||
                    (rank == bestRank && dist == bestDist && c.index == bestCi && v.index < bestVi)
                if (better) {
                    bestRank = rank
                    bestDist = dist
                    bestCi = c.index
                    bestVi = v.index
                    bestC = c
                    bestV = v
                }
            }
        }
        return bestC to bestV
    }

    private fun otherInColumn(parsed: List<Parsed>, locked: Parsed, open: Role): Parsed? {
        val lockedRole = if (open == Role.COST) Role.VOL else Role.COST
        val pool = parsed.filter { p ->
            p.index != locked.index &&
                p.readings != null &&
                sharesColumn(locked, p) &&
                roleString(p, open, costLocked = false) != null
        }
        if (pool.isEmpty()) return null
        val notStolen = pool.filter { !hasNatural(it, lockedRole) }
        val eligible = if (notStolen.isNotEmpty()) notStolen else pool
        val withReading = eligible.filter { hasNatural(it, open) }
        val pick = if (withReading.isNotEmpty()) withReading else eligible
        return bestByReadScore(pick)
    }

    private fun hasNatural(p: Parsed, role: Role): Boolean {
        val r = p.readings ?: return false
        return if (role == Role.COST) r.cost != null else r.vol != null
    }

    private fun pickLock(parsed: List<Parsed>, role: Role): Lock? {
        var best: Lock? = null
        for (p in parsed) {
            val wantCost = role == Role.COST
            val has = if (wantCost) p.costInstr else p.volInstr
            val other = if (wantCost) p.volInstr else p.costInstr
            if (!has || other) continue
            if (canTake(p, role, costLocked = wantCost)) {
                val lock = Lock(p, sameString = true, dist = 0)
                best = preferLock(best, lock)
                continue
            }
            if (p.readings != null) continue
            val target = nearestTarget(parsed, p, role) ?: continue
            val dist = if (sameLine(p, target)) xDist(p, target) else yDist(p, target)
            best = preferLock(best, Lock(target, sameString = false, dist = dist))
        }
        return best
    }

    private fun preferLock(current: Lock?, next: Lock): Lock {
        if (current == null) return next
        if (next.sameString != current.sameString) {
            return if (next.sameString) next else current
        }
        if (next.dist != current.dist) return if (next.dist < current.dist) next else current
        if (centerY(next.box) != centerY(current.box)) {
            return if (centerY(next.box) < centerY(current.box)) next else current
        }
        return if (next.box.index < current.box.index) next else current
    }

    private fun nearestTarget(parsed: List<Parsed>, instr: Parsed, role: Role): Parsed? {
        val pool = parsed.filter { p ->
            p.index != instr.index && canTake(p, role, costLocked = role == Role.COST)
        }
        if (pool.isEmpty() || instr.cand.rect == null) return null
        val line = pool.filter { sameLine(instr, it) }
        if (line.isNotEmpty()) return bestByReadScore(line)
        val col = pool.filter { aboveOrBelow(instr, it) }
        if (col.isEmpty()) return null
        val clusters = lineClusters(col)
        val closest = clusters.minWith(
            compareBy<List<Parsed>>(
                { cluster -> cluster.minOf { yDist(instr, it) } },
                { cluster -> cluster.minBy { yDist(instr, it) }.index },
            ),
        )
        return bestByReadScore(closest)
    }

    private fun bestByReadScore(boxes: List<Parsed>): Parsed =
        boxes.maxWith(compareBy({ readScore(it) }, { -it.index }))

    private fun readScore(p: Parsed): Float {
        val mean = PumpCostVolUtils.probCorrectness(p.cand.digitsProbs)
        return if ('.' in p.cand.digits) mean + 0.25f else mean
    }

    private fun lineClusters(boxes: List<Parsed>): List<List<Parsed>> {
        val n = boxes.size
        val parent = IntArray(n) { it }
        fun find(i: Int): Int {
            var x = i
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]
                x = parent[x]
            }
            return x
        }
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                if (sameLine(boxes[i], boxes[j])) parent[find(i)] = find(j)
            }
        }
        val groups = LinkedHashMap<Int, MutableList<Parsed>>()
        for (i in 0 until n) {
            groups.getOrPut(find(i)) { ArrayList() }.add(boxes[i])
        }
        return groups.values.toList()
    }

    private fun canTake(p: Parsed, role: Role, costLocked: Boolean): Boolean {
        if (role == Role.COST) {
            if (blocksAlphabetDollarCost(p)) return false
            if (p.readings != null && roleString(p, Role.COST, costLocked) != null) return true
            return costLocked && p.lockableCost != null
        }
        return p.readings != null && roleString(p, Role.VOL, costLocked = false) != null
    }

    private fun blocksAlphabetDollarCost(p: Parsed): Boolean {
        val r = p.readings ?: return false
        if (r.cost != null || r.vol == null) return false
        if ('$' in p.cand.digits) return false
        return '$' in p.cand.asis
    }

    private fun roleString(p: Parsed, role: Role, costLocked: Boolean): String? {
        val digits = p.digits
        if (digits.length < 3) return null
        if (role == Role.COST && blocksAlphabetDollarCost(p)) return null
        val r = p.readings
        if (role == Role.COST) {
            r?.cost?.let { return it }
            r?.partial?.let { return it }
            val maxDigits = if (costLocked) 9 else 6
            if (digits.length in 3..maxDigits) return place(digits, 2)
            return null
        }
        r?.vol?.let { return it }
        r?.partial?.let { return it }
        if (digits.length in 4..6) return place(digits, 3)
        return null
    }

    private fun parse(index: Int, cand: RedBoxOcrCandidate): Parsed {
        val (costInstr, volInstr) = instructions(cand.asis, cand.digits)
        val numeric = amountNumeric(cand)
        if (numeric == null) {
            return Parsed(index, cand, "", null, null, costInstr, volInstr)
        }
        val digits = digitsOnly(numeric)
        val dots = numeric.count { it == '.' }
        val readings = readingsOf(numeric)
        val lockable = if (dots == 0 && digits.length in 7..9) place(digits, 2) else null
        return Parsed(index, cand, digits, readings, lockable, costInstr, volInstr)
    }

    private fun amountNumeric(c: RedBoxOcrCandidate): String? {
        if ((':' in c.asis || '/' in c.asis) && !hasAmountWord(c.asis)) return null
        val fromDigits = dotsAndDigits(c.digits)
        val dig = digitsOnly(fromDigits)
        if (dig.length < 3) return null
        if ('.' !in fromDigits && c.asis.count { it == '.' } == 1 && digitsOnly(c.asis) == dig) {
            val adopted = dotsAndDigits(c.asis)
            if (digitsOnly(adopted) == dig && adopted.count { it == '.' } == 1) return adopted
        }
        return fromDigits
    }

    private fun readingsOf(raw: String): Readings? {
        val digits = digitsOnly(raw)
        if (digits.length < 3) return null
        val dots = raw.count { it == '.' }
        if (dots >= 3) return null
        if (dots == 2) {
            val a = oneDotReading(deleteDot(raw, 0))
            val b = oneDotReading(deleteDot(raw, 1))
            val cost = a.cost ?: b.cost
            val vol = a.vol ?: b.vol
            if (cost == null && vol == null) return null
            return Readings(cost, vol)
        }
        if (dots == 1) {
            val r = oneDotReading(raw)
            if (r.cost == null && r.vol == null && r.partial == null) return null
            return r
        }
        if (digits.length > 6) return null
        val cost = place(digits, 2)
        val vol = if (digits.length >= 4) place(digits, 3) else null
        return Readings(cost, vol)
    }

    private fun oneDotReading(raw: String): Readings {
        val dot = raw.indexOf('.')
        if (dot < 0) return Readings(null, null, null)
        val digits = digitsOnly(raw)
        val places = digitsOnly(raw.substring(dot + 1)).length
        val cost = if (places == 2 && digits.length >= 3) raw else null
        val vol = if (places == 3 && digits.length in 4..6) raw else null
        val partial = if (
            cost == null && vol == null && digits.length >= 3 && places != 2 && places != 3
        ) {
            raw
        } else {
            null
        }
        return Readings(cost, vol, partial)
    }

    private fun hasAmountWord(asis: String): Boolean {
        var i = 0
        while (i < asis.length) {
            if (!asis[i].isLetter()) {
                i++
                continue
            }
            val start = i
            while (i < asis.length && asis[i].isLetter()) i++
            val run = asis.substring(start, i).lowercase()
            if (run == "cost" || run == "sale" || run.endsWith("sale")) return true
            if (run in volumeRuns || run.endsWith("ons")) return true
        }
        return false
    }

    private fun deleteDot(raw: String, which: Int): String {
        var seen = 0
        return buildString {
            for (ch in raw) {
                if (ch == '.') {
                    if (seen == which) {
                        seen++
                        continue
                    }
                    seen++
                }
                append(ch)
            }
        }
    }

    private fun place(digits: String, places: Int): String {
        val n = digits.length
        return digits.substring(0, n - places) + "." + digits.substring(n - places)
    }

    private fun instructions(asis: String, digits: String): Pair<Boolean, Boolean> {
        var cost = '$' in digits
        var vol = false
        var i = 0
        while (i < asis.length) {
            val ch = asis[i]
            if (ch == '$') {
                cost = true
                i++
                continue
            }
            if (ch.isLetter()) {
                val start = i
                while (i < asis.length && asis[i].isLetter()) i++
                val run = asis.substring(start, i).lowercase()
                if (run == "cost" || run == "sale" || run.endsWith("sale")) cost = true
                if (run in volumeRuns || run.endsWith("ons")) vol = true
                continue
            }
            i++
        }
        return cost to vol
    }

    private fun sameLine(a: Parsed, b: Parsed): Boolean {
        val ra = a.cand.rect ?: return false
        val rb = b.cand.rect ?: return false
        return overlapAtLeast(yOverlap(ra, rb), min(ra.height(), rb.height()), 45)
    }

    private fun sharesColumn(a: Parsed, b: Parsed): Boolean {
        val ra = a.cand.rect ?: return false
        val rb = b.cand.rect ?: return false
        return overlapAtLeast(xOverlap(ra, rb), min(ra.width(), rb.width()), 25)
    }

    private fun aboveOrBelow(a: Parsed, b: Parsed): Boolean = sharesColumn(a, b) && !sameLine(a, b)

    private fun overlapAtLeast(overlap: Int, span: Int, percent: Int): Boolean {
        val denom = if (span > 0) span else 1
        return overlap * 100 >= denom * percent
    }

    private fun yOverlap(a: Rect, b: Rect): Int {
        val top = if (a.top > b.top) a.top else b.top
        val bot = if (a.bottom < b.bottom) a.bottom else b.bottom
        return if (bot > top) bot - top else 0
    }

    private fun xOverlap(a: Rect, b: Rect): Int {
        val left = if (a.left > b.left) a.left else b.left
        val right = if (a.right < b.right) a.right else b.right
        return if (right > left) right - left else 0
    }

    private fun centerY(p: Parsed): Int {
        val r = p.cand.rect ?: return Int.MAX_VALUE
        return (r.top + r.bottom) / 2
    }

    private fun yDist(a: Parsed, b: Parsed): Int {
        if (a.cand.rect == null || b.cand.rect == null) return Int.MAX_VALUE / 4
        return abs(centerY(a) - centerY(b))
    }

    private fun xDist(a: Parsed, b: Parsed): Int {
        val ra = a.cand.rect ?: return Int.MAX_VALUE / 4
        val rb = b.cand.rect ?: return Int.MAX_VALUE / 4
        val ax = (ra.left + ra.right) / 2
        val bx = (rb.left + rb.right) / 2
        return abs(ax - bx)
    }

    private fun digitsOnly(s: String): String = buildString {
        for (ch in s) if (ch.isDigit()) append(ch)
    }

    private fun dotsAndDigits(s: String): String = buildString {
        for (ch in s) if (ch.isDigit() || ch == '.') append(ch)
    }

    private fun done(
        cost: String,
        vol: String,
        costCand: RedBoxOcrCandidate?,
        volCand: RedBoxOcrCandidate?,
    ): CostVolClassifyResult {
        val cOk = cost.isNotEmpty() && cost != "N/A"
        val vOk = vol.isNotEmpty() && vol != "N/A"
        return CostVolClassifyResult(
            if (cOk) cost else "N/A",
            if (vOk) vol else "N/A",
            if (cOk) costCand ?: emptyCand else emptyCand,
            if (vOk) volCand ?: emptyCand else emptyCand,
        )
    }

    class WeightedCostVol(
        val cost: String,
        val vol: String,
        val json: String,
        val costCand: RedBoxOcrCandidate,
        val volCand: RedBoxOcrCandidate,
    )

    fun classifyWeighted(
        candidates: List<RedBoxOcrCandidate>,
        quads: List<ContentExpandUtils.OrientedQuad>,
        imgW: Int,
    ): WeightedCostVol {
        class Frame(
            val ux: Float,
            val uy: Float,
            val vx: Float,
            val vy: Float,
            val cx: Float,
            val cy: Float,
            val u0: Float,
            val u1: Float,
            val v0: Float,
            val v1: Float,
        )

        class Hint(val name: String, val weight: Float, val role: String, val from: Int)

        class Box(
            val index: Int,
            val cand: RedBoxOcrCandidate,
            var numeric: String?,
            var costLabel: Boolean,
            val volLabel: Boolean,
            val frame: Frame?,
            val pts: FloatArray?,
            val hints: MutableList<Hint> = ArrayList(),
            var costTotal: Float = 0f,
            var volTotal: Float = 0f,
        )

        val costEdge = listOf("sale", "cost", "purchase")
        val volEdge = listOf("gallon", "gallons", "liter", "litre", "liters", "litres")

        // u = edge closest to horizontal; +u right; +v down. Copied here, not shared.
        fun frameOf(pts: FloatArray): Frame? {
            if (pts.size < 8) return null
            var bestAng = 1e9
            var bestLen = 0.0
            var ux = 1f
            var uy = 0f
            for (i in 0 until 4) {
                val j = (i + 1) % 4
                val dx = (pts[j * 2] - pts[i * 2]).toDouble()
                val dy = (pts[j * 2 + 1] - pts[i * 2 + 1]).toDouble()
                val len = hypot(dx, dy)
                if (len < 1e-3) continue
                var ang = abs(atan2(dy, dx))
                if (ang > Math.PI / 2.0) ang = Math.PI - ang
                val closer = ang < bestAng - 1e-6
                val tieLonger = abs(ang - bestAng) <= 1e-6 && len > bestLen
                if (closer || tieLonger) {
                    bestAng = ang
                    bestLen = len
                    ux = (dx / len).toFloat()
                    uy = (dy / len).toFloat()
                }
            }
            if (bestLen < 2.0) return null
            var vx = -uy
            var vy = ux
            if (vy < 0f) {
                vx = -vx
                vy = -vy
            }
            var cx = 0f
            var cy = 0f
            for (i in 0 until 4) {
                cx += pts[i * 2]
                cy += pts[i * 2 + 1]
            }
            cx /= 4f
            cy /= 4f
            var u0 = Float.POSITIVE_INFINITY
            var u1 = Float.NEGATIVE_INFINITY
            var v0 = Float.POSITIVE_INFINITY
            var v1 = Float.NEGATIVE_INFINITY
            for (i in 0 until 4) {
                val dx = pts[i * 2] - cx
                val dy = pts[i * 2 + 1] - cy
                val u = dx * ux + dy * uy
                val v = dx * vx + dy * vy
                if (u < u0) u0 = u
                if (u > u1) u1 = u
                if (v < v0) v0 = v
                if (v > v1) v1 = v
            }
            if (u1 - u0 < 2f || v1 - v0 < 2f) return null
            if (ux < 0f) {
                ux = -ux
                uy = -uy
                val t = u0
                u0 = -u1
                u1 = -t
            }
            return Frame(ux, uy, vx, vy, cx, cy, u0, u1, v0, v1)
        }

        fun project(frame: Frame, pts: FloatArray): FloatArray {
            var pu0 = Float.POSITIVE_INFINITY
            var pu1 = Float.NEGATIVE_INFINITY
            var pv0 = Float.POSITIVE_INFINITY
            var pv1 = Float.NEGATIVE_INFINITY
            val n = min(4, pts.size / 2)
            for (i in 0 until n) {
                val dx = pts[i * 2] - frame.cx
                val dy = pts[i * 2 + 1] - frame.cy
                val u = dx * frame.ux + dy * frame.uy
                val v = dx * frame.vx + dy * frame.vy
                if (u < pu0) pu0 = u
                if (u > pu1) pu1 = u
                if (v < pv0) pv0 = v
                if (v > pv1) pv1 = v
            }
            return floatArrayOf(pu0, pu1, pv0, pv1)
        }

        fun overlapLen(a0: Float, a1: Float, b0: Float, b1: Float): Float {
            val left = if (a0 > b0) a0 else b0
            val right = if (a1 < b1) a1 else b1
            return if (right > left) right - left else 0f
        }

        fun overlapAtLeastF(overlap: Float, span: Float, percent: Int): Boolean {
            val denom = if (span > 0f) span else 1f
            return overlap * 100f >= denom * percent
        }

        fun vOverlap(frame: Frame, pts: FloatArray): Boolean {
            val iv = project(frame, pts)
            val overlap = overlapLen(frame.v0, frame.v1, iv[2], iv[3])
            val span = min(frame.v1 - frame.v0, iv[3] - iv[2])
            return overlapAtLeastF(overlap, span, 45)
        }

        fun uOverlap(frame: Frame, pts: FloatArray): Boolean {
            val iv = project(frame, pts)
            val overlap = overlapLen(frame.u0, frame.u1, iv[0], iv[1])
            val span = min(frame.u1 - frame.u0, iv[1] - iv[0])
            return overlapAtLeastF(overlap, span, 25)
        }

        fun sameLine(a: Box, b: Box): Boolean {
            val pa = a.pts
            val pb = b.pts
            val fa = a.frame
            val fb = b.frame
            if (pa == null || pb == null || fa == null || fb == null) return false
            return vOverlap(fa, pb) || vOverlap(fb, pa)
        }

        fun sharesColumn(a: Box, b: Box): Boolean {
            val pa = a.pts
            val pb = b.pts
            val fa = a.frame
            val fb = b.frame
            if (pa == null || pb == null || fa == null || fb == null) return false
            return uOverlap(fa, pb) || uOverlap(fb, pa)
        }

        // Two number boxes on one display. 0.75 of the smaller uv area.
        // Row 63 covers about 0.84. Two real fields on 22-37-22 stayed at or under 0.50.
        fun heavyOverlap(a: Box, b: Box): Boolean {
            val pa = a.pts
            val pb = b.pts
            val fa = a.frame
            val fb = b.frame
            if (pa == null || pb == null || fa == null || fb == null) return false
            fun cover(frame: Frame, other: FloatArray): Float {
                val p = project(frame, other)
                val area = overlapLen(frame.u0, frame.u1, p[0], p[1]) *
                    overlapLen(frame.v0, frame.v1, p[2], p[3])
                val own = (frame.u1 - frame.u0) * (frame.v1 - frame.v0)
                val proj = (p[1] - p[0]) * (p[3] - p[2])
                val smaller = if (own < proj) own else proj
                if (smaller <= 0f) return 0f
                return area / smaller
            }
            val ab = cover(fa, pb)
            val ba = cover(fb, pa)
            val greater = if (ab > ba) ab else ba
            return greater >= 0.75f
        }

        fun shortAxis(pts: FloatArray?): Float? {
            if (pts == null || pts.size < 6) return null
            val d01 = hypot((pts[2] - pts[0]).toDouble(), (pts[3] - pts[1]).toDouble())
            val d12 = hypot((pts[4] - pts[2]).toDouble(), (pts[5] - pts[3]).toDouble())
            return min(d01, d12).toFloat()
        }

        fun labelVFraction(frame: Frame, labelPts: FloatArray, digitPts: FloatArray): Float {
            val lab = project(frame, labelPts)
            val dig = project(frame, digitPts)
            val extent = lab[3] - lab[2]
            if (extent <= 0f) return 0f
            val overlap = overlapLen(lab[2], lab[3], dig[2], dig[3])
            val frac = overlap / extent
            return when {
                frac < 0f -> 0f
                frac > 1f -> 1f
                else -> frac
            }
        }

        fun labelCoveredFraction(label: Box, digit: Box): Float {
            val labelPts = label.pts ?: return 0f
            val digitPts = digit.pts ?: return 0f
            val labelFrame = label.frame ?: return 0f
            val digitFrame = digit.frame ?: return 0f
            return min(
                labelVFraction(labelFrame, labelPts, digitPts),
                labelVFraction(digitFrame, labelPts, digitPts),
            )
        }

        fun remnant(run: String, words: List<String>): Boolean {
            if (run.length < 2) return false
            for (w in words) {
                if (w.startsWith(run) || w.endsWith(run)) return true
            }
            return false
        }

        fun labelRoles(c: RedBoxOcrCandidate): Pair<Boolean, Boolean> {
            var cost = false
            var vol = false
            val rect = c.rect
            val leftEdge = rect != null && rect.left <= 8
            val rightEdge = rect != null && rect.right >= imgW - 8
            val edge = leftEdge || rightEdge
            fun edgeDropped(run: String, words: List<String>): Boolean {
                if (leftEdge && run.length > 1 && remnant(run.substring(1), words)) return true
                if (rightEdge && run.isNotEmpty() && remnant(run.substring(0, run.length - 1), words)) {
                    return true
                }
                return false
            }
            var i = 0
            val asis = c.asis
            while (i < asis.length) {
                if (!asis[i].isLetter()) {
                    i++
                    continue
                }
                val start = i
                while (i < asis.length && asis[i].isLetter()) i++
                val run = asis.substring(start, i).lowercase()
                if (
                    run == "cost" || run == "sale" || run == "purchase" ||
                    run.endsWith("cost") || run.endsWith("sale") || run.endsWith("purchase")
                ) {
                    cost = true
                }
                // Do not treat a bare "ons" ending as gallons. Full words in volumeRuns match
                // anywhere. Against the image edge, a run of 2+ letters may be a prefix or
                // suffix of volEdge, and the one letter touching the edge may be dropped
                // first (Sallons -> allons, a suffix of gallons). The old endsWith("ons")
                // also matched interior corruption on non-7-segment receipts (12.441callons
                // and five siblings). Those rows already showed a decimal on the gallons
                // read and a $ on the sale read, so the word was only a third confirmation.
                // Restore a narrow endsWith("ons") if a future interior callons is the only
                // gallons evidence and the decimal was missed.
                if (run in volumeRuns) vol = true
                if (edge && remnant(run, costEdge)) cost = true
                if (edge && remnant(run, volEdge)) vol = true
                if (edgeDropped(run, costEdge)) cost = true
                if (edgeDropped(run, volEdge)) vol = true
            }
            return cost to vol
        }

        val boxes = ArrayList<Box>(candidates.size)
        for (i in candidates.indices) {
            val c = candidates[i]
            val pts = quads.getOrNull(i)?.pts
            val frame = if (pts != null && pts.size >= 8) frameOf(pts) else null
            val roles = labelRoles(c)
            boxes.add(
                Box(
                    index = i,
                    cand = c,
                    numeric = amountNumeric(c),
                    costLabel = roles.first,
                    volLabel = roles.second,
                    frame = frame,
                    pts = if (pts != null && pts.size >= 8) pts else null,
                ),
            )
        }

        fun addHint(box: Box, name: String, weight: Float, role: String, from: Int) {
            box.hints.add(Hint(name, weight, role, from))
            when (role) {
                "cost" -> box.costTotal += weight
                "volume" -> box.volTotal += weight
                else -> {
                    box.costTotal += weight
                    box.volTotal += weight
                }
            }
        }

        var largestShort = 0f
        var sawShort = false
        for (b in boxes) {
            if (b.numeric == null) continue
            val s = shortAxis(b.pts) ?: continue
            if (!sawShort || s > largestShort) largestShort = s
            sawShort = true
        }
        if (sawShort) {
            for (b in boxes) {
                if (b.numeric == null) continue
                val s = shortAxis(b.pts) ?: continue
                val ratio = if (s > 0f) {
                    largestShort / s
                } else if (largestShort > 0f) {
                    Float.POSITIVE_INFINITY
                } else {
                    0f
                }
                if (ratio > 2.3f) b.numeric = null
            }
        }

        for (b in boxes) {
            if ('$' in b.cand.asis && b.numeric == null) b.costLabel = true
            val num = b.numeric
            if (num != null && num.count { it == '.' } == 1) {
                val nDigits = digitsOnly(num).length
                val places = digitsOnly(num.substring(num.indexOf('.') + 1)).length
                if (places == 2) addHint(b, "places-2", 1f, "cost", b.index)
                if (places == 3 && nDigits in 4..6) addHint(b, "places-3", 2f, "volume", b.index)
                if (places == 1 && nDigits >= 3) addHint(b, "places-1", 0.5f, "cost", b.index)
            }
            val mean = PumpCostVolUtils.probCorrectness(b.cand.digitsProbs).coerceIn(0f, 1f)
            addHint(b, "digit-mean", mean, "both", b.index)
            if (num != null && '.' in b.cand.digits) addHint(b, "digits-dot", 0.25f, "both", b.index)
            if (
                '$' in b.cand.asis &&
                num != null &&
                digitsOnly(b.cand.asis) == digitsOnly(b.cand.digits)
            ) {
                addHint(b, "dollar-match", 3f, "cost", b.index)
            }
        }

        fun sameLineGroups(pool: List<Box>): List<List<Box>> {
            val used = BooleanArray(pool.size)
            val groups = ArrayList<List<Box>>()
            for (i in pool.indices) {
                if (used[i]) continue
                val group = ArrayList<Box>()
                val stack = ArrayList<Int>()
                stack.add(i)
                used[i] = true
                while (stack.isNotEmpty()) {
                    val k = stack.removeAt(stack.lastIndex)
                    group.add(pool[k])
                    for (j in pool.indices) {
                        if (used[j]) continue
                        if (sameLine(pool[k], pool[j])) {
                            used[j] = true
                            stack.add(j)
                        }
                    }
                }
                groups.add(group)
            }
            return groups
        }

        for (label in boxes) {
            if (!label.costLabel && !label.volLabel) continue
            for (m in boxes) {
                if (m.index == label.index || m.numeric == null) continue
                if (!sameLine(label, m)) continue
                val covered = labelCoveredFraction(label, m)
                if (!(covered > 0f)) continue
                val weight = 3f * covered
                if (label.costLabel) addHint(m, "label-line", weight, "cost", label.index)
                if (label.volLabel) addHint(m, "label-line", weight, "volume", label.index)
            }
            val frame = label.frame ?: continue
            if (label.pts == null) continue
            val pool = ArrayList<Box>()
            for (m in boxes) {
                val pts = m.pts ?: continue
                if (m.numeric == null) continue
                if (sameLine(label, m)) continue
                if (!uOverlap(frame, pts)) continue
                pool.add(m)
            }
            val labelVc = (frame.v0 + frame.v1) * 0.5f
            var next: List<Box>? = null
            var bestDist = Float.MAX_VALUE
            var bestIdx = Int.MAX_VALUE
            for (members in sameLineGroups(pool)) {
                var sum = 0f
                var n = 0
                var minIdx = Int.MAX_VALUE
                for (m in members) {
                    val pts = m.pts ?: continue
                    val iv = project(frame, pts)
                    sum += (iv[2] + iv[3]) * 0.5f
                    n++
                    if (m.index < minIdx) minIdx = m.index
                }
                if (n == 0) continue
                val dist = abs(sum / n - labelVc)
                if (dist < bestDist || (dist == bestDist && minIdx < bestIdx)) {
                    bestDist = dist
                    bestIdx = minIdx
                    next = members
                }
            }
            for (m in next.orEmpty()) {
                if (label.costLabel) addHint(m, "label-next", 2f, "cost", label.index)
                if (label.volLabel) addHint(m, "label-next", 2f, "volume", label.index)
            }
        }

        val openVolFrom = HashMap<Int, Int>()
        val openCostFrom = HashMap<Int, Int>()
        for (label in boxes) {
            if (!label.costLabel && !label.volLabel) continue
            val marked = ArrayList<Box>()
            for (n in boxes) {
                if (n.numeric == null) continue
                if (sameLine(label, n)) marked.add(n)
            }
            if (marked.isEmpty()) continue
            val markedIds = HashSet<Int>()
            for (n in marked) markedIds.add(n.index)
            val targets = HashSet<Int>()
            for (n in marked) {
                for (other in boxes) {
                    if (other.numeric == null) continue
                    if (other.index in markedIds) continue
                    if (!sharesColumn(n, other)) continue
                    targets.add(other.index)
                }
            }
            for (t in targets) {
                if (label.costLabel) {
                    val prev = openVolFrom[t]
                    if (prev == null || label.index < prev) openVolFrom[t] = label.index
                }
                if (label.volLabel) {
                    val prev = openCostFrom[t]
                    if (prev == null || label.index < prev) openCostFrom[t] = label.index
                }
            }
        }
        for (b in boxes) {
            val volFrom = openVolFrom[b.index]
            if (volFrom != null) addHint(b, "open-role", 1.5f, "volume", volFrom)
            val costFrom = openCostFrom[b.index]
            if (costFrom != null) addHint(b, "open-role", 1.5f, "cost", costFrom)
        }

        val numbers = ArrayList<Box>()
        for (b in boxes) if (b.numeric != null) numbers.add(b)
        if (numbers.size == 1) {
            val only = numbers[0]
            for (label in boxes) {
                if (!label.costLabel && !label.volLabel) continue
                var aligned = false
                for (n in numbers) {
                    if (sameLine(label, n) || sharesColumn(label, n)) {
                        aligned = true
                        break
                    }
                }
                if (aligned) continue
                if (label.costLabel) addHint(only, "leftover", 0.5f, "volume", label.index)
                if (label.volLabel) addHint(only, "leftover", 0.5f, "cost", label.index)
            }
        }

        val sized = ArrayList<Pair<Box, Float>>()
        for (b in boxes) {
            if (b.numeric == null) continue
            val s = shortAxis(b.pts) ?: continue
            sized.add(b to s)
        }
        if (sized.size >= 2) {
            var maxS = sized[0].second
            var minS = sized[0].second
            for (pair in sized) {
                if (pair.second > maxS) maxS = pair.second
                if (pair.second < minS) minS = pair.second
            }
            if (maxS > minS && minS > 0f) {
                val weight = maxS / minS - 1f
                for (pair in sized) {
                    if (pair.second == maxS) {
                        addHint(pair.first, "taller", weight, "cost", pair.first.index)
                    }
                }
            }
        }

        val valued = ArrayList<Pair<Box, Float>>()
        for (b in boxes) {
            val num = b.numeric ?: continue
            if (num.count { it == '.' } != 1) continue
            val value = num.toFloatOrNull() ?: continue
            valued.add(b to value)
        }
        if (valued.size >= 2) {
            var maxV = valued[0].second
            for (pair in valued) if (pair.second > maxV) maxV = pair.second
            var winners = 0
            var winner: Box? = null
            for (pair in valued) {
                if (pair.second == maxV) {
                    winners++
                    winner = pair.first
                }
            }
            if (winners == 1 && winner != null) {
                addHint(winner, "larger-value", 0.5f, "cost", winner.index)
            }
        }

        fun better(score: Float, index: Int, bestScore: Float, bestIndex: Int): Boolean =
            score > bestScore || (score == bestScore && index < bestIndex)

        fun pick(costRole: Boolean): Box? {
            var best: Box? = null
            var bestScore = Float.NEGATIVE_INFINITY
            var bestIndex = Int.MAX_VALUE
            for (b in boxes) {
                if (b.numeric == null) continue
                val score = if (costRole) b.costTotal else b.volTotal
                if (best == null || better(score, b.index, bestScore, bestIndex)) {
                    best = b
                    bestScore = score
                    bestIndex = b.index
                }
            }
            return best
        }

        fun nextInColumn(winner: Box, costRole: Boolean): Box? {
            var best: Box? = null
            var bestScore = Float.NEGATIVE_INFINITY
            var bestIndex = Int.MAX_VALUE
            for (b in boxes) {
                if (b.index == winner.index || b.numeric == null) continue
                if (!sharesColumn(winner, b)) continue
                val score = if (costRole) b.costTotal else b.volTotal
                if (best == null || better(score, b.index, bestScore, bestIndex)) {
                    best = b
                    bestScore = score
                    bestIndex = b.index
                }
            }
            return best
        }

        var costBox = pick(true)
        var volBox = pick(false)
        if (costBox != null && volBox != null && costBox.index == volBox.index) {
            if (costBox.volTotal > costBox.costTotal) {
                costBox = nextInColumn(volBox, true)
            } else {
                volBox = nextInColumn(costBox, false)
            }
        }
        if (
            costBox != null &&
            volBox != null &&
            costBox.index != volBox.index &&
            heavyOverlap(costBox, volBox)
        ) {
            val keepCost = costBox.costTotal >= volBox.volTotal
            val kept = if (keepCost) costBox else volBox
            val costRole = !keepCost
            var best: Box? = null
            var bestScore = Float.NEGATIVE_INFINITY
            var bestIndex = Int.MAX_VALUE
            for (b in boxes) {
                if (b.numeric == null) continue
                if (b.index == kept.index) continue
                if (heavyOverlap(kept, b)) continue
                val score = if (costRole) b.costTotal else b.volTotal
                if (best == null || better(score, b.index, bestScore, bestIndex)) {
                    best = b
                    bestScore = score
                    bestIndex = b.index
                }
            }
            if (keepCost) volBox = best else costBox = best
        }

        fun render(box: Box?, costRole: Boolean): String {
            val num = box?.numeric ?: return "N/A"
            if ('.' in num) return num
            val digits = digitsOnly(num)
            val places = if (costRole) 2 else 3
            if (digits.length < places) return "N/A"
            return place(digits, places)
        }

        val costText = render(costBox, true)
        val volText = render(volBox, false)
        val costCand = if (costText == "N/A") emptyCand else costBox!!.cand
        val volCand = if (volText == "N/A") emptyCand else volBox!!.cand
        val root = JSONObject()
        root.put("cost", costText)
        root.put("vol", volText)
        val arr = JSONArray()
        for (b in boxes) {
            val hints = JSONArray()
            for (h in b.hints) {
                hints.put(
                    JSONObject()
                        .put("name", h.name)
                        .put("weight", h.weight.toDouble())
                        .put("role", h.role)
                        .put("from", h.from),
                )
            }
            arr.put(
                JSONObject()
                    .put("index", b.index)
                    .put("label", b.cand.label)
                    .put("asis", b.cand.asis)
                    .put("digits", b.cand.digits)
                    .put("costTotal", b.costTotal.toDouble())
                    .put("volTotal", b.volTotal.toDouble())
                    .put("hints", hints),
            )
        }
        root.put("boxes", arr)
        return WeightedCostVol(costText, volText, root.toString(), costCand, volCand)
    }

}
