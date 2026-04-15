from pathlib import Path


BIG_REGIONS = {
    "CN-NORTH": "华北",
    "CN-NORTHEAST": "东北",
    "CN-EAST": "华东",
    "CN-CENTRAL": "华中",
    "CN-SOUTH": "华南",
    "CN-SOUTHWEST": "西南",
    "CN-NORTHWEST": "西北",
    "CN-HKMO": "港澳台",
    "CN-OVERSEAS": "海外",
    "CN-NATIONAL": "全国",
    "CN-OTHER": "其他",
}

PROVINCES = [
    ("CN-BJ", "北京", "CN-NORTH"),
    ("CN-TJ", "天津", "CN-NORTH"),
    ("CN-HE", "河北省", "CN-NORTH"),
    ("CN-SX", "山西省", "CN-NORTH"),
    ("CN-NM", "内蒙古自治区", "CN-NORTH"),
    ("CN-LN", "辽宁省", "CN-NORTHEAST"),
    ("CN-JL", "吉林省", "CN-NORTHEAST"),
    ("CN-HLJ", "黑龙江省", "CN-NORTHEAST"),
    ("CN-SH", "上海", "CN-EAST"),
    ("CN-JS", "江苏省", "CN-EAST"),
    ("CN-ZJ", "浙江省", "CN-EAST"),
    ("CN-AH", "安徽省", "CN-EAST"),
    ("CN-FJ", "福建省", "CN-EAST"),
    ("CN-JX", "江西省", "CN-EAST"),
    ("CN-SD", "山东省", "CN-EAST"),
    ("CN-HA", "河南省", "CN-CENTRAL"),
    ("CN-HB", "湖北省", "CN-CENTRAL"),
    ("CN-HN", "湖南省", "CN-CENTRAL"),
    ("CN-GD", "广东省", "CN-SOUTH"),
    ("CN-GX", "广西壮族自治区", "CN-SOUTH"),
    ("CN-HI", "海南省", "CN-SOUTH"),
    ("CN-CQ", "重庆", "CN-SOUTHWEST"),
    ("CN-SC", "四川省", "CN-SOUTHWEST"),
    ("CN-GZ", "贵州省", "CN-SOUTHWEST"),
    ("CN-YN", "云南省", "CN-SOUTHWEST"),
    ("CN-XZ", "西藏自治区", "CN-SOUTHWEST"),
    ("CN-SN", "陕西省", "CN-NORTHWEST"),
    ("CN-GS", "甘肃省", "CN-NORTHWEST"),
    ("CN-QH", "青海省", "CN-NORTHWEST"),
    ("CN-NX", "宁夏回族自治区", "CN-NORTHWEST"),
    ("CN-XJ", "新疆维吾尔自治区", "CN-NORTHWEST"),
    ("CN-HK", "香港特别行政区", "CN-HKMO"),
    ("CN-MO", "澳门特别行政区", "CN-HKMO"),
    ("CN-INTL", "海外", "CN-OVERSEAS"),
    ("CN-ALL", "全国", "CN-NATIONAL"),
    ("CN-MISC", "其他", "CN-OTHER"),
]

PROVINCE_TO_CITIES = {
    "CN-BJ": ["北京"],
    "CN-TJ": ["天津"],
    "CN-HE": ["石家庄", "唐山", "秦皇岛", "邯郸", "邢台", "保定", "张家口", "承德", "沧州", "廊坊", "衡水"],
    "CN-SX": ["太原", "大同", "阳泉", "长治", "晋城", "朔州", "晋中", "运城", "忻州", "临汾", "吕梁"],
    "CN-NM": ["呼和浩特", "包头", "乌海", "赤峰", "通辽", "鄂尔多斯", "呼伦贝尔", "巴彦淖尔", "乌兰察布", "兴安盟", "锡林郭勒盟", "阿拉善盟"],
    "CN-LN": ["沈阳", "大连", "鞍山", "抚顺", "本溪", "丹东", "锦州", "营口", "阜新", "辽阳", "盘锦", "铁岭", "朝阳", "葫芦岛"],
    "CN-JL": ["长春", "吉林市", "四平", "辽源", "通化", "白山", "松原", "白城", "延边"],
    "CN-HLJ": ["哈尔滨", "齐齐哈尔", "鸡西", "鹤岗", "双鸭山", "大庆", "伊春", "佳木斯", "七台河", "牡丹江", "黑河", "绥化", "大兴安岭"],
    "CN-SH": ["上海"],
    "CN-JS": ["南京", "无锡", "徐州", "常州", "苏州", "南通", "连云港", "淮安", "盐城", "扬州", "镇江", "泰州", "宿迁"],
    "CN-ZJ": ["杭州", "宁波", "温州", "嘉兴", "湖州", "绍兴", "金华", "衢州", "舟山", "台州", "丽水", "浙江"],
    "CN-AH": ["合肥", "芜湖", "蚌埠", "淮南", "马鞍山", "淮北", "铜陵", "安庆", "黄山", "滁州", "阜阳", "宿州", "六安", "亳州", "池州", "宣城"],
    "CN-FJ": ["福州", "厦门", "莆田", "三明", "泉州", "漳州", "南平", "龙岩", "宁德"],
    "CN-JX": ["南昌", "景德镇", "萍乡", "九江", "新余", "鹰潭", "赣州", "吉安", "宜春", "抚州", "上饶"],
    "CN-SD": ["济南", "青岛", "淄博", "枣庄", "东营", "烟台", "潍坊", "济宁", "泰安", "威海", "日照", "临沂", "德州", "聊城", "滨州", "菏泽"],
    "CN-HA": ["郑州", "开封", "洛阳", "平顶山", "安阳", "鹤壁", "新乡", "焦作", "濮阳", "许昌", "漯河", "三门峡", "南阳", "商丘", "信阳", "周口", "驻马店", "济源市"],
    "CN-HB": ["武汉", "黄石", "十堰", "宜昌", "襄阳", "鄂州", "荆门", "孝感", "荆州", "黄冈", "咸宁", "随州", "恩施", "仙桃市", "潜江市", "天门市", "神农架林区"],
    "CN-HN": ["长沙", "株洲", "湘潭", "衡阳", "邵阳", "岳阳", "常德", "张家界", "益阳", "郴州", "永州", "怀化", "娄底", "湘西", "湖南"],
    "CN-GD": ["广州", "深圳", "珠海", "汕头", "佛山", "韶关", "湛江", "肇庆", "江门", "茂名", "惠州", "梅州", "汕尾", "河源", "阳江", "清远", "东莞", "中山", "潮州", "揭阳", "云浮"],
    "CN-GX": ["南宁", "柳州", "桂林", "梧州", "北海", "防城港", "钦州", "贵港", "玉林", "百色", "贺州", "河池", "来宾", "崇左", "广西"],
    "CN-HI": ["海口", "三亚", "三沙", "儋州", "五指山", "琼海", "文昌", "万宁", "东方", "定安", "澄迈", "临高", "白沙", "昌江", "乐东", "陵水", "保亭", "琼中"],
    "CN-CQ": ["重庆"],
    "CN-SC": ["成都", "自贡", "攀枝花", "泸州", "德阳", "绵阳", "广元", "遂宁", "内江", "乐山", "南充", "眉山", "宜宾", "广安", "达州", "雅安", "巴中", "资阳", "阿坝", "甘孜", "凉山"],
    "CN-GZ": ["贵阳", "六盘水", "遵义", "安顺", "毕节", "铜仁", "黔西南", "黔东南", "黔南"],
    "CN-YN": ["昆明", "曲靖", "玉溪", "保山", "昭通", "丽江", "普洱", "临沧", "楚雄", "红河", "文山", "西双版纳", "大理", "德宏", "怒江", "迪庆"],
    "CN-XZ": ["拉萨", "日喀则", "昌都", "林芝", "山南", "那曲", "阿里"],
    "CN-SN": ["西安", "铜川", "宝鸡", "咸阳", "渭南", "延安", "汉中", "榆林", "安康", "商洛", "西咸新区"],
    "CN-GS": ["兰州", "嘉峪关", "金昌", "白银", "天水", "武威", "张掖", "平凉", "酒泉", "庆阳", "定西", "陇南", "临夏", "甘南"],
    "CN-QH": ["西宁", "海东", "海北", "黄南", "海南州", "果洛", "玉树", "海西"],
    "CN-NX": ["银川", "石嘴山", "吴忠", "固原", "中卫"],
    "CN-XJ": ["乌鲁木齐", "克拉玛依", "吐鲁番", "哈密", "昌吉", "博尔塔拉", "巴音郭楞", "阿克苏", "克孜勒苏柯尔克孜", "喀什", "和田", "伊犁", "塔城", "阿勒泰", "石河子市", "阿拉尔市", "图木舒克市", "五家渠市", "北屯市", "铁门关市", "双河市", "可克达拉市", "昆玉市", "新星市"],
    "CN-HK": ["香港"],
    "CN-MO": ["澳门"],
    "CN-INTL": ["乌兹别克斯坦", "乌干达", "以色列", "伊拉克", "俄罗斯联邦", "几内亚", "刚果（金）", "加纳", "加蓬", "南非", "博茨瓦纳", "卢旺达", "印度尼西亚", "吉尔吉斯斯坦", "哈萨克斯坦", "哥伦比亚", "喀麦隆", "坦桑尼亚", "埃塞俄比亚", "塞拉利昂", "墨西哥", "孟加拉国", "安哥拉", "尼加拉瓜", "尼日利亚", "尼日尔", "巴布亚新几内亚", "巴拿马", "所罗门群岛", "新加坡", "日本", "智利", "柬埔寨", "沙特阿拉伯", "泰国", "津巴布韦", "瓦努阿图", "科特迪瓦", "秘鲁", "罗马尼亚", "老挝", "肯尼亚", "越南", "赞比亚", "菲律宾", "莫桑比克", "贝宁", "阿富汗", "阿尔及利亚", "阿曼", "阿联酋", "马拉维", "马来西亚", "黑山", "摩洛哥", "纳米比亚"],
    "CN-ALL": ["全国"],
    "CN-MISC": ["其他"],
}


def esc(value: str) -> str:
    return value.replace("'", "''")


province_name_map = {code: name for code, name, _ in PROVINCES}
province_region_map = {code: parent for code, _, parent in PROVINCES}
city_to_province = {
    city: province_code
    for province_code, cities in PROVINCE_TO_CITIES.items()
    for city in cities
}


def main() -> None:
    sql = ["USE career_platform;\n\n"]
    for code, name in BIG_REGIONS.items():
        sql.append(
            "INSERT INTO dim_region (region_code, region_name, region_level, parent_code, full_name, sort_no, status, created_at, updated_at) "
            f"VALUES ('{code}', '{esc(name)}', 1, NULL, '{esc(name)}', 0, 1, NOW(), NOW()) "
            "ON DUPLICATE KEY UPDATE region_name=VALUES(region_name), region_level=1, parent_code=NULL, full_name=VALUES(full_name), updated_at=NOW();\n"
        )
    sql.append("\n")
    for idx, (code, name, parent_code) in enumerate(PROVINCES, start=1):
        parent_name = BIG_REGIONS[parent_code]
        full_name = f"{parent_name}/{name}"
        sql.append(
            "INSERT INTO dim_region (region_code, region_name, region_level, parent_code, full_name, sort_no, status, created_at, updated_at) "
            f"VALUES ('{code}', '{esc(name)}', 2, '{parent_code}', '{esc(full_name)}', {idx}, 1, NOW(), NOW()) "
            "ON DUPLICATE KEY UPDATE region_name=VALUES(region_name), region_level=2, parent_code=VALUES(parent_code), full_name=VALUES(full_name), updated_at=NOW();\n"
        )
    sql.append("\n")
    for city_name, province_code in sorted(city_to_province.items()):
        province_name = province_name_map[province_code]
        big_region_name = BIG_REGIONS[province_region_map[province_code]]
        full_name = f"{big_region_name}/{province_name}/{city_name}"
        sql.append(
            "UPDATE dim_region "
            f"SET region_level = 3, parent_code = '{province_code}', full_name = '{esc(full_name)}', updated_at = NOW() "
            f"WHERE region_name = '{esc(city_name)}' AND region_code LIKE 'CITY_%';\n"
        )
    sql.append(
        "\nUPDATE biz_job_posting jp "
        "JOIN dim_region city ON city.region_code = jp.city_code "
        "LEFT JOIN dim_region province ON province.region_code = city.parent_code "
        "LEFT JOIN dim_region region ON region.region_code = province.parent_code "
        "SET jp.province_code = province.region_code, "
        "    jp.region_code = region.region_code, "
        "    jp.region = COALESCE(province.region_name, jp.region), "
        "    jp.city = COALESCE(city.region_name, jp.city), "
        "    jp.updated_at = NOW() "
        "WHERE jp.city_code IS NOT NULL;\n"
    )
    sql.append(
        "UPDATE biz_company bc "
        "JOIN dim_region city ON city.region_code = bc.city_code "
        "LEFT JOIN dim_region province ON province.region_code = city.parent_code "
        "LEFT JOIN dim_region region ON region.region_code = province.parent_code "
        "SET bc.province_code = province.region_code, "
        "    bc.region_code = region.region_code, "
        "    bc.updated_at = NOW() "
        "WHERE bc.city_code IS NOT NULL;\n"
    )
    sql.append(
        "\nSELECT COUNT(*) AS total, SUM(region_level = 1) AS level1_count, SUM(region_level = 2) AS level2_count, "
        "SUM(region_level = 3) AS level3_count, SUM(parent_code IS NULL) AS no_parent_count FROM dim_region;\n"
    )
    sql.append(
        "SELECT (SELECT COUNT(*) FROM biz_job_posting WHERE city_code IS NOT NULL) AS jp_city, "
        "(SELECT COUNT(*) FROM biz_job_posting WHERE province_code IS NOT NULL) AS jp_province, "
        "(SELECT COUNT(*) FROM biz_job_posting WHERE region_code IS NOT NULL) AS jp_region, "
        "(SELECT COUNT(*) FROM biz_company WHERE city_code IS NOT NULL) AS company_city, "
        "(SELECT COUNT(*) FROM biz_company WHERE province_code IS NOT NULL) AS company_province, "
        "(SELECT COUNT(*) FROM biz_company WHERE region_code IS NOT NULL) AS company_region;\n"
    )
    path = Path("config/rebuild_dim_region_hierarchy.sql")
    path.write_text("".join(sql), encoding="utf-8")
    print(path)


if __name__ == "__main__":
    main()
