import streamlit as st
import pandas as pd
import time
from datetime import datetime
import logging
import sqlite3
import os

from src.data_fetcher import get_fund_holdings, get_realtime_stock_prices, get_fund_history_nav
from src.valuation import estimate_nav_change

# Database setup
db_path = 'funds.db'

def init_db():
    """Initialize the SQLite database and create tables if they don't exist."""
    conn = sqlite3.connect(db_path)
    c = conn.cursor()
    
    # Create funds table with fund_code as unique key
    c.execute('''
    CREATE TABLE IF NOT EXISTS funds (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        fund_code TEXT UNIQUE NOT NULL,
        fund_name TEXT,
        current_amount REAL NOT NULL,
        current_holding_profit REAL NOT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    )
    ''')
    
    # Create index on fund_code for faster lookup
    c.execute('CREATE INDEX IF NOT EXISTS idx_fund_code ON funds (fund_code)')
    
    conn.commit()
    conn.close()

# Initialize database on app start
init_db()

def get_all_funds():
    """Get all funds from the database."""
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    c = conn.cursor()
    c.execute('SELECT * FROM funds ORDER BY fund_code')
    funds = [dict(row) for row in c.fetchall()]
    conn.close()
    return funds

def add_fund(fund_code, current_amount, fund_name=''):
    """Add a new fund to the database."""
    # Get fund name from API if not provided
    if not fund_name:
        try:
            result_data = get_fund_holdings(fund_code)
            if result_data:
                if len(result_data) == 3:
                    fund_name = result_data[0]
                else:
                    fund_name = result_data[0]
        except Exception as e:
            logging.warning(f"Error fetching fund name for {fund_code}: {e}")
            # Keep empty fund name if API call fails
    
    conn = sqlite3.connect(db_path)
    c = conn.cursor()
    try:
        c.execute('''
        INSERT OR REPLACE INTO funds (fund_code, fund_name, current_amount, current_holding_profit, updated_at)
        VALUES (?, ?, ?, 0, CURRENT_TIMESTAMP)
        ''', (fund_code, fund_name, current_amount))
        conn.commit()
        return True
    except Exception as e:
        logging.error(f"Error adding fund {fund_code}: {e}")
        return False
    finally:
        conn.close()

def delete_fund(fund_code):
    """Delete a fund from the database."""
    conn = sqlite3.connect(db_path)
    c = conn.cursor()
    try:
        c.execute('DELETE FROM funds WHERE fund_code = ?', (fund_code,))
        conn.commit()
        return True
    except Exception as e:
        logging.error(f"Error deleting fund {fund_code}: {e}")
        return False
    finally:
        conn.close()

def update_fund(fund_code, current_amount, current_holding_profit, fund_name=''):
    """Update a fund in the database."""
    conn = sqlite3.connect(db_path)
    c = conn.cursor()
    try:
        c.execute('''
        UPDATE funds SET fund_name = ?, current_amount = ?, current_holding_profit = ?, updated_at = CURRENT_TIMESTAMP
        WHERE fund_code = ?
        ''', (fund_name, current_amount, current_holding_profit, fund_code))
        conn.commit()
        return True
    except Exception as e:
        logging.error(f"Error updating fund {fund_code}: {e}")
        return False
    finally:
        conn.close()

# Configure page
st.set_page_config(page_title="基金净值估算器", layout="wide")

st.title("🇨🇳 中国公募基金实时净值估算系统")
st.markdown("基于前十大重仓股实时估算基金净值涨跌幅。")

# Sidebar
st.sidebar.header("配置")

# Database management in sidebar
with st.sidebar:
    st.subheader("基金管理")
    
    # View all funds
    funds = get_all_funds()
    
    if funds:
        st.write("### 现有基金")
        for fund in funds:
            with st.expander(f"{fund['fund_code']} - {fund['fund_name'] or '未命名'}"):
                col1, col2 = st.columns(2)
                with col1:
                    st.write(f"**当前持仓金额:** ¥{fund['current_amount']:.2f}")
                    st.write(f"**当前持有收益:** ¥{fund['current_holding_profit']:.2f}")
                with col2:
                    if st.button(f"删除 {fund['fund_code']}", key=f"delete_{fund['fund_code']}"):
                        if delete_fund(fund['fund_code']):
                            st.success(f"基金 {fund['fund_code']} 已删除")
                            st.rerun()
                        else:
                            st.error(f"删除基金 {fund['fund_code']} 失败")
    else:
        st.write("### 暂无基金，请添加")
    
    # Add new fund
    st.write("### 添加新基金")
    with st.form("add_fund_form"):
        new_fund_code = st.text_input("基金代码", help="例如：002611")
        new_fund_name = st.text_input("基金名称 (可选)")
        new_current_amount = st.number_input("当前持仓金额", min_value=0.0, value=10000.0, step=100.0, format="%.2f")
        submitted = st.form_submit_button("添加基金")
        
        if submitted:
            if new_fund_code:
                # Add fund to database
                if add_fund(new_fund_code, new_current_amount, new_fund_name):
                    # Show success message
                    st.success(f"基金 {new_fund_code} 已添加")
                    # Rerun the app to show updated data
                    st.rerun()
                else:
                    st.error(f"添加基金 {new_fund_code} 失败")
            else:
                st.error("请输入基金代码")

auto_refresh = st.sidebar.checkbox("自动刷新 (每60秒)", value=False)
refresh_btn = st.sidebar.button("立即刷新")

# Main Logic
from concurrent.futures import ThreadPoolExecutor, as_completed

@st.cache_data(ttl=3600)
def fetch_history_cached(code, days):
    return get_fund_history_nav(code, days)

def process_single_fund(code, position_amount=10000.0):
    """Background worker to fetch data for a single fund."""
    try:
        # 1. Fetch Holdings
        result_data = get_fund_holdings(code)
        
        if not result_data:
            return {
                '基金代码': code,
                '基金名称': '--',
                '持仓日期': '--',
                '状态': '获取持仓失败',
                '估算涨跌': None,
                '重仓股权重': None,
                '持仓金额': position_amount,
                '估算收益': None,
                'Details': []
            }
            
        # Unpack tuple
        if len(result_data) == 3:
             fund_name, holdings, report_date = result_data
        else:
             fund_name, holdings = result_data
             report_date = "--"
        
        # 2. Fetch Prices
        stock_fetch_codes = [h.get('fetch_code', h['code']) for h in holdings]
        prices = get_realtime_stock_prices(stock_fetch_codes)
        
        # 3. Estimate
        valuation = estimate_nav_change(holdings, prices)
        
        # 4. Calculate estimated profit
        estimated_change = valuation['estimated_change']
        estimated_profit = position_amount * (estimated_change / 100) if estimated_change is not None else None
        
        # 5. Fetch History (Last 365 days for flexibility)
        # Cached to avoid heavy network io
        history_df = fetch_history_cached(code, days=365)
        
        return {
            '基金代码': code,
            '基金名称': fund_name,
            '持仓日期': report_date,
            '状态': '成功',
            '估算涨跌': estimated_change,
            '重仓股权重': valuation['total_weight_used'],
            '持仓金额': position_amount,
            '估算收益': estimated_profit,
            'Details': valuation['details'],
            'History': history_df, # Add history
            '更新时间': datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        }
    except Exception as e:
        logging.error(f"Error processing {code}: {e}")
        return {
            '基金代码': code,
            '基金名称': 'Error',
            '持仓日期': '--',
            '状态': f'Error: {str(e)}',
            '估算涨跌': None,
            '重仓股权重': None,
            '持仓金额': position_amount,
            '估算收益': None,
            'Details': []
        }

def color_change(val):
    """Return CSS color based on value positive/negative."""
    if val is None:
        return ''
    try:
        val = float(val)
        if val > 0:
            return 'color: #d63031; font-weight: bold;'
        elif val < 0:
            return 'color: #00b894; font-weight: bold;'
        else:
            return ''
    except (ValueError, TypeError):
        return ''

def process_funds(funds_with_amounts):
    results = []
    total = len(funds_with_amounts)
    progress_bar = st.progress(0)
    status_text = st.empty()
    
    status_text.text("正在并发获取数据...")
    
    with ThreadPoolExecutor(max_workers=5) as executor:
        # Create map of future -> (code, current_amount, source) for each fund
        futures_map = {executor.submit(process_single_fund, code, current_amount): (code, current_amount, source) for code, current_amount, source in funds_with_amounts}
        
        completed_count = 0
        
        # Process completed futures
        for future in as_completed(futures_map):
            completed_count += 1
            progress_bar.progress(completed_count / len(futures_map))
            try:
                data = future.result()
                # No need to calculate estimated total holding profit and remaining amount
                results.append(data)
            except Exception as e:
                code, current_amount, source = futures_map[future]
                logging.error(f"Future blocked for {code}: {e}")
                # Add error entry
                results.append({
                    '基金代码': code,
                    '基金名称': '--',
                    '持仓日期': '--',
                    '状态': f'处理失败: {str(e)}',
                    '估算涨跌': None,
                    '重仓股权重': None,
                    '持仓金额': current_amount,
                    '估算收益': None,
                    'Details': []
                })
                
    status_text.empty()
    progress_bar.empty()
    
    # Sort results to match input order
    code_to_index = {code: i for i, (code, _, _) in enumerate(funds_with_amounts)}
    results.sort(key=lambda x: code_to_index.get(x['基金代码'], 999))
    
    return results

# Only get funds from database
db_funds = get_all_funds()
funds_with_amounts = [(fund['fund_code'], fund['current_amount'], 'database') for fund in db_funds]

codes = [item[0] for item in funds_with_amounts]

if not codes:
    st.warning("请在数据库中添加基金。")
    st.stop()

# Container for the dashboard
dashboard = st.empty()

def render_dashboard():
    with dashboard.container():
        data = process_funds(funds_with_amounts)
        
        if not data:
            st.error("未找到数据。")
            return

        # Summary Table
        st.subheader("概览")
        
        if data:
            # Create a dataframe with the results
            df = pd.DataFrame(data)
            
            # Reorder columns to match the desired order
            columns_order = ['基金代码', '基金名称', '持仓日期', '估算涨跌', '重仓股权重', '持仓金额', '估算收益', '状态', '更新时间']
            df = df[columns_order]
            
            # Display the dataframe with borders and color styling
            styler = df.style\
                .format({'估算涨跌': "{:+.2f}%", '重仓股权重': "{:.2f}%", '持仓金额': "{:.2f}", '估算收益': "{:+.2f}"}, na_rep="--")\
                .map(color_change, subset=['估算涨跌', '估算收益'])
            
            st.dataframe(styler, use_container_width=True, hide_index=True)
        else:
            st.warning("未找到数据。")
        
        # Update Intraday History Logic (Restored)
        for item in data:
            if item['状态'] == '成功' and item['估算涨跌'] is not None:
                f_code = item['基金代码']
                if 'fund_intraday' not in st.session_state:
                    st.session_state['fund_intraday'] = {}
                
                if f_code not in st.session_state['fund_intraday']:
                    st.session_state['fund_intraday'][f_code] = pd.DataFrame(columns=['Time', 'Estimate'])
                
                current_time = datetime.now().strftime("%H:%M")
                
                # Simple append
                new_row = pd.DataFrame({'Time': [current_time], 'Estimate': [item['估算涨跌']]})
                st.session_state['fund_intraday'][f_code] = pd.concat([st.session_state['fund_intraday'][f_code], new_row], ignore_index=True)
        
        # Detail Expander
        st.subheader("详细信息")
        tabs = st.tabs([f"{d['基金代码']}" for d in data])
        
        for i, tab in enumerate(tabs):
            with tab:
                item = data[i]
                if item['状态'] == '成功':
                    # --- Metrics Row ---
                    c1, c2, c3, c4, c5, c6, c7 = st.columns(7)
                    with c1:
                        st.metric("实时估算涨跌", f"{item['估算涨跌']:+.2f}%", delta=None)
                    with c2:
                         st.metric("前十大持仓占比", f"{item['重仓股权重']:.2f}%")
                    with c3:
                         st.metric("持仓报告期", item['持仓日期'])
                    with c4:
                         st.metric("持仓金额", f"{item['持仓金额']:.2f}元")
                    with c5:
                         st.metric("估算收益", f"{item['估算收益']:+.2f}元" if item['估算收益'] is not None else "--")
                    with c6:
                         st.metric("更新时间", item.get('更新时间', '--'))
                    with c7:
                         st.metric("数据状态", item.get('状态', '--'))
                    
                    st.divider()
                    
                    # --- Charts Area (Tabs) ---
                    chart_tab1, chart_tab2 = st.tabs(["📉 实时分时走势", "📅 历史净值趋势"])
                    
                    with chart_tab1:
                         # Intraday Chart
                         f_code = item['基金代码']
                         if 'fund_intraday' in st.session_state and f_code in st.session_state['fund_intraday']:
                             df_intra = st.session_state['fund_intraday'][f_code]
                             if not df_intra.empty:
                                 # Use Altair for consistency
                                 import altair as alt
                                 chart_intra = alt.Chart(df_intra).mark_line(color='#FFA500').encode(
                                     x=alt.X('Time', title='时间'),
                                     y=alt.Y('Estimate', title='估算涨跌(%)', scale=alt.Scale(zero=False))
                                 ).properties(height=250)
                                 st.altair_chart(chart_intra, use_container_width=True)
                             else:
                                 st.info("暂无今日实时数据，请等待刷新...")
                         else:
                             st.info("数据收集中...")
                    
                    with chart_tab2:
                        # Historical Chart
                        if 'History' in item and item['History'] is not None and not item['History'].empty:
                            # Date Range Selector
                            range_map = {'1周': 7, '1月': 30, '3月': 90, '6月': 180, '1年': 365}
                            selected_range = st.radio(
                                "时间范围", 
                                list(range_map.keys()), 
                                index=1, 
                                key=f"range_{item['基金代码']}",
                                horizontal=True,
                                label_visibility="collapsed"
                            )
                            
                            days_limit = range_map[selected_range]
                            hist_df = item['History']
                            
                            # Filter
                            start_date = pd.Timestamp.now() - pd.Timedelta(days=days_limit)
                            chart_df = hist_df[hist_df['date'] >= start_date]
                            
                            import altair as alt
                            chart_hist = alt.Chart(chart_df).mark_line().encode(
                                x=alt.X('date', title='日期', axis=alt.Axis(format='%m-%d')),
                                y=alt.Y('nav', title='单位净值', scale=alt.Scale(zero=False)),
                                tooltip=['date', 'nav']
                            ).properties(height=250)
                            st.altair_chart(chart_hist, use_container_width=True)
                        else:
                            st.warning("暂无历史数据")

                    st.caption("注意：估值仅基于已披露的前十大重仓股，并已归一化处理。")
                    
                    # --- Holdings Table ---
                    with st.expander("查看重仓股详情", expanded=False):
                        details = item['Details']
                        df_det = pd.DataFrame(details)
                        
                        if not df_det.empty:
                            df_det = df_det[['code', 'name', 'weight', 'price', 'change']]
                            df_det.columns = ['代码', '名称', '权重(%)', '现价', '涨跌(%)']
                            # Fill None values in numeric columns to prevent format errors
                            numeric_cols = ['权重(%)', '现价', '涨跌(%)']
                            for col in numeric_cols:
                                if col in df_det.columns:
                                    df_det[col] = df_det[col].fillna(0.0)
                            
                            # Style highlights
                            def highlight_change(val):
                                if val is None or not isinstance(val, (int, float)):
                                    return ''
                                color = '#d63031' if val > 0 else '#00b894' if val < 0 else ''
                                return f'color: {color}'
                                
                            st.dataframe(
                                df_det.style.map(highlight_change, subset=['涨跌(%)'])
                                            .format({'权重(%)': "{:.2f}", '现价': "{:.2f}", '涨跌(%)': "{:+.2f}"}),
                                use_container_width=True
                            )
                        else:
                            st.info("暂无持仓详情。")
                else:
                    st.error(f"获取数据失败: {item.get('状态', 'Unknown Error')}")

# Main Loop Logic
if auto_refresh:
    while True:
        render_dashboard()
        time.sleep(60)
        st.rerun()
else:
    render_dashboard()

if refresh_btn:
    st.rerun()