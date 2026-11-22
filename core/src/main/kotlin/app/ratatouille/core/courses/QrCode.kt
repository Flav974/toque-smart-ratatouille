package app.ratatouille.core.courses

/**
 * Encodeur QR code minimal (norme ISO/IEC 18004), mode octets (UTF-8), versions 1 à 40.
 * Écrit d'après l'algorithme public de Project Nayuki (licence MIT), sans bibliothèque : l'appli
 * n'a aucune dépendance de ce genre et se compile hors ligne.
 */
class QrCode private constructor(val version: Int, val ecc: Ecc, data: ByteArray) {

    enum class Ecc(val formatBits: Int, val index: Int) { L(1, 0), M(0, 1), Q(3, 2), H(2, 3) }

    val size: Int = version * 4 + 17
    private val modules = Array(size) { BooleanArray(size) }
    private val isFunction = Array(size) { BooleanArray(size) }
    var mask: Int = 0
        private set

    init {
        drawFunctionPatterns()
        drawCodewords(addEccAndInterleave(data))
        var best = 0
        var bestPenalty = Int.MAX_VALUE
        for (m in 0..7) {
            applyMask(m)
            drawFormatBits(m)
            val p = penalty()
            if (p < bestPenalty) { best = m; bestPenalty = p }
            applyMask(m) // annule (XOR)
        }
        mask = best
        applyMask(best)
        drawFormatBits(best)
    }

    /** Module foncé ? Hors du code : clair. */
    fun get(x: Int, y: Int): Boolean = x in 0 until size && y in 0 until size && modules[y][x]

    // ------------------------------------------------------------------ motifs fixes

    private fun drawFunctionPatterns() {
        for (i in 0 until size) {
            setFunction(6, i, i % 2 == 0)
            setFunction(i, 6, i % 2 == 0)
        }
        drawFinder(3, 3)
        drawFinder(size - 4, 3)
        drawFinder(3, size - 4)
        val pos = alignmentPositions(version)
        val n = pos.size
        for (i in 0 until n) for (j in 0 until n) {
            if ((i == 0 && j == 0) || (i == 0 && j == n - 1) || (i == n - 1 && j == 0)) continue
            drawAlignment(pos[i], pos[j])
        }
        drawFormatBits(0)
        drawVersion()
    }

    private fun drawFinder(x: Int, y: Int) {
        for (dy in -4..4) for (dx in -4..4) {
            val dist = maxOf(Math.abs(dx), Math.abs(dy))
            val xx = x + dx
            val yy = y + dy
            if (xx in 0 until size && yy in 0 until size) setFunction(xx, yy, dist != 2 && dist != 4)
        }
    }

    private fun drawAlignment(x: Int, y: Int) {
        for (dy in -2..2) for (dx in -2..2) setFunction(x + dx, y + dy, maxOf(Math.abs(dx), Math.abs(dy)) != 1)
    }

    private fun drawFormatBits(m: Int) {
        val data = ecc.formatBits shl 3 or m
        var rem = data
        repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
        val bits = (data shl 10 or rem) xor 0x5412
        for (i in 0..5) setFunction(8, i, bit(bits, i))
        setFunction(8, 7, bit(bits, 6))
        setFunction(8, 8, bit(bits, 7))
        setFunction(7, 8, bit(bits, 8))
        for (i in 9 until 15) setFunction(14 - i, 8, bit(bits, i))
        for (i in 0 until 8) setFunction(size - 1 - i, 8, bit(bits, i))
        for (i in 8 until 15) setFunction(8, size - 15 + i, bit(bits, i))
        setFunction(8, size - 8, true)
    }

    private fun drawVersion() {
        if (version < 7) return
        var rem = version
        repeat(12) { rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25) }
        val bits = version shl 12 or rem
        for (i in 0 until 18) {
            val b = bit(bits, i)
            val a = size - 11 + i % 3
            val c = i / 3
            setFunction(a, c, b)
            setFunction(c, a, b)
        }
    }

    private fun setFunction(x: Int, y: Int, dark: Boolean) {
        modules[y][x] = dark
        isFunction[y][x] = true
    }

    // ------------------------------------------------------------------ données

    private fun addEccAndInterleave(data: ByteArray): ByteArray {
        val numBlocks = BLOCKS[ecc.index][version]
        val blockEccLen = ECC_PER_BLOCK[ecc.index][version]
        val rawCodewords = rawDataModules(version) / 8
        val numShortBlocks = numBlocks - rawCodewords % numBlocks
        val shortBlockLen = rawCodewords / numBlocks
        val divisor = rsDivisor(blockEccLen)
        val blocks = ArrayList<ByteArray>()
        var k = 0
        for (i in 0 until numBlocks) {
            val len = shortBlockLen - blockEccLen + (if (i < numShortBlocks) 0 else 1)
            val dat = data.copyOfRange(k, k + len)
            k += len
            val eccBytes = rsRemainder(dat, divisor)
            val block = ByteArray(shortBlockLen + 1)
            System.arraycopy(dat, 0, block, 0, dat.size)
            System.arraycopy(eccBytes, 0, block, block.size - blockEccLen, blockEccLen)
            blocks += block
        }
        val out = ByteArray(rawCodewords)
        var n = 0
        for (i in 0 until blocks[0].size) for (j in blocks.indices) {
            if (i != shortBlockLen - blockEccLen || j >= numShortBlocks) out[n++] = blocks[j][i]
        }
        return out
    }

    private fun drawCodewords(data: ByteArray) {
        var i = 0
        var right = size - 1
        while (right >= 1) {
            if (right == 6) right = 5
            for (vert in 0 until size) for (j in 0..1) {
                val x = right - j
                val upward = ((right + 1) and 2) == 0
                val y = if (upward) size - 1 - vert else vert
                if (!isFunction[y][x] && i < data.size * 8) {
                    modules[y][x] = bit(data[i ushr 3].toInt(), 7 - (i and 7))
                    i++
                }
            }
            right -= 2
        }
    }

    private fun applyMask(m: Int) {
        for (y in 0 until size) for (x in 0 until size) {
            val invert = when (m) {
                0 -> (x + y) % 2 == 0
                1 -> y % 2 == 0
                2 -> x % 3 == 0
                3 -> (x + y) % 3 == 0
                4 -> (x / 3 + y / 2) % 2 == 0
                5 -> x * y % 2 + x * y % 3 == 0
                6 -> (x * y % 2 + x * y % 3) % 2 == 0
                else -> ((x + y) % 2 + x * y % 3) % 2 == 0
            }
            if (invert && !isFunction[y][x]) modules[y][x] = !modules[y][x]
        }
    }

    /** Pénalité simplifiée (règles 1, 2 et 4 de la norme) : choisit un masque lisible. */
    private fun penalty(): Int {
        var p = 0
        for (horizontal in listOf(true, false)) for (a in 0 until size) {
            var run = 1
            for (b in 1 until size) {
                val cur = if (horizontal) modules[a][b] else modules[b][a]
                val prev = if (horizontal) modules[a][b - 1] else modules[b - 1][a]
                if (cur == prev) { run++; if (run == 5) p += 3 else if (run > 5) p++ } else run = 1
            }
        }
        for (y in 0 until size - 1) for (x in 0 until size - 1) {
            val c = modules[y][x]
            if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) p += 3
        }
        var dark = 0
        for (row in modules) for (c in row) if (c) dark++
        val total = size * size
        val k = (Math.abs(dark * 20 - total * 10) + total - 1) / total - 1
        return p + maxOf(0, k) * 10
    }

    companion object {
        /** Code QR du texte (UTF-8), version la plus petite possible. Null si trop long. */
        fun encode(texte: String, ecc: Ecc = Ecc.M): QrCode? {
            val bytes = texte.toByteArray(Charsets.UTF_8)
            for (v in 1..40) {
                val ccBits = if (v <= 9) 8 else 16
                val capBits = dataCodewords(v, ecc) * 8
                val used = 4 + ccBits + bytes.size * 8
                if (used > capBits) continue
                val bits = BitBuf()
                bits.add(0x4, 4)
                bits.add(bytes.size, ccBits)
                for (b in bytes) bits.add(b.toInt() and 0xFF, 8)
                bits.add(0, minOf(4, capBits - bits.len))
                bits.add(0, (8 - bits.len % 8) % 8)
                var pad = 0xEC
                while (bits.len < capBits) { bits.add(pad, 8); pad = pad xor 0xEC xor 0x11 }
                return QrCode(v, ecc, bits.bytes())
            }
            return null
        }

        fun rawDataModules(ver: Int): Int {
            var r = (16 * ver + 128) * ver + 64
            if (ver >= 2) {
                val n = ver / 7 + 2
                r -= (25 * n - 10) * n - 55
                if (ver >= 7) r -= 36
            }
            return r
        }

        fun dataCodewords(ver: Int, ecc: Ecc): Int =
            rawDataModules(ver) / 8 - ECC_PER_BLOCK[ecc.index][ver] * BLOCKS[ecc.index][ver]

        fun alignmentPositions(ver: Int): IntArray {
            if (ver == 1) return IntArray(0)
            val n = ver / 7 + 2
            val step = if (ver == 32) 26 else (ver * 4 + n * 2 + 1) / (n * 2 - 2) * 2
            val out = IntArray(n)
            out[0] = 6
            var pos = ver * 4 + 17 - 7
            for (i in n - 1 downTo 1) { out[i] = pos; pos -= step }
            return out
        }

        private fun bit(x: Int, i: Int) = (x ushr i) and 1 != 0

        private fun mul(x: Int, y: Int): Int {
            var z = 0
            for (i in 7 downTo 0) {
                z = (z shl 1) xor ((z ushr 7) * 0x11D)
                z = z xor (((y ushr i) and 1) * x)
            }
            return z and 0xFF
        }

        private fun rsDivisor(degree: Int): IntArray {
            val r = IntArray(degree)
            r[degree - 1] = 1
            var root = 1
            for (i in 0 until degree) {
                for (j in r.indices) {
                    r[j] = mul(r[j], root)
                    if (j + 1 < r.size) r[j] = r[j] xor r[j + 1]
                }
                root = mul(root, 0x02)
            }
            return r
        }

        private fun rsRemainder(data: ByteArray, divisor: IntArray): ByteArray {
            val r = IntArray(divisor.size)
            for (b in data) {
                val factor = (b.toInt() and 0xFF) xor r[0]
                System.arraycopy(r, 1, r, 0, r.size - 1)
                r[r.size - 1] = 0
                for (i in r.indices) r[i] = r[i] xor mul(divisor[i], factor)
            }
            return ByteArray(r.size) { r[it].toByte() }
        }

        private val ECC_PER_BLOCK = arrayOf(
            intArrayOf(-1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
            intArrayOf(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28),
            intArrayOf(-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
            intArrayOf(-1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
        )
        private val BLOCKS = arrayOf(
            intArrayOf(-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25),
            intArrayOf(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49),
            intArrayOf(-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68),
            intArrayOf(-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81),
        )
    }

    private class BitBuf {
        private val bits = ArrayList<Boolean>()
        val len: Int get() = bits.size
        fun add(v: Int, n: Int) { for (i in n - 1 downTo 0) bits += (v ushr i) and 1 != 0 }
        fun bytes(): ByteArray {
            val out = ByteArray(bits.size / 8)
            for (i in bits.indices) if (bits[i]) out[i ushr 3] = (out[i ushr 3].toInt() or (1 shl (7 - (i and 7)))).toByte()
            return out
        }
    }
}
