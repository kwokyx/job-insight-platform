#!/usr/bin/env python
"""
测试RabbitMQ连接
"""
import pika
import os
import sys

# 从.env文件读取配置（简单版本）
def load_env():
    env_path = os.path.join(os.path.dirname(__file__), '.env')
    config = {}
    if os.path.exists(env_path):
        with open(env_path, 'r') as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith('#') and '=' in line:
                    key, value = line.split('=', 1)
                    config[key.strip()] = value.strip()
    return config

config = load_env()

# 使用环境变量或默认值
RABBITMQ_HOST = config.get('RABBITMQ_HOST', 'localhost')
RABBITMQ_PORT = int(config.get('RABBITMQ_PORT', 5672))
RABBITMQ_USER = config.get('RABBITMQ_USER', 'admin')
RABBITMQ_PASSWORD = config.get('RABBITMQ_PASSWORD', 'admin123')

print(f"测试RabbitMQ连接:")
print(f"主机: {RABBITMQ_HOST}")
print(f"端口: {RABBITMQ_PORT}")
print(f"用户: {RABBITMQ_USER}")
print(f"密码: {RABBITMQ_PASSWORD}")

try:
    credentials = pika.PlainCredentials(RABBITMQ_USER, RABBITMQ_PASSWORD)
    parameters = pika.ConnectionParameters(
        host=RABBITMQ_HOST,
        port=RABBITMQ_PORT,
        credentials=credentials,
        socket_timeout=10
    )
    connection = pika.BlockingConnection(parameters)
    print("✅ RabbitMQ连接成功!")
    connection.close()
except Exception as e:
    print(f"❌ RabbitMQ连接失败: {e}")
    import traceback
    traceback.print_exc()
    sys.exit(1)