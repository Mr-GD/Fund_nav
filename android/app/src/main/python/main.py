import sys
import os
from data_fetcher import get_fund_holdings, get_realtime_stock_prices, get_fund_history_nav
from valuation import estimate_nav_change

# 确保输出到控制台
sys.stdout = open(os.devnull, 'w')
sys.stderr = open(os.devnull, 'w')

# 基金净值估算函数
def estimate_fund_nav(fund_code, position_amount):
    """
    估算基金净值变化和收益
    
    Args:
        fund_code: 基金代码
        position_amount: 持仓金额
    
    Returns:
        dict: 包含基金信息和估算结果的字典
    """
    try:
        # 1. 获取基金持仓
        result_data = get_fund_holdings(fund_code)
        
        if not result_data:
            return {
                'fund_code': fund_code,
                'fund_name': '--',
                'holding_date': '--',
                'status': '获取持仓失败',
                'estimated_change': None,
                'top_holdings_weight': None,
                'position_amount': position_amount,
                'estimated_profit': None
            }
        
        # 解析结果
        if len(result_data) == 3:
            fund_name, holdings, report_date = result_data
        else:
            fund_name, holdings = result_data
            report_date = "--"
        
        # 2. 获取股票价格
        stock_fetch_codes = [h.get('fetch_code', h['code']) for h in holdings]
        prices = get_realtime_stock_prices(stock_fetch_codes)
        
        # 3. 估算净值变化
        valuation = estimate_nav_change(holdings, prices)
        
        # 4. 计算估算收益
        estimated_change = valuation['estimated_change']
        estimated_profit = position_amount * (estimated_change / 100) if estimated_change is not None else None
        
        # 5. 准备返回数据
        return {
            'fund_code': fund_code,
            'fund_name': fund_name,
            'holding_date': report_date,
            'status': '成功',
            'estimated_change': estimated_change,
            'top_holdings_weight': valuation['total_weight_used'],
            'position_amount': position_amount,
            'estimated_profit': estimated_profit
        }
    except Exception as e:
        print(f"估算基金{fund_code}净值失败: {e}")
        return {
            'fund_code': fund_code,
            'fund_name': '--',
            'holding_date': '--',
            'status': f'处理失败: {str(e)}',
            'estimated_change': None,
            'top_holdings_weight': None,
            'position_amount': position_amount,
            'estimated_profit': None
        }

# 批量估算基金净值
def batch_estimate_funds(funds_data):
    """
    批量估算多个基金的净值变化
    
    Args:
        funds_data: 基金数据列表，每个元素包含fund_code和position_amount
    
    Returns:
        list: 估算结果列表
    """
    results = []
    for fund_data in funds_data:
        fund_code = fund_data['fund_code']
        position_amount = fund_data['position_amount']
        result = estimate_fund_nav(fund_code, position_amount)
        results.append(result)
    return results

# 测试函数
if __name__ == "__main__":
    # 测试单个基金估算
    test_result = estimate_fund_nav('002611', 10000)
    print("测试结果:")
    print(f"基金代码: {test_result['fund_code']}")
    print(f"基金名称: {test_result['fund_name']}")
    print(f"估算涨跌: {test_result['estimated_change']:.2f}%")
    print(f"估算收益: {test_result['estimated_profit']:.2f}元")
