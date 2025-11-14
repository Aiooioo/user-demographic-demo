import requests
import random
import time
import uuid
from datetime import datetime

# --- 配置 ---
# 目标API的URL。如果您的网关或服务运行在不同端口，请修改此处的端口号。
# 8080是Spring Cloud Gateway的默认端口。
API_URL = "http://localhost:8080/collect/event"

# --- 模拟数据池 ---
USER_IDS = [f"user_{i}" for i in range(1, 21)]  # 20个模拟用户
CATEGORIES = ["electronics", "books", "clothing", "home_goods", "sports", "beauty"]
# 事件类型权重，让曝光和点击事件更多，购买事件较少
EVENT_TYPES = ["IMPRESSION"] * 15 + ["CLICK"] * 5 + ["PURCHASE"] * 1

def generate_event():
    """生成一个随机的广告事件。"""
    user_id = random.choice(USER_IDS)
    event_type = random.choice(EVENT_TYPES)
    category = random.choice(CATEGORIES)
    
    event = {
        "eventId": str(uuid.uuid4()),
        "userId": user_id,
        "eventType": event_type,
        "timestamp": int(datetime.now().timestamp() * 1000), # Flink通常需要毫秒级时间戳
        "category": category,
        "amount": None
    }

    # 如果是购买事件，则生成一个随机金额
    if event_type == "PURCHASE":
        event["amount"] = round(random.uniform(10.5, 500.8), 2)
        
    return event

def send_event(event):
    """发送单个事件到API。"""
    try:
        response = requests.post(API_URL, json=event, timeout=5)
        if response.status_code == 200 and response.text == "accepted":
            print(f"✅  Successfully sent {event['eventType']} event for {event['userId']}. Amount: {event['amount'] or 'N/A'}")
            return True
        else:
            print(f"❌  Failed to send event. Status: {response.status_code}, Response: {response.text}")
            return False
    except requests.exceptions.RequestException as e:
        print(f"🔥 Error connecting to API: {e}")
        return False

if __name__ == "__main__":
    print(f"Starting event simulation script...")
    print(f"Target API: {API_URL}")
    print("-" * 30)

    # 检查requests库是否安装
    try:
        import requests
    except ImportError:
        print("🚨 'requests' library not found.")
        print("Please install it by running: pip install requests")
        exit(1)

    event_count = 0
    # 循环发送事件，可以按 Ctrl+C 停止
    try:
        while True:
            event_data = generate_event()
            if send_event(event_data):
                event_count += 1
            
            # 随机暂停0.1到1秒，模拟真实用户行为间隔
            time.sleep(random.uniform(0.1, 1.0))

    except KeyboardInterrupt:
        print("\n" + "-" * 30)
        print("Script interrupted by user.")
        print(f"Total events sent: {event_count}")
        print("Exiting.")
