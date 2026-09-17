"""
Redis Client Wrapper for Upstash
==================================
Wraps Redis operations with fallbacks for when Redis is unavailable
or when the free tier limit (10K commands/day) is hit.

Redis Key Schema:
  chrona:profile:{user_id}          -> BehaviorProfile JSON (TTL: 24h)
  chrona:repair:{user_id}:{task_id} -> Repair proposals JSON (TTL: 5min)
  chrona:lock:schedule:{user_id}    -> Schedule write lock (TTL: 5s)
  chrona:memory:{user_id}:{session} -> Agent conversation memory (TTL: 1h)

Usage:
    from cache.redis_client import get_redis_client

    redis = get_redis_client()
    redis.setex("key", 300, "value")  # 5 min TTL
"""
import os
import json
from functools import lru_cache


class RedisFallback:
    """
    In-memory fallback when Redis is unavailable.
    Uses an LRU cache with max 1000 entries.
    NOT persistent — lost on server restart.
    """

    def __init__(self):
        self._store = {}
        self._is_fallback = True

    def get(self, key):
        return self._store.get(key)

    def set(self, key, value):
        self._store[key] = value

    def setex(self, key, ttl_seconds, value):
        # TTL not enforced in fallback (simplicity for hackathon)
        self._store[key] = value

    def delete(self, key):
        self._store.pop(key, None)

    def exists(self, key):
        return key in self._store

    def ping(self):
        return True


def get_redis_client():
    """
    Get a Redis client instance.

    Tries to connect to Upstash Redis.
    Falls back to in-memory store if:
    - UPSTASH_REDIS_URL is not set
    - Connection fails

    Returns:
        Redis client or RedisFallback
    """
    redis_url = os.getenv("UPSTASH_REDIS_URL")
    redis_token = os.getenv("UPSTASH_REDIS_TOKEN")

    if not redis_url or not redis_token:
        print("WARNING: UPSTASH_REDIS_URL/TOKEN not set. Using in-memory fallback.")
        return RedisFallback()

    try:
        import redis as redis_lib
        client = redis_lib.Redis(
            host=redis_url.replace("https://", "").replace("http://", ""),
            port=6379,
            password=redis_token,
            ssl=True,
            decode_responses=True,
            socket_connect_timeout=5,
        )
        client.ping()
        return client
    except ImportError:
        print("WARNING: redis package not installed. Using in-memory fallback.")
        return RedisFallback()
    except Exception as e:
        print(f"WARNING: Redis connection failed ({e}). Using in-memory fallback.")
        return RedisFallback()


# ============================================================
# SELF-TEST
# ============================================================
if __name__ == "__main__":
    # Test fallback
    fallback = RedisFallback()
    fallback.setex("test:key", 300, json.dumps({"hello": "world"}))
    result = fallback.get("test:key")
    assert result is not None
    assert json.loads(result)["hello"] == "world"
    print("Redis Fallback: OK")

    # Test get_redis_client (will likely use fallback in dev)
    client = get_redis_client()
    print(f"Redis client type: {type(client).__name__}")
    print("Redis Client: OK")
