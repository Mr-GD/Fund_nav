package com.example.fundnavapp;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private EditText fundCodeEditText;
    private EditText positionAmountEditText;
    private Button estimateButton;
    private TextView resultTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化Python
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }

        // 绑定UI组件
        fundCodeEditText = findViewById(R.id.fund_code_edit_text);
        positionAmountEditText = findViewById(R.id.position_amount_edit_text);
        estimateButton = findViewById(R.id.estimate_button);
        resultTextView = findViewById(R.id.result_text_view);

        // 设置估算按钮点击事件
        estimateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                estimateFundNav();
            }
        });
    }

    private void estimateFundNav() {
        final String fundCode = fundCodeEditText.getText().toString().trim();
        final String positionAmountStr = positionAmountEditText.getText().toString().trim();

        // 验证输入
        if (fundCode.isEmpty()) {
            Toast.makeText(this, "请输入基金代码", Toast.LENGTH_SHORT).show();
            return;
        }

        if (positionAmountStr.isEmpty()) {
            Toast.makeText(this, "请输入持仓金额", Toast.LENGTH_SHORT).show();
            return;
        }

        final double positionAmount;
        try {
            positionAmount = Double.parseDouble(positionAmountStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "请输入有效的持仓金额", Toast.LENGTH_SHORT).show();
            return;
        }

        // 显示加载提示
        resultTextView.setText("正在估算...");

        // 在后台线程中执行Python代码
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 获取Python实例
                    Python py = Python.getInstance();

                    // 导入main模块
                    PyObject pyMain = py.getModule("main");

                    // 调用estimate_fund_nav函数
                    PyObject result = pyMain.callAttr("estimate_fund_nav", fundCode, positionAmount);

                    // 构建结果文本
                    StringBuilder resultText = new StringBuilder();
                    
                    // 从PyObject中提取数据
                    resultText.append("基金代码: " + result.callAttr("get", "fund_code").toString() + "\n");
                    resultText.append("基金名称: " + result.callAttr("get", "fund_name").toString() + "\n");
                    resultText.append("持仓日期: " + result.callAttr("get", "holding_date").toString() + "\n");
                    resultText.append("状态: " + result.callAttr("get", "status").toString() + "\n");

                    // 处理估算涨跌
                    PyObject estimatedChangeObj = result.callAttr("get", "estimated_change");
                    if (estimatedChangeObj != null && !estimatedChangeObj.toString().equals("None")) {
                        double estimatedChange = estimatedChangeObj.toJava(Double.class);
                        resultText.append("估算涨跌: " + String.format("%.2f%%", estimatedChange) + "\n");
                    } else {
                        resultText.append("估算涨跌: --\n");
                    }

                    // 处理重仓股权重
                    PyObject topHoldingsWeightObj = result.callAttr("get", "top_holdings_weight");
                    if (topHoldingsWeightObj != null && !topHoldingsWeightObj.toString().equals("None")) {
                        double topHoldingsWeight = topHoldingsWeightObj.toJava(Double.class);
                        resultText.append("重仓股权重: " + String.format("%.2f%%", topHoldingsWeight) + "\n");
                    } else {
                        resultText.append("重仓股权重: --\n");
                    }

                    // 处理持仓金额
                    double positionAmountResult = result.callAttr("get", "position_amount").toJava(Double.class);
                    resultText.append("持仓金额: " + String.format("%.2f元", positionAmountResult) + "\n");

                    // 处理估算收益
                    PyObject estimatedProfitObj = result.callAttr("get", "estimated_profit");
                    if (estimatedProfitObj != null && !estimatedProfitObj.toString().equals("None")) {
                        double estimatedProfit = estimatedProfitObj.toJava(Double.class);
                        resultText.append("估算收益: " + String.format("%.2f元", estimatedProfit) + "\n");
                    } else {
                        resultText.append("估算收益: --\n");
                    }

                    // 在主线程中更新UI
                    final String finalResultText = resultText.toString();
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            // 更新结果文本
                            resultTextView.setText(finalResultText);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    // 在主线程中显示错误
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            resultTextView.setText("估算失败: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }
}
