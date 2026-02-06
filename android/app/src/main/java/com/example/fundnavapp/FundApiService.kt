package com.example.fundnavapp

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class FundApiService {

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    // 获取基金持仓数据
    fun getFundHoldings(fundCode: String): FundHoldingsResponse? {
        val url = "http://fundf10.eastmoney.com/FundArchivesDatas.aspx"
        val params = "type=jjcc&code=$fundCode&topline=10"
        val fullUrl = "$url?$params"

        val request = Request.Builder()
            .url(fullUrl)
            .header("User-Agent", "Mozilla/5.0")
            .header("Referer", "http://fundf10.eastmoney.com/ccmx_$fundCode.html")
            .build()

        try {
            val response: Response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    return parseFundHoldings(responseBody, fundCode)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return null
    }

    // 解析基金持仓数据
    private fun parseFundHoldings(responseBody: String, fundCode: String): FundHoldingsResponse? {
        try {
            // 提取基金名称
            val nameMatch = Regex("title='(.*?)'").find(responseBody)
            val fundName = nameMatch?.groupValues?.get(1) ?: "基金名称未知"

            // 提取报告日期
            val dateMatch = Regex("截止至：<font class='px12'>(.*?)</font>").find(responseBody)
            val reportDate = dateMatch?.groupValues?.get(1) ?: "--"

            // 提取持仓数据
            val contentStart = responseBody.indexOf("content:")
            if (contentStart != -1) {
                val quoteStart = responseBody.indexOf('"', contentStart)
                if (quoteStart != -1) {
                    val quoteEnd = responseBody.indexOf('"', quoteStart + 1)
                    if (quoteEnd != -1) {
                        val htmlTable = responseBody.substring(quoteStart + 1, quoteEnd)

                        if (htmlTable.isNotEmpty() && "暂无数据" !in htmlTable) {
                            val holdings = mutableListOf<Holding>()
                            val rowsPattern = Regex("<tr>(.*?)</tr>")
                            val rows = rowsPattern.findAll(htmlTable)

                            for (rowMatch in rows) {
                                val rowHtml = rowMatch.groupValues[1]
                                if ("th" in rowHtml) continue

                                // 提取股票代码和市场
                                val linkPattern = Regex("unify/r/(\\d+)\\.([a-zA-Z0-9]+)")
                                val linkMatch = linkPattern.find(rowHtml)
                                var stockCode = "Unknown"
                                var marketId: String? = null

                                if (linkMatch != null) {
                                    marketId = linkMatch.groupValues[1]
                                    stockCode = linkMatch.groupValues[2]
                                } else {
                                    val colsPattern = Regex("<td.*?>(.*?)</td>")
                                    val cols = colsPattern.findAll(rowHtml).toList()
                                    if (cols.size > 1) {
                                        val tagPattern = Regex("<.*?>")
                                        stockCode = tagPattern.replace(cols[1].groupValues[1], "").trim()
                                    }
                                }

                                // 提取股票名称
                                val colsPattern = Regex("<td.*?>(.*?)</td>")
                                val cols = colsPattern.findAll(rowHtml).toList()
                                if (cols.size < 7) continue

                                val tagPattern = Regex("<.*?>")
                                val stockName = tagPattern.replace(cols[2].groupValues[1], "").trim()

                                // 提取权重
                                val weightStr = tagPattern.replace(cols[6].groupValues[1], "").trim()
                                    .replace('%', ' ').replace(',', ' ')
                                if (weightStr.isEmpty() || weightStr == "--") continue

                                val weight = weightStr.toDoubleOrNull() ?: continue

                                // 生成新浪财经的股票代码
                                val sinaCode = generateSinaCode(marketId, stockCode)

                                holdings.add(Holding(stockCode, stockName, weight, sinaCode))
                            }

                            return FundHoldingsResponse(fundName, holdings, reportDate)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    // 生成新浪财经的股票代码
    private fun generateSinaCode(marketId: String?, stockCode: String): String {
        if (marketId != null) {
            when (marketId) {
                "0" -> return "sz$stockCode" // 深圳
                "1" -> return "sh$stockCode" // 上海
                "116" -> return "rt_hk${stockCode.padStart(5, '0')}" // 香港
                else -> if (marketId.toIntOrNull() ?: 0 >= 100) {
                    return "gb_${stockCode.toLowerCase()}" // 美国
                }
            }
        }

        //  fallback
        if (stockCode.any { it.isLetter() }) {
            return "gb_${stockCode.toLowerCase()}"
        } else if (stockCode.length < 6) {
            return "rt_hk${stockCode.padStart(5, '0')}"
        } else {
            return if (stockCode.startsWith('6') || stockCode.startsWith('5')) {
                "sh$stockCode"
            } else {
                "sz$stockCode"
            }
        }
    }

    // 获取实时股票价格
    fun getRealtimeStockPrices(stockCodes: List<String>): Map<String, StockPrice> {
        val results = mutableMapOf<String, StockPrice>()
        if (stockCodes.isEmpty()) return results

        val listParam = stockCodes.joinToString(",")
        val url = "http://hq.sinajs.cn/list=$listParam"

        val request = Request.Builder()
            .url(url)
            .header("Referer", "http://finance.sina.com.cn/")
            .build()

        try {
            val response: Response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    parseStockPrices(responseBody, results)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return results
    }

    // 解析股票价格
    private fun parseStockPrices(responseBody: String, results: MutableMap<String, StockPrice>) {
        val lines = responseBody.split('\n')
        for (line in lines) {
            if (line.isEmpty() || !line.contains("=")) continue

            val parts = line.split('=')
            if (parts.size < 2) continue

            val key = parts[0].trim().removePrefix("var hq_str_").trim()
            val dataStr = parts[1].trim().removeSurrounding("\"")
            if (dataStr.isEmpty()) continue

            val data = dataStr.split(',')

            var name = "Unknown"
            var price = 0.0
            var changePct = 0.0

            if (key.startsWith("rt_hk")) {
                // 香港股票
                if (data.size >= 9) {
                    name = data[1]
                    price = data[6].toDoubleOrNull() ?: 0.0
                    changePct = data[8].toDoubleOrNull() ?: 0.0
                }
            } else if (key.startsWith("gb_")) {
                // 美国股票
                if (data.size >= 3) {
                    name = data[0]
                    price = data[1].toDoubleOrNull() ?: 0.0
                    changePct = data[2].toDoubleOrNull() ?: 0.0
                }
            } else {
                // A股
                if (data.size >= 4) {
                    name = data[0]
                    val preClose = data[2].toDoubleOrNull() ?: 0.0
                    val currentPrice = data[3].toDoubleOrNull() ?: 0.0
                    price = currentPrice
                    if (preClose > 0) {
                        changePct = ((currentPrice - preClose) / preClose) * 100
                    }
                }
            }

            results[key] = StockPrice(name, price, changePct)
        }
    }

    // 估算基金净值变化
    fun estimateNavChange(holdings: List<Holding>): NavEstimation {
        val stockCodes = holdings.mapNotNull { it.sinaCode }
        val stockPrices = getRealtimeStockPrices(stockCodes)

        var totalWeight = 0.0
        var weightedChange = 0.0
        val details = mutableListOf<HoldingDetail>()

        for (holding in holdings) {
            val stockPrice = stockPrices[holding.sinaCode]
            if (stockPrice != null) {
                totalWeight += holding.weight
                weightedChange += holding.weight * stockPrice.changePct
                details.add(HoldingDetail(
                    holding.code,
                    holding.name,
                    holding.weight,
                    stockPrice.price,
                    stockPrice.changePct
                ))
            }
        }

        val estimatedChange = if (totalWeight > 0) {
            // 使用BigDecimal进行精确计算，避免浮点数精度问题
            val bdWeightedChange = java.math.BigDecimal(weightedChange.toString())
            val bdTotalWeight = java.math.BigDecimal(totalWeight.toString())
            bdWeightedChange.divide(bdTotalWeight, 4, java.math.RoundingMode.HALF_UP).toDouble()
        } else {
            0.0
        }

        return NavEstimation(estimatedChange, totalWeight, details)
    }

    // 数据类
    data class FundHoldingsResponse(
        val fundName: String,
        val holdings: List<Holding>,
        val reportDate: String
    )

    data class Holding(
        val code: String,
        val name: String,
        val weight: Double,
        val sinaCode: String?
    )

    data class StockPrice(
        val name: String,
        val price: Double,
        val changePct: Double
    )

    data class NavEstimation(
        val estimatedChange: Double,
        val totalWeight: Double,
        val details: List<HoldingDetail>
    )

    data class HoldingDetail(
        val code: String,
        val name: String,
        val weight: Double,
        val price: Double,
        val change: Double
    )
}