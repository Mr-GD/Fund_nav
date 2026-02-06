package com.example.fundnavapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.ImageButton

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var fundAdapter: FundAdapter
    private lateinit var addButton: FloatingActionButton
    private lateinit var refreshButton: ImageButton
    private val fundRepository = FundRepository(this)
    private val fundApiService = FundApiService()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化视图
        recyclerView = findViewById(R.id.recyclerView)
        addButton = findViewById(R.id.addButton)
        refreshButton = findViewById(R.id.refreshButton)

        // 配置RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        fundAdapter = FundAdapter(
            emptyList(),
            { fund ->
                // 点击基金项，跳转到详情页
                val intent = Intent(this, FundDetailActivity::class.java)
                intent.putExtra("fundCode", fund.fundCode)
                intent.putExtra("fundName", fund.fundName)
                intent.putExtra("currentAmount", fund.currentAmount)
                intent.putExtra("dailyChange", fund.dailyChange)
                intent.putExtra("dailyProfit", fund.dailyProfit)
                startActivity(intent)
            },
            { fund ->
                // 点击删除按钮
                fundRepository.deleteFund(fund.fundCode)
                loadFunds()
            }
        )
        recyclerView.adapter = fundAdapter

        // 添加按钮点击事件
        addButton.setOnClickListener {
            showAddFundDialog()
        }

        // 刷新按钮点击事件
        refreshButton.setOnClickListener {
            loadFunds()
        }

        // 加载基金列表
        loadFunds()
    }

    private fun showAddFundDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_fund, null)
        val fundCodeEditText = dialogView.findViewById<TextInputEditText>(R.id.fundCodeEditText)
        val fundNameEditText = dialogView.findViewById<TextInputEditText>(R.id.fundNameEditText)
        val amountEditText = dialogView.findViewById<TextInputEditText>(R.id.amountEditText)

        AlertDialog.Builder(this)
            .setTitle(R.string.add_fund)
            .setView(dialogView)
            .setPositiveButton(R.string.add) { _, _ ->
                val fundCode = fundCodeEditText.text.toString().trim()
                val fundName = fundNameEditText.text.toString().trim()
                val amount = amountEditText.text.toString().toDoubleOrNull()

                if (fundCode.isNotEmpty() && amount != null) {
                    GlobalScope.launch(Dispatchers.IO) {
                        // 尝试从API获取基金名称
                        var finalFundName = fundName
                        if (finalFundName.isEmpty()) {
                            val holdingsResponse = fundApiService.getFundHoldings(fundCode)
                            if (holdingsResponse != null) {
                                finalFundName = holdingsResponse.fundName
                            }
                        }

                        val fund = Fund(fundCode, finalFundName, amount, 0.0, 0.0, 0.0)
                        fundRepository.addFund(fund)
                        withContext(Dispatchers.Main) {
                            loadFunds()
                        }
                    }
                } else {
                    Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun loadFunds() {
        GlobalScope.launch(Dispatchers.IO) {
            val funds = fundRepository.getAllFunds()
            // 为每个基金从API获取当日涨幅和当日收益
            val updatedFunds = funds.map {
                try {
                    // 从API获取基金持仓数据
                    val holdingsResponse = fundApiService.getFundHoldings(it.fundCode)
                    if (holdingsResponse != null && holdingsResponse.holdings.isNotEmpty()) {
                        // 估算净值变化
                        val estimation = fundApiService.estimateNavChange(holdingsResponse.holdings)
                        val dailyChange = estimation.estimatedChange
                        // 使用BigDecimal进行精确计算，避免浮点数精度问题
                        val bdAmount = java.math.BigDecimal(it.currentAmount.toString())
                        val bdChange = java.math.BigDecimal(dailyChange.toString())
                        val bdHundred = java.math.BigDecimal("100")
                        val dailyProfit = bdAmount.multiply(bdChange).divide(bdHundred, 2, java.math.RoundingMode.HALF_UP).toDouble()
                        it.copy(dailyChange = dailyChange, dailyProfit = dailyProfit)
                    } else {
                        // 如果API获取失败，使用默认值
                        it
                    }
                } catch (e: Exception) {
                    // 发生异常时使用默认值
                    e.printStackTrace()
                    it
                }
            }
            withContext(Dispatchers.Main) {
                fundAdapter.updateFunds(updatedFunds)
            }
        }
    }
}

// 基金数据类
data class Fund(
    val fundCode: String,
    val fundName: String,
    val currentAmount: Double,
    val currentHoldingProfit: Double,
    val dailyChange: Double = 0.0,
    val dailyProfit: Double = 0.0
)

// 基金适配器
class FundAdapter(
    private var funds: List<Fund>,
    private val onFundClick: (Fund) -> Unit,
    private val onDeleteClick: (Fund) -> Unit
) : RecyclerView.Adapter<FundAdapter.FundViewHolder>() {

    class FundViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val fundCodeTextView: android.widget.TextView = itemView.findViewById(R.id.fundCodeTextView)
        val fundNameTextView: android.widget.TextView = itemView.findViewById(R.id.fundNameTextView)
        val dailyChangeTextView: android.widget.TextView = itemView.findViewById(R.id.dailyChangeTextView)
        val dailyProfitTextView: android.widget.TextView = itemView.findViewById(R.id.dailyProfitTextView)
        val holdingAmountTextView: android.widget.TextView = itemView.findViewById(R.id.holdingAmountTextView)
        val detailButton: android.widget.Button = itemView.findViewById(R.id.detailButton)
        val deleteButton: android.widget.Button = itemView.findViewById(R.id.deleteButton)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): FundViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_fund, parent, false)
        return FundViewHolder(view)
    }

    override fun onBindViewHolder(holder: FundViewHolder, position: Int) {
        val fund = funds[position]
        holder.fundCodeTextView.text = fund.fundCode
        holder.fundNameTextView.text = fund.fundName
        holder.dailyChangeTextView.text = "${String.format("%.2f", fund.dailyChange)}%"
        holder.dailyProfitTextView.text = "¥${String.format("%.2f", fund.dailyProfit)}"
        holder.holdingAmountTextView.text = "¥${String.format("%.2f", fund.currentAmount)}"

        // 设置当日涨幅文本颜色
        holder.dailyChangeTextView.setTextColor(
            if (fund.dailyChange >= 0) {
                holder.itemView.context.getColor(R.color.red)
            } else {
                holder.itemView.context.getColor(R.color.green)
            }
        )

        // 设置当日收益文本颜色
        holder.dailyProfitTextView.setTextColor(
            if (fund.dailyProfit >= 0) {
                holder.itemView.context.getColor(R.color.red)
            } else {
                holder.itemView.context.getColor(R.color.green)
            }
        )

        holder.detailButton.setOnClickListener {
            onFundClick(fund)
        }

        holder.deleteButton.setOnClickListener {
            onDeleteClick(fund)
        }
    }

    override fun getItemCount(): Int = funds.size

    fun updateFunds(newFunds: List<Fund>) {
        funds = newFunds
        notifyDataSetChanged()
    }
}
