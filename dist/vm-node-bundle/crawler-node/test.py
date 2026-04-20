# import pika
# import socket
# import sys

# def test_connection():
#     print("Python版本:", sys.version)
#     print("当前工作目录:", __import__('os').getcwd())
    
#     # 测试DNS解析
#     try:
#         print(f"解析 localhost: {socket.gethostbyname('localhost')}")
#         print(f"解析 127.0.0.1: {socket.gethostbyname('127.0.0.1')}")
#     except Exception as e:
#         print(f"DNS解析失败: {e}")
    
#     # 测试RabbitMQ连接
#     try:
#         credentials = pika.PlainCredentials('admin', 'admin123')
#         params = pika.ConnectionParameters(
#             host='127.0.0.1',
#             port=5672,
#             credentials=credentials,
#             virtual_host='/'
#         )
#         print(f"尝试连接: {params.host}:{params.port}")
#         connection = pika.BlockingConnection(params)
#         print("✅ RabbitMQ连接成功！")
#         connection.close()
#         return True
#     except Exception as e:
#         print(f"❌ 连接失败: {e}")
#         print(f"错误类型: {type(e).__name__}")
#         return False

# if __name__ == "__main__":
#     test_connection()
import pika
import socket

print("测试1: 直接 socket 连接")
try:
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(5)
    result = sock.connect_ex(('127.0.0.1', 5672))
    if result == 0:
        print("✅ TCP 连接成功")
    else:
        print(f"❌ TCP 连接失败: {result}")
    sock.close()
except Exception as e:
    print(f"❌ 连接异常: {e}")

print("\n测试2: pika 连接")
try:
    credentials = pika.PlainCredentials('admin', 'admin123')
    params = pika.ConnectionParameters(
        host='127.0.0.1',
        port=5672,
        credentials=credentials,
        heartbeat=0
    )
    conn = pika.BlockingConnection(params)
    print("✅ pika 连接成功")
    conn.close()
except Exception as e:
    print(f"❌ pika 连接失败: {e}")
    import traceback
    traceback.print_exc()
import sys
import os

# 添加当前目录到路径
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

print("1. 测试导入 config...")
from config import config
print(f"   RABBITMQ_HOST = {config.RABBITMQ_HOST}")

print("\n2. 测试导入 producer...")
from mq.producer import MQProducer
print("   导入成功")

print("\n3. 测试创建 producer（不连接）...")
producer = MQProducer()
print(f"   producer 创建成功: {producer}")
print(f"   connection 状态: {producer.connection}")

print("\n4. 测试连接...")
result = producer.connect()
print(f"   连接结果: {result}")

print("\n5. 测试发送心跳...")
from utils.heartbeat import HeartbeatManager

# 创建一个简单的 mock worker
class MockWorker:
    def get_system_stats(self):
        return {}

print("   创建心跳管理器...")
hb = HeartbeatManager(MockWorker())
print("   心跳管理器创建成功")

print("\n✅ 所有测试通过！")