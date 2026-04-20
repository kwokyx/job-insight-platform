#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
数据库连接测试脚本
"""

import pymysql
import sys

# 请修改为你的实际配置
DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'password': 'xqy220094',  # 改成你的密码
    'database': 'zhaopin_db',  # 改成你的数据库名
    'charset': 'utf8mb4'
}

def test_mysql_connection():
    print("=" * 60)
    print("测试MySQL连接")
    print("=" * 60)
    print(f"主机: {DB_CONFIG['host']}:{DB_CONFIG['port']}")
    print(f"用户: {DB_CONFIG['user']}")
    print(f"数据库: {DB_CONFIG['database']}")
    print("-" * 60)
    
    try:
        # 测试连接
        conn = pymysql.connect(**DB_CONFIG)
        print("✅ 成功连接到MySQL服务器")
        
        # 获取服务器信息
        with conn.cursor() as cursor:
            cursor.execute("SELECT VERSION()")
            version = cursor.fetchone()
            print(f"✅ MySQL版本: {version[0]}")
            
            cursor.execute("SELECT CURRENT_USER()")
            current_user = cursor.fetchone()
            print(f"✅ 当前用户: {current_user[0]}")
            
            cursor.execute("SHOW DATABASES")
            databases = cursor.fetchall()
            print(f"✅ 可用数据库: {[db[0] for db in databases]}")
        
        conn.close()
        print("=" * 60)
        print("✅ 所有测试通过！")
        return True
        
    except pymysql.err.OperationalError as e:
        print(f"❌ 连接失败: {e}")
        if e.args[0] == 1045:
            print("   原因: 用户名或密码错误")
            print("   解决方法:")
            print("   1. 检查密码是否正确")
            print("   2. 尝试重置MySQL密码")
        elif e.args[0] == 1049:
            print("   原因: 数据库不存在")
            print("   解决方法: 创建数据库 CREATE DATABASE your_db_name;")
        elif e.args[0] == 2003:
            print("   原因: 无法连接到MySQL服务器")
            print("   解决方法: 确保MySQL服务已启动")
        return False
    except Exception as e:
        print(f"❌ 未知错误: {e}")
        import traceback
        traceback.print_exc()
        return False

def test_rabbitmq_connection():
    print("\n" + "=" * 60)
    print("测试RabbitMQ连接")
    print("=" * 60)
    try:
        import pika
        connection = pika.BlockingConnection(
            pika.ConnectionParameters(host='localhost', port=5672)
        )
        connection.close()
        print("✅ RabbitMQ连接成功")
        return True
    except Exception as e:
        print(f"❌ RabbitMQ连接失败: {e}")
        return False

if __name__ == "__main__":
    print("\n开始诊断分布式数据采集系统...\n")
    
    # 测试MySQL
    mysql_ok = test_mysql_connection()
    
    # 测试RabbitMQ
    rabbitmq_ok = test_rabbitmq_connection()
    
    print("\n" + "=" * 60)
    print("诊断结果:")
    print(f"MySQL: {'✅ 正常' if mysql_ok else '❌ 失败'}")
    print(f"RabbitMQ: {'✅ 正常' if rabbitmq_ok else '❌ 失败'}")
    print("=" * 60)
    
    if not mysql_ok:
        print("\n建议:")
        print("1. 确认MySQL服务已启动")
        print("2. 在命令行运行: net start MySQL")  # Windows
        print("3. 测试MySQL登录: mysql -u root -p")
        print("4. 检查并修改配置文件中的密码")