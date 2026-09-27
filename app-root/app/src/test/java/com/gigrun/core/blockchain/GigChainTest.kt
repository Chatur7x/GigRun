package com.gigrun.core.blockchain

import com.gigrun.data.database.entities.TempTransaction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GigChainTest {

    private fun tx(data: String) = TempTransaction(type = "E", serializedData = data, timestamp = 1L)

    @Test
    fun mineVerifyRoundTrip_tamperFails() = runBlocking {
        val b = GigChain.mineBlock(1, "GENESIS_HASH_GIGRUN_V3.3.0", listOf(tx("{\"amt\":75.5}")))
        assertTrue(b.hash.startsWith("00"))
        assertTrue(GigChain.verifyLedger(listOf(b)))
        // Tampered payload fails
        assertFalse(GigChain.verifyLedger(listOf(b.copy(dataPayload = "[tampered]"))))
        // Broken ordering fails
        assertFalse(GigChain.verifyLedger(listOf(b, b.copy(index = 5))))
        // Unmined hash fails difficulty check
        assertFalse(GigChain.verifyLedger(listOf(b.copy(hash = "ff" + b.hash.drop(2)))))
    }

    @Test
    fun verifyLedger_emptyPasses_genesisSentinelPinned() {
        assertTrue(GigChain.verifyLedger(emptyList()))
        val b = runBlocking { GigChain.mineBlock(1, "GENESIS_HASH_GIGRUN_V3.3.0", listOf(tx("x"))) }
        assertTrue(GigChain.verifyLedger(listOf(b)))
        // Genesis swap (wrong parent, high index) is rejected
        assertFalse(GigChain.verifyLedger(listOf(b.copy(previousHash = "EVIL", index = 9))))
        // Forged genesis at index 0/1 is rejected too (strict pin, no exemptions)
        assertFalse(GigChain.verifyLedger(listOf(b.copy(previousHash = "EVIL", index = 0))))
        assertFalse(GigChain.verifyLedger(listOf(b.copy(previousHash = "EVIL", index = 1))))
        // Broken link across two valid blocks is rejected
        val b2 = runBlocking { GigChain.mineBlock(2, b.hash, listOf(tx("y"))) }
        assertTrue(GigChain.verifyLedger(listOf(b, b2)))
        assertFalse(GigChain.verifyLedger(listOf(b, b2.copy(previousHash = "WRONG"))))
    }
}
