package com.gigrun.core.utils

import com.gigrun.core.blockchain.GigChain
import com.gigrun.data.database.dao.BlockDao
import com.gigrun.data.database.dao.TempTransactionDao
import com.gigrun.data.database.entities.Block
import com.gigrun.data.database.entities.TempTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LedgerManager @Inject constructor(
    private val blockDao: BlockDao,
    private val tempTransactionDao: TempTransactionDao
) {
    val blocksFlow: Flow<List<Block>> = blockDao.getAllBlocksFlow()
    val tempTransactionsFlow: Flow<List<TempTransaction>> = tempTransactionDao.getAllTempTransactionsFlow()

    companion object {
        // Process-wide: AppDatabase.getInstance() can back a second LedgerManager
        // outside Hilt — an instance Mutex would let two miners fork the chain.
        private val commitMutex = kotlinx.coroutines.sync.Mutex()
    }

    suspend fun addTransaction(type: String, serializedData: String) = withContext(Dispatchers.IO) {
        // Cap: unbounded strings (50 MB mempool PoC) become one giant block and
        // OOM readers. Ledger entries are metadata, not file storage.
        val temp = TempTransaction(
            type = type.take(32),
            serializedData = serializedData.take(8000),
            timestamp = System.currentTimeMillis()
        )
        tempTransactionDao.insert(temp)
    }

    suspend fun commitTransactionsToBlockchain(): Boolean = withContext(Dispatchers.IO) {
        commitMutex.withLock {
            val unconfirmed = tempTransactionDao.getAllTempTransactions()
            if (unconfirmed.isEmpty()) return@withLock false
            // Cap txns/block: mining holds the mutex; a 10k-deep mempool would
            // starve commits and the 30 s verify poll. Oldest 200 go now.
            val batch = unconfirmed.take(200)

            val latestBlock = blockDao.getLatestBlock()
            val nextIndex = (latestBlock?.index ?: 0) + 1
            val previousHash = latestBlock?.hash ?: "GENESIS_HASH_GIGRUN_V3.3.0"

            // Mine block using GigChain Proof-of-Work (cancellable via yield inside miner)
            val newBlock = GigChain.mineBlock(nextIndex, previousHash, batch)

            // R&D fix: atomic insert-then-clear (crash-safe, no double-mint).
            // Lost the height race (duplicate index) → keep mempool, report false.
            if (!blockDao.insertBlockAndClearMempool(newBlock, batch.map { it.id })) {
                android.util.Log.w("Ledger", "block height race lost at index $nextIndex, mempool kept")
                return@withLock false
            }
            true
        }
    }

    suspend fun verifyBlockchain(): Boolean = withContext(Dispatchers.IO) {
        val blocks = blockDao.getAllBlocks()
        GigChain.verifyLedger(blocks)
    }
}
