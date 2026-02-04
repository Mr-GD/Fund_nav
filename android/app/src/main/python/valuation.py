def estimate_nav_change(holdings, prices):
    """
    基于持仓和股票价格估算基金净值变化
    
    Args:
        holdings: 持仓列表，每个元素包含code、name、weight、fetch_code
        prices: 股票价格字典，键为fetch_code，值为包含price和change的字典
    
    Returns:
        dict: 包含estimated_change和details的字典
    """
    if not holdings:
        return {
            'estimated_change': None,
            'details': []
        }
    
    total_weight = sum(h['weight'] for h in holdings)
    if total_weight == 0:
        return {
            'estimated_change': None,
            'details': []
        }
    
    weighted_change = 0.0
    details = []
    
    for holding in holdings:
        fetch_code = holding.get('fetch_code', holding['code'])
        stock_data = prices.get(fetch_code, {})
        
        price = stock_data.get('price', None)
        change = stock_data.get('change', 0.0)
        
        # 计算加权贡献
        contribution = (change * holding['weight']) / total_weight
        weighted_change += contribution
        
        details.append({
            'code': holding['code'],
            'name': holding['name'],
            'weight': holding['weight'],
            'price': price,
            'change': change
        })
    
    return {
        'estimated_change': weighted_change,
        'total_weight_used': total_weight,
        'details': details
    }
