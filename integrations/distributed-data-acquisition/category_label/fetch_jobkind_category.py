"""
从智联招聘HTML中提取职类筛选标签（保留一级和二级分类）
提取所有职类链接，并按一级分类（销售/商务拓展等）和二级分类（销售顾问等）组织
"""

import json
import re
from bs4 import BeautifulSoup
from collections import defaultdict

def extract_cluster_hierarchy(html_file='job.html'):
    """
    提取职类信息，保留一级和二级分类的层级关系
    
    返回结构：
    {
        'cluster_hierarchy': {
            '销售/商务拓展': {
                '销售顾问': ['销售顾问', '客户经理', '大客户代表', ...],
                '其他二级分类': [...]
            },
            '技术类': {...},
            ...
        },
        'all_cluster_links': [...],  # 所有职类平级列表
        'statistics': {...}           # 统计信息
    }
    """
    with open(html_file, encoding='utf-8') as f:
        soup = BeautifulSoup(f.read(), 'html.parser')
    
    result = {
        'cluster_hierarchy': {},
        'all_cluster_links': [],
        'statistics': {}
    }
    
    # 找到职类筛选的整体容器
    cluster_menu = soup.find('div', class_='menu-jobtype')
    if not cluster_menu:
        job_menu = soup.find('div', class_='job-menu')
        if job_menu:
            cluster_menu = job_menu.find('div', class_='menu-jobtype')
    
    if not cluster_menu:
        print("未找到职类筛选菜单")
        return result
    
    print("找到职类筛选菜单，开始解析...")
    
    # 查找所有一级分类的dl结构
    dl_cluster = cluster_menu.find('dl', class_='menu-jobtype--jobtype')
    if not dl_cluster:
        dl_cluster = cluster_menu.find('dl')
    
    if not dl_cluster:
        print("未找到职类列表结构")
        return result
    
    # 遍历每个dd元素（代表一个一级分类）
    dd_elements = dl_cluster.find_all('dd')
    print(f"找到 {len(dd_elements)} 个一级分类")
    
    for dd in dd_elements:
        # 获取一级分类名称
        title_div = dd.find('div', class_='menu__item-title')
        if not title_div:
            continue
        
        title_link = title_div.find('a')
        if not title_link:
            continue
        
        first_level = title_link.get_text(strip=True)
        result['cluster_hierarchy'][first_level] = {}
        print(f"\n解析一级分类: {first_level}")
        
        # 查找展开的职类列表
        expand_div = dd.find('div', class_='menu__expand')
        if not expand_div:
            print(f"  {first_level}: 未找到展开区域")
            continue
        
        # 查找所有li元素（每个li代表一个二级分类）
        li_elements = expand_div.find_all('li')
        print(f"  找到 {len(li_elements)} 个二级分类")
        
        for li in li_elements:
            # 获取二级分类名称（h4标签）
            h4 = li.find('h4')
            if not h4:
                continue
            
            second_level = h4.get_text(strip=True)
            result['cluster_hierarchy'][first_level][second_level] = []
            
            # 获取该二级分类下的所有职类
            third_div = li.find('div', class_='menu__expand__third-name')
            if third_div:
                cluster_links = third_div.find_all('a')
                for link in cluster_links:
                    name = link.get_text(strip=True)
                    href = link.get('href', '')
                    
                    if name and name not in ['全部职类', '']:
                        # 提取职类代码
                        cluster_code = None
                        if 'jobLevel=' in href:
                            match = re.search(r'jobLevel=(\d+)', href)
                            if match:
                                cluster_code = match.group(1)
                        
                        result['cluster_hierarchy'][first_level][second_level].append(name)
                        
                        result['all_cluster_links'].append({
                            'name': name,
                            'code': cluster_code,
                            'first_level': first_level,
                            'second_level': second_level,
                            'url': href
                        })
                
                print(f"    {second_level}: {len(result['cluster_hierarchy'][first_level][second_level])} 个职类")
    
    # 统计信息
    total_first = len(result['cluster_hierarchy'])
    total_second = sum(len(second) for second in result['cluster_hierarchy'].values())
    total_clusters = len(result['all_cluster_links'])
    
    result['statistics'] = {
        'first_level_count': total_first,
        'second_level_count': total_second,
        'cluster_count': total_clusters,
        'first_levels': list(result['cluster_hierarchy'].keys())
    }
    
    return result

def print_cluster_hierarchy(hierarchy_data, max_items=5):
    """打印职类层级结构"""
    for first_level, second_levels in hierarchy_data.items():
        print(f"\n【{first_level}】")
        for second_level, clusters in second_levels.items():
            print(f"  ├─ {second_level} ({len(clusters)}个职类)")
            if clusters:
                preview = clusters[:min(max_items, len(clusters))]
                for cluster in preview:
                    print(f"  │    └─ {cluster}")
                if len(clusters) > max_items:
                    print(f"  │    └─ ... 还有 {len(clusters) - max_items} 个职类")

def main():
    """主函数"""
    print("=" * 70)
    print("智联招聘职类筛选标签提取工具（保留一级和二级分类）")
    print("=" * 70)
    
    # 尝试多个可能的文件名
    possible_files = ['job.html', 'menu.html', 'jobtype.html', 'cluster.html']
    extracted_data = None
    
    for filename in possible_files:
        try:
            print(f"\n尝试读取文件: {filename}")
            extracted_data = extract_cluster_hierarchy(filename)
            if extracted_data['statistics'].get('cluster_count', 0) > 0:
                print(f"成功从 {filename} 提取数据")
                break
        except FileNotFoundError:
            print(f"文件 {filename} 不存在")
            continue
    
    if not extracted_data or extracted_data['statistics'].get('cluster_count', 0) == 0:
        print("\n❌ 无法提取职类数据，请确保以下文件之一存在且包含职类内容:")
        print("   - job.html")
        print("   - menu.html")
        print("   - jobtype.html")
        return
    
    # 显示统计信息
    stats = extracted_data['statistics']
    print(f"\n📊 提取统计:")
    print(f"  - 一级分类: {stats.get('first_level_count', 0)} 个")
    print(f"  - 二级分类: {stats.get('second_level_count', 0)} 个")
    print(f"  - 职类总数: {stats.get('cluster_count', 0)} 个")
    
    # 显示层级结构示例
    if extracted_data['cluster_hierarchy']:
        print("\n📁 职类层级结构示例:")
        print_cluster_hierarchy(extracted_data['cluster_hierarchy'], max_items=3)
    
    # 保存完整结果
    output = {
        'hierarchy': extracted_data['cluster_hierarchy'],
        'all_clusters': extracted_data['all_cluster_links'],
        'statistics': extracted_data['statistics'],
        'usage_example': {
            'note': '可以通过以下方式访问数据',
            'get_first_levels': "list(data['hierarchy'].keys())",
            'get_second_levels': "list(data['hierarchy']['销售/商务拓展'].keys())",
            'get_clusters': "data['hierarchy']['销售/商务拓展']['销售顾问']"
        }
    }
    
    with open('cluster_hierarchy.json', 'w', encoding='utf-8') as f:
        json.dump(output, f, ensure_ascii=False, indent=2)
    
    # 生成职类代码字典
    cluster_codes = {}
    for item in extracted_data['all_cluster_links']:
        if item.get('code'):
            cluster_codes[item['code']] = item['name']
    
    with open('cluster_codes.json', 'w', encoding='utf-8') as f:
        json.dump({
            'total': len(cluster_codes),
            'clusters': cluster_codes
        }, f, ensure_ascii=False, indent=2)
    
    # 生成按一级分类组织的职类代码
    by_first_level = {}
    for item in extracted_data['all_cluster_links']:
        first = item['first_level']
        if first not in by_first_level:
            by_first_level[first] = {}
        second = item['second_level']
        if second not in by_first_level[first]:
            by_first_level[first][second] = []
        by_first_level[first][second].append({
            'code': item['code'],
            'name': item['name']
        })
    
    with open('clusters_by_level.json', 'w', encoding='utf-8') as f:
        json.dump(by_first_level, f, ensure_ascii=False, indent=2)
    
    print("\n" + "=" * 70)
    print("✅ 结果已保存到以下文件:")
    print("  1. cluster_hierarchy.json - 完整层级结构（一级→二级→职类）")
    print("  2. cluster_codes.json - 职类代码字典（code→name）")
    print("  3. clusters_by_level.json - 按一级和二级分类组织的职类（含代码）")
    
    # 显示一级分类列表
    if extracted_data['cluster_hierarchy']:
        print("\n📋 一级分类列表:")
        for first_level in extracted_data['cluster_hierarchy'].keys():
            print(f"  - {first_level}")

if __name__ == "__main__":
    main()