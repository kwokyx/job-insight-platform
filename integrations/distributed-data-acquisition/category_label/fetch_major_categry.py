"""
从智联招聘HTML中提取专业筛选标签（保留一级和二级分类）
提取所有专业链接，并按一级分类（工学、理学等）和二级分类（安全科学与工程类等）组织
"""

import json
import re
from bs4 import BeautifulSoup
from collections import defaultdict

def extract_major_hierarchy(html_file='menu.html'):
    """
    提取专业信息，保留一级和二级分类的层级关系
    
    返回结构：
    {
        'major_hierarchy': {
            '工学': {
                '安全科学与工程类': ['安全工程', '安全生产监管'],
                '兵器类': ['弹药工程与爆炸技术', '探测制导与控制技术', ...],
                ...
            },
            '理学': {...},
            ...
        },
        'all_major_links': [...],  # 所有专业平级列表
        'statistics': {...}         # 统计信息
    }
    """
    with open(html_file, encoding='utf-8') as f:
        soup = BeautifulSoup(f.read(), 'html.parser')
    
    result = {
        'major_hierarchy': {},
        'all_major_links': [],
        'statistics': {}
    }
    
    # 找到专业筛选的整体容器
    major_menu = soup.find('div', class_='menu-major')
    if not major_menu:
        job_menu = soup.find('div', class_='job-menu')
        if job_menu:
            major_menu = job_menu.find('div', class_='menu-major')
    
    if not major_menu:
        print("未找到专业筛选菜单")
        return result
    
    print("找到专业筛选菜单，开始解析...")
    
    # 查找所有一级分类的dl结构
    dl_major = major_menu.find('dl', class_='menu--major')
    if not dl_major:
        dl_major = major_menu.find('dl')
    
    if not dl_major:
        print("未找到专业列表结构")
        return result
    
    # 遍历每个dd元素（代表一个一级分类）
    dd_elements = dl_major.find_all('dd')
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
        result['major_hierarchy'][first_level] = {}
        print(f"\n解析一级分类: {first_level}")
        
        # 查找展开的专业列表
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
            result['major_hierarchy'][first_level][second_level] = []
            
            # 获取该二级分类下的所有专业
            third_div = li.find('div', class_='menu__expand__third-name')
            if third_div:
                major_links = third_div.find_all('a')
                for link in major_links:
                    name = link.get_text(strip=True)
                    href = link.get('href', '')
                    
                    if name and name not in ['全部专业', '']:
                        # 提取专业代码
                        major_code = None
                        if 'major=' in href:
                            match = re.search(r'major=(\d+)', href)
                            if match:
                                major_code = match.group(1)
                        
                        result['major_hierarchy'][first_level][second_level].append(name)
                        
                        result['all_major_links'].append({
                            'name': name,
                            'code': major_code,
                            'first_level': first_level,
                            'second_level': second_level,
                            'url': href
                        })
                
                print(f"    {second_level}: {len(result['major_hierarchy'][first_level][second_level])} 个专业")
    
    # 统计信息
    total_first = len(result['major_hierarchy'])
    total_second = sum(len(second) for second in result['major_hierarchy'].values())
    total_majors = len(result['all_major_links'])
    
    result['statistics'] = {
        'first_level_count': total_first,
        'second_level_count': total_second,
        'major_count': total_majors,
        'first_levels': list(result['major_hierarchy'].keys())
    }
    
    return result

def print_major_hierarchy(hierarchy_data, max_items=5):
    """打印专业层级结构"""
    for first_level, second_levels in hierarchy_data.items():
        print(f"\n【{first_level}】")
        for second_level, majors in second_levels.items():
            print(f"  ├─ {second_level} ({len(majors)}个专业)")
            if majors:
                preview = majors[:min(max_items, len(majors))]
                for major in preview:
                    print(f"  │    └─ {major}")
                if len(majors) > max_items:
                    print(f"  │    └─ ... 还有 {len(majors) - max_items} 个专业")

def main():
    """主函数"""
    print("=" * 70)
    print("智联招聘专业筛选标签提取工具（保留一级和二级分类）")
    print("=" * 70)
    
    # 尝试多个可能的文件名
    possible_files = ['menu.html', 'major.html', 'professional.html', 'index.html']
    extracted_data = None
    
    for filename in possible_files:
        try:
            print(f"\n尝试读取文件: {filename}")
            extracted_data = extract_major_hierarchy(filename)
            if extracted_data['statistics'].get('major_count', 0) > 0:
                print(f"成功从 {filename} 提取数据")
                break
        except FileNotFoundError:
            print(f"文件 {filename} 不存在")
            continue
    
    if not extracted_data or extracted_data['statistics'].get('major_count', 0) == 0:
        print("\n❌ 无法提取专业数据，请确保以下文件之一存在且包含专业内容:")
        print("   - menu.html")
        print("   - major.html")
        print("   - professional.html")
        return
    
    # 显示统计信息
    stats = extracted_data['statistics']
    print(f"\n📊 提取统计:")
    print(f"  - 一级分类: {stats.get('first_level_count', 0)} 个")
    print(f"  - 二级分类: {stats.get('second_level_count', 0)} 个")
    print(f"  - 专业总数: {stats.get('major_count', 0)} 个")
    
    # 显示层级结构示例
    if extracted_data['major_hierarchy']:
        print("\n📁 专业层级结构示例:")
        print_major_hierarchy(extracted_data['major_hierarchy'], max_items=3)
    
    # 保存完整结果
    output = {
        'hierarchy': extracted_data['major_hierarchy'],
        'all_majors': extracted_data['all_major_links'],
        'statistics': extracted_data['statistics'],
        'usage_example': {
            'note': '可以通过以下方式访问数据',
            'get_first_levels': "list(data['hierarchy'].keys())",
            'get_second_levels': "list(data['hierarchy']['工学'].keys())",
            'get_majors': "data['hierarchy']['工学']['安全科学与工程类']"
        }
    }
    
    with open('major_hierarchy.json', 'w', encoding='utf-8') as f:
        json.dump(output, f, ensure_ascii=False, indent=2)
    
    # 生成专业代码字典
    major_codes = {}
    for item in extracted_data['all_major_links']:
        if item.get('code'):
            major_codes[item['code']] = item['name']
    
    with open('major_codes.json', 'w', encoding='utf-8') as f:
        json.dump({
            'total': len(major_codes),
            'majors': major_codes
        }, f, ensure_ascii=False, indent=2)
    
    # 生成按一级分类组织的专业代码
    by_first_level = {}
    for item in extracted_data['all_major_links']:
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
    
    with open('majors_by_level.json', 'w', encoding='utf-8') as f:
        json.dump(by_first_level, f, ensure_ascii=False, indent=2)
    
    print("\n" + "=" * 70)
    print("✅ 结果已保存到以下文件:")
    print("  1. major_hierarchy.json - 完整层级结构（一级→二级→专业）")
    print("  2. major_codes.json - 专业代码字典（code→name）")
    print("  3. majors_by_level.json - 按一级和二级分类组织的专业（含代码）")
    
    # 显示一级分类列表
    if extracted_data['major_hierarchy']:
        print("\n📋 一级分类列表:")
        for first_level in extracted_data['major_hierarchy'].keys():
            print(f"  - {first_level}")
    
    # 生成示例代码
    print("\n" + "=" * 70)
    print("📝 使用示例:")
    print("""
# 读取专业层级数据
import json
with open('major_hierarchy.json', 'r', encoding='utf-8') as f:
    data = json.load(f)

# 获取所有一级分类
first_levels = list(data['hierarchy'].keys())
print(f"专业一级分类: {first_levels}")

# 获取工学下的所有二级分类
second_levels = list(data['hierarchy']['工学'].keys())
print(f"工学的二级分类: {second_levels}")

# 获取安全科学与工程类下的所有专业
majors = data['hierarchy']['工学']['安全科学与工程类']
print(f"安全科学与工程类专业: {majors[:5]}")

# 遍历所有专业
for major in data['all_majors']:
    print(f"{major['first_level']} > {major['second_level']} > {major['name']} (code: {major['code']})")
""")

if __name__ == "__main__":
    main()