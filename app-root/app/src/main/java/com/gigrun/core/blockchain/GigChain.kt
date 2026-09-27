package com.gigrun.core.blockchain

import com.gigrun.data.database.entities.Block
import com.gigrun.data.database.entities.TempTransaction
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.security.MessageDigest
import java.nio.charset.StandardCharsets

object GigChain {
    private const val DIFFICULTY = "00" // Requires SHA-256 to start with "00" for easy local proof-of-work

    fun calculateHash(index: Int, previousHash: String, timestamp: Long, dataPayload: String, nonce: Long): String {
        return calculateHash(MessageDigest.getInstance("SHA-256"), index, previousHash, timestamp, dataPayload, nonce)
    }

    private fun calculateHash(digest: MessageDigest, index: Int, previousHash: String, timestamp: Long, dataPayload: String, nonce: Long): String {
        val input = "$index$previousHash$timestamp$dataPayload$nonce"
        digest.reset()
        val hashBytes = digest.digest(input.toByteArray(StandardCharsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun mineBlock(index: Int, previousHash: String, transactions: List<TempTransaction>): Block {
        val timestamp = System.currentTimeMillis()
        val dataPayload = Json.encodeToString(transactions.map { it.serializedData })
        var nonce = 0L
        var hash = ""
        // Reused digest: getInstance per nonce (~256 expected iters) is pure overhead.
        val digest = MessageDigest.getInstance("SHA-256")

        // R&D fix: cancellable + bounded PoW so a cancelled coroutine can't spin forever.
        while (true) {
            hash = calculateHash(digest, index, previousHash, timestamp, dataPayload, nonce)
            if (hash.startsWith(DIFFICULTY)) {
                break
            }
            nonce++
            if (nonce % 4096 == 0L) {
                // Cooperative cancellation point; also prevents infinite loop on pathological input.
                yield()
            }
            // Beast-mode bound: difficulty "00" always hits far earlier. NEVER mint
            // an unmined block — fail loudly instead of voiding tamper-evidence.
            if (nonce > 5_000_000L) throw IllegalStateException("GigChain PoW bound exceeded at index $index")
        }

        return Block(
            index = index,
            previousHash = previousHash,
            timestamp = timestamp,
            dataPayload = dataPayload,
            nonce = nonce,
            hash = hash
        )
    }

    fun verifyLedger(blocks: List<Block>): Boolean {
        if (blocks.isEmpty()) return true

        // Defensive ordering: chain is by height, not row-id (see BlockDao ORDER BY).
        val sorted = blocks.sortedBy { it.index }
        // Strict genesis pin: the first block MUST carry the canonical sentinel.
        // (Index exemptions removed — a forged index-0/1 genesis used to pass.)
        if (sorted[0].previousHash != "GENESIS_HASH_GIGRUN_V3.3.0") {
            return false
        }

        // Verify genesis block has valid self-hash; chain links + ordering below.
        for (i in sorted.indices) {
            val current = sorted[i]
            // Difficulty: every block must actually be mined.
            if (!current.hash.startsWith(DIFFICULTY)) return false
            // Verify index ordering
            if (i > 0 && current.index != sorted[i - 1].index + 1) {
                return false
            }
            // Verify hash match
            val calculated = calculateHash(
                current.index,
                current.previousHash,
                current.timestamp,
                current.dataPayload,
                current.nonce
            )
            if (calculated != current.hash) {
                return false
            }
            // Verify chain links
            if (i > 0 && current.previousHash != sorted[i - 1].hash) {
                return false
            }
        }
        return true
    }
}
