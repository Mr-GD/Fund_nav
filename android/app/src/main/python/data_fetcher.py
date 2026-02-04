import requests
from bs4 import BeautifulSoup
import pandas as pd
import re

# 获取基金持仓
def get_fund_holdings(fund_code):
    """获取基金前十大重仓股"""
    url = f"http://fundf10.eastmoney.com/ccmx_{fund_code}.html"
    try:
        response = requests.get(url, headers={
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
        }, timeout=10)
        response.encoding = 'utf-8'
        soup = BeautifulSoup(response.text, 'lxml')
        
        # 获取基金名称
        fund_name_elem = soup.select_one('.fundDetail-tit .funCurFundName')
        fund_name = fund_name_elem.text.strip() if fund_name_elem else f"基金{fund_code}"
        
        # 获取持仓表格
        holding_table = soup.select_one('#cctable tbody')
        if not holding_table:
            return None
        
        holdings = []
        rows = holding_table.select('tr')
        for row in rows[:10]:  # 只取前十大重仓股
            cols = row.select('td')
            if len(cols) >= 4:
                code = cols[1].text.strip()
                name = cols[2].text.strip()
                weight = float(cols[3].text.strip().replace('%', ''))
                
                # 处理股票代码（添加市场前缀）
                if code.startswith('6'):
                    fetch_code = f"sh{code}"
                else:
                    fetch_code = f"sz{code}"
                
                holdings.append({
                    'code': code,
                    'name': name,
                    'weight': weight,
                    'fetch_code': fetch_code
                })
        
        # 获取报告日期
        report_date_elem = soup.select_one('.subtitle .time')
        report_date = report_date_elem.text.strip() if report_date_elem else "--"
        
        return fund_name, holdings, report_date
    except Exception as e:
        print(f"获取基金{fund_code}持仓失败: {e}")
        return None

# 获取实时股票价格
def get_realtime_stock_prices(stock_codes):
    """获取实时股票价格"""
    if not stock_codes:
        return {}
    
    # 构建请求URL
    codes_str = ",".join(stock_codes)
    url = f"http://hq.sinajs.cn/list={codes_str}"
    
    try:
        response = requests.get(url, timeout=10)
        response.encoding = 'gbk'
        
        prices = {}
        lines = response.text.strip().split('\n')
        
        for line in lines:
            if '=' in line:
                parts = line.split('=')
                code = parts[0].split('_')[1]
                data_str = parts[1].strip('"')
                data = data_str.split(',')
                
                if len(data) >= 4:
                    try:
                        price = float(data[3])  # 最新价
                        prev_close = float(data[2])  # 昨收价
                        change = (price - prev_close) / prev_close * 100
                        
                        prices[code] = {
                            'price': price,
                            'change': change
                        }
                    except (ValueError, IndexError):
                        continue
        
        return prices
    except Exception as e:
        print(f"获取股票价格失败: {e}")
        return {}

# 获取基金历史净值
def get_fund_history_nav(fund_code, days=365):
    """获取基金历史净值"""
    url = f"http://fund.eastmoney.com/f10/F10DataApi.aspx?type=lsjz&code={fund_code}&page=1&per={days}&sdate=&edate="
    
    try:
        response = requests.get(url, headers={
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
        }, timeout=10)
        response.encoding = 'utf-8'
        
        # 提取HTML表格
        html_match = re.search(r'<table[^>]*>.*?</table>', response.text, re.DOTALL)
        if not html_match:
            return pd.DataFrame()
        
        html_table = html_match.group(0)
        df = pd.read_html(html_table)[0]
        
        # 清理数据
        df = df.dropna(subset=['净值日期'])
        df['date'] = pd.to_datetime(df['净值日期'])
        df['nav'] = pd.to_numeric(df['单位净值'], errors='coerce')
        df = df[['date', 'nav']].sort_values('date')
        
        return df
    except Exception as e:
        print(f"获取基金历史净值失败: {e}")
        return pd.DataFrame()
