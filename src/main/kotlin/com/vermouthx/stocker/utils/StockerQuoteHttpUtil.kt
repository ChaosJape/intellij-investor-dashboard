package com.vermouthx.stocker.utils

import com.intellij.openapi.diagnostic.Logger
import com.vermouthx.stocker.entities.StockerQuote
import com.vermouthx.stocker.enums.StockerMarketType
import com.vermouthx.stocker.enums.StockerQuoteProvider
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.util.EntityUtils

object StockerQuoteHttpUtil {

    /**
     * 新浪国际贵金属(现货黄金/白银)接口代码映射: 插件代码 -> 新浪 hq.sinajs.cn 的 hf_ 前缀代码
     * 注意: 国际现货贵金属在新浪走 hf_ 前缀, 与国内期货的 nf_ 前缀不同
     */
    val sinaIntlMetalCodeMap = mapOf(
        "XAUUSD" to "hf_XAU",
        "XAGUSD" to "hf_XAG",
    )

    private val log = Logger.getInstance(javaClass)

    private val httpClientPool = run {
        val connectionManager = PoolingHttpClientConnectionManager()
        connectionManager.maxTotal = 20
        val requestConfig = RequestConfig.custom().build()
        HttpClients.custom().setConnectionManager(connectionManager).setDefaultRequestConfig(requestConfig)
            .useSystemProperties().build()
    }

    fun get(
        marketType: StockerMarketType, quoteProvider: StockerQuoteProvider, codes: List<String>
    ): List<StockerQuote> {
        if (codes.isEmpty()) {
            return emptyList()
        }
        val codesParam = when (quoteProvider) {
            StockerQuoteProvider.SINA -> {
//                if (marketType == StockerMarketType.QH || marketType == StockerMarketType.HKStocks) {
                if (marketType == StockerMarketType.QH) {
                    codes.joinToString(",") { code ->
                        // 国际贵金属走 hf_ 前缀, 国内期货走 nf_ 前缀
                        sinaIntlMetalCodeMap[code.uppercase()] ?: "nf_${code.uppercase()}"
                    }
                } else {
                    codes.joinToString(",") { code ->
                        "${quoteProvider.providerPrefixMap[marketType]}${code.lowercase()}"
                    }
                }
            }

            StockerQuoteProvider.TENCENT -> {
//                if (marketType == StockerMarketType.HKStocks || marketType == StockerMarketType.USStocks) {
                if ( marketType == StockerMarketType.USStocks) {
                    codes.joinToString(",") { code ->
                        "${quoteProvider.providerPrefixMap[marketType]}${code.uppercase()}"
                    }
                } else {
                    codes.joinToString(",") { code ->
                        "${quoteProvider.providerPrefixMap[marketType]}${code.lowercase()}"
                    }
                }
            }
        }

        val url = if (quoteProvider == StockerQuoteProvider.SINA) {
            // 新浪 CDN 缓存节点间新鲜度不一致, 请求会交替命中"实时节点"和"陈旧缓存节点"(hf_ 国际贵金属尤为明显),
            // rn= 参数强制绕过缓存, 保证每次拿到实时快照
            quoteProvider.host.replace("list=", "rn=${System.currentTimeMillis()}/list=") + codesParam
        } else {
            "${quoteProvider.host}${codesParam}"
        }
        val httpGet = HttpGet(url)
        if (quoteProvider == StockerQuoteProvider.SINA) {
            httpGet.setHeader("Referer", "https://finance.sina.com.cn") // Sina API requires this header
        }
        return try {
            val response = httpClientPool.execute(httpGet)
            val responseText = if (quoteProvider == StockerQuoteProvider.SINA) {
                // Sina 行情接口按 GB18030 返回, 用 UTF-8 解析中文名称会乱码
                EntityUtils.toString(response.entity, "GB18030")
            } else {
                EntityUtils.toString(response.entity, "UTF-8")
            }
            StockerQuoteParser.parseQuoteResponse(quoteProvider, marketType, responseText)
        } catch (e: Exception) {
            log.warn(e)
            emptyList()
        }
    }

    fun validateCode(
        marketType: StockerMarketType, quoteProvider: StockerQuoteProvider, code: String
    ): Boolean {
        when (quoteProvider) {
            StockerQuoteProvider.SINA -> {
//                val url = if (marketType == StockerMarketType.QH || marketType == StockerMarketType.HKStocks) {
                val url = if (marketType == StockerMarketType.QH) {
                    // 国际贵金属走 hf_ 前缀, 国内期货走 nf_ 前缀
                    // rn= 参数绕过新浪 CDN 缓存, 与 get() 保持一致
                    "${quoteProvider.host.replace("list=", "rn=${System.currentTimeMillis()}/list=")}${sinaIntlMetalCodeMap[code.uppercase()] ?: "nf_${code.uppercase()}"}"
                } else {
                    "${quoteProvider.host.replace("list=", "rn=${System.currentTimeMillis()}/list=")}${quoteProvider.providerPrefixMap[marketType]}${code.lowercase()}"
                }
                val httpGet = HttpGet(url)
                httpGet.setHeader("Referer", "https://finance.sina.com.cn") // Sina API requires this header
                val response = httpClientPool.execute(httpGet)
                val responseText = EntityUtils.toString(response.entity, "UTF-8")
                val firstLine = responseText.split("\n")[0]
                val start = firstLine.indexOfFirst { c -> c == '"' } + 1
                val end = firstLine.indexOfLast { c -> c == '"' }
                if (start == end) {
                    return false
                }
                return firstLine.subSequence(start, end).contains(",")
            }

            StockerQuoteProvider.TENCENT -> {
//                val url = if (marketType == StockerMarketType.HKStocks || marketType == StockerMarketType.USStocks) {
                val url = if ( marketType == StockerMarketType.USStocks) {
                    "${quoteProvider.host}${quoteProvider.providerPrefixMap[marketType]}${code.uppercase()}"
                } else {
                    "${quoteProvider.host}${quoteProvider.providerPrefixMap[marketType]}${code.lowercase()}"
                }
                val httpGet = HttpGet(url)
                val response = httpClientPool.execute(httpGet)
                val responseText = EntityUtils.toString(response.entity, "UTF-8")
                return !responseText.startsWith("v_pv_none_match")
            }
        }
    }
}
