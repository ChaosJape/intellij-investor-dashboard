package com.vermouthx.stocker

import com.intellij.openapi.application.ApplicationManager
import com.vermouthx.stocker.enums.StockerMarketIndex
import com.vermouthx.stocker.enums.StockerMarketType
import com.vermouthx.stocker.listeners.StockerQuoteReloadNotifier.*
import com.vermouthx.stocker.listeners.StockerQuoteUpdateNotifier.*
import com.vermouthx.stocker.settings.StockerSetting
import com.vermouthx.stocker.utils.StockerQuoteHttpUtil
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class StockerApp {

    private val setting = StockerSetting.instance
    private val messageBus = ApplicationManager.getApplication().messageBus

    private var scheduledExecutorService: ScheduledExecutorService = Executors.newScheduledThreadPool(4)

    private var scheduleInitialDelay: Long = 3
    private val schedulePeriod: Long = StockerSetting.instance.refreshInterval

    fun schedule() {
        if (scheduledExecutorService.isShutdown) {
            scheduledExecutorService = Executors.newScheduledThreadPool(4)
            scheduleInitialDelay = 0
        }
        scheduledExecutorService.scheduleAtFixedRate(
            createQuoteUpdateThread(StockerMarketType.AShare, setting.aShareList),
            scheduleInitialDelay,
            schedulePeriod,
            TimeUnit.SECONDS
        )

        scheduledExecutorService.scheduleAtFixedRate(
            createQuoteUpdateThread(StockerMarketType.USStocks, setting.usStocksList),
            scheduleInitialDelay,
            schedulePeriod,
            TimeUnit.SECONDS
        )
//        scheduledExecutorService.scheduleAtFixedRate(
//            createQuoteUpdateThread(StockerMarketType.Crypto, setting.cryptoList),
//            scheduleInitialDelay, schedulePeriod, TimeUnit.SECONDS
//        )
        scheduledExecutorService.scheduleAtFixedRate(
            createAllQuoteUpdateThread(), scheduleInitialDelay, schedulePeriod, TimeUnit.SECONDS
        )
        // QH 行情统一由 createAllQuoteUpdateThread 拉取并发布, 不再单独起 QH 轮询线程,
        // 避免每个周期对 hq.sinajs.cn 重复请求(新浪 CDN 节点缓存不一致会交替返回陈旧快照)
    }

    fun shutdown() {
        scheduledExecutorService.shutdown()
    }

    fun isShutdown(): Boolean {
        return scheduledExecutorService.isShutdown
    }

    private fun clear() {
        messageBus.syncPublisher(STOCK_ALL_QUOTE_RELOAD_TOPIC).clear()
        messageBus.syncPublisher(STOCK_CN_QUOTE_RELOAD_TOPIC).clear()
//        messageBus.syncPublisher(STOCK_HK_QUOTE_RELOAD_TOPIC).clear()
        messageBus.syncPublisher(STOCK_US_QUOTE_RELOAD_TOPIC).clear()
        messageBus.syncPublisher(QH_QUOTE_RELOAD_TOPIC).clear()
    }

    fun shutdownThenClear() {
        shutdown()
        clear()
    }

    private fun createAllQuoteUpdateThread(): Runnable {
        return Runnable {
            val quoteProvider = setting.quoteProvider
            val aShareQuotes = StockerQuoteHttpUtil.get(
                StockerMarketType.AShare, quoteProvider, setting.aShareList
            )
//            StockerQuoteHttpUtil.get(StockerMarketType.HKStocks, quoteProvider, setting.hkStocksList)
            val usStockQuotes = StockerQuoteHttpUtil.get(
                StockerMarketType.USStocks, quoteProvider, setting.usStocksList
            )
//            StockerQuoteHttpUtil.get(StockerMarketType.Crypto, quoteProvider, setting.cryptoList)
            val qhQuotes = StockerQuoteHttpUtil.get(StockerMarketType.QH, quoteProvider, setting.qhList)
            val allStockQuotes = listOf(aShareQuotes, usStockQuotes, qhQuotes).flatten()
            val aShareIndices = StockerQuoteHttpUtil.get(
                StockerMarketType.AShare, quoteProvider, StockerMarketIndex.CN.codes
            )
//            StockerQuoteHttpUtil.get(StockerMarketType.HKStocks, quoteProvider, StockerMarketIndex.HK.codes)
            val usIndices = StockerQuoteHttpUtil.get(
                StockerMarketType.USStocks, quoteProvider, StockerMarketIndex.US.codes
            )
//            StockerQuoteHttpUtil.get(StockerMarketType.Crypto, quoteProvider, StockerMarketIndex.Crypto.codes)
            val qhIndices = StockerQuoteHttpUtil.get(StockerMarketType.QH, quoteProvider, StockerMarketIndex.QH.codes)
            val allStockIndices = listOf(aShareIndices, usIndices, qhIndices).flatten()

            // 全部页签
            val allPublisher = messageBus.syncPublisher(STOCK_ALL_QUOTE_UPDATE_TOPIC)
            allPublisher.syncQuotes(allStockQuotes, setting.allStockListSize)
            allPublisher.syncIndices(allStockIndices)
            // 期货页签复用同一份 QH 数据, 避免每个周期对 hq.sinajs.cn 重复请求
            val qhPublisher = messageBus.syncPublisher(QH_QUOTE_UPDATE_TOPIC)
            qhPublisher.syncQuotes(qhQuotes, setting.qhList.size)
            qhPublisher.syncIndices(qhIndices)
        }
    }

    private fun createQuoteUpdateThread(marketType: StockerMarketType, stockCodeList: List<String>): Runnable {
        return Runnable {
            refresh(marketType, stockCodeList)
        }
    }

    private fun refresh(
        marketType: StockerMarketType, stockCodeList: List<String>
    ) {
        val quoteProvider = setting.quoteProvider
        val size = stockCodeList.size
        when (marketType) {
            StockerMarketType.AShare -> {
                val quotes = StockerQuoteHttpUtil.get(marketType, quoteProvider, stockCodeList)
                val indices = StockerQuoteHttpUtil.get(marketType, quoteProvider, StockerMarketIndex.CN.codes)
                val publisher = messageBus.syncPublisher(STOCK_CN_QUOTE_UPDATE_TOPIC)
                publisher.syncQuotes(quotes, size)
                publisher.syncIndices(indices)
            }

//            StockerMarketType.HKStocks -> {
//                val quotes = StockerQuoteHttpUtil.get(marketType, quoteProvider, stockCodeList)
//                val indices = StockerQuoteHttpUtil.get(marketType, quoteProvider, StockerMarketIndex.HK.codes)
//                val publisher = messageBus.syncPublisher(STOCK_HK_QUOTE_UPDATE_TOPIC)
//                publisher.syncQuotes(quotes, size)
//                publisher.syncIndices(indices)
//            }

            StockerMarketType.USStocks -> {
                val quotes = StockerQuoteHttpUtil.get(marketType, quoteProvider, stockCodeList)
                val indices = StockerQuoteHttpUtil.get(marketType, quoteProvider, StockerMarketIndex.US.codes)
                val publisher = messageBus.syncPublisher(STOCK_US_QUOTE_UPDATE_TOPIC)
                publisher.syncQuotes(quotes, size)
                publisher.syncIndices(indices)
            }

            StockerMarketType.Crypto -> {
                val quotes = StockerQuoteHttpUtil.get(marketType, quoteProvider, stockCodeList)
                val indices = StockerQuoteHttpUtil.get(marketType, quoteProvider, StockerMarketIndex.Crypto.codes)
                val publisher = messageBus.syncPublisher(CRYPTO_QUOTE_UPDATE_TOPIC)
                publisher.syncQuotes(quotes, size)
                publisher.syncIndices(indices)
            }

            StockerMarketType.QH -> {
                val quotes = StockerQuoteHttpUtil.get(marketType, quoteProvider, stockCodeList)
                val indices = StockerQuoteHttpUtil.get(marketType, quoteProvider, StockerMarketIndex.QH.codes)
                val publisher = messageBus.syncPublisher(QH_QUOTE_UPDATE_TOPIC)
                publisher.syncQuotes(quotes, size)
                publisher.syncIndices(indices)
            }
        }
    }
}
