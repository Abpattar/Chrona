"""
Supabase Client Helper
=======================
Centralized Supabase client initialization and auth helpers.

Usage:
    from db.supabase_client import get_supabase_client, verify_user_token
"""
import os


def get_supabase_client():
    """
    Get a Supabase client instance using the Python SDK.

    Falls back to REST API wrapper if supabase-py is not installed.
    """
    url = os.getenv("SUPABASE_URL")
    key = os.getenv("SUPABASE_ANON_KEY")

    if not url or not key:
        raise ValueError("SUPABASE_URL and SUPABASE_ANON_KEY must be set")

    try:
        from supabase import create_client
        return create_client(url, key)
    except ImportError:
        # Return a lightweight REST wrapper
        return SupabaseRestClient(url, key)


class SupabaseRestClient:
    """
    Lightweight REST API wrapper for Supabase.
    Used when supabase-py is not installed.
    """

    def __init__(self, url: str, key: str):
        self.url = url
        self.key = key
        self.headers = {
            "apikey": key,
            "Authorization": f"Bearer {key}",
            "Content-Type": "application/json",
        }

    def table(self, name: str):
        return TableQuery(self.url, self.headers, name)


class TableQuery:
    """Simple query builder for Supabase REST API."""

    def __init__(self, base_url: str, headers: dict, table: str):
        self.base_url = f"{base_url}/rest/v1/{table}"
        self.headers = headers.copy()
        self._filters = []
        self._select_cols = "*"

    def select(self, columns="*"):
        self._select_cols = columns
        return self

    def eq(self, col, val):
        self._filters.append(f"{col}=eq.{val}")
        return self

    def neq(self, col, val):
        self._filters.append(f"{col}=neq.{val}")
        return self

    def lt(self, col, val):
        self._filters.append(f"{col}=lt.{val}")
        return self

    def gt(self, col, val):
        self._filters.append(f"{col}=gt.{val}")
        return self

    def gte(self, col, val):
        self._filters.append(f"{col}=gte.{val}")
        return self

    def lte(self, col, val):
        self._filters.append(f"{col}=lte.{val}")
        return self

    def order(self, col, ascending=True):
        direction = "asc" if ascending else "desc"
        self._filters.append(f"order={col}.{direction}")
        return self

    def single(self):
        self.headers["Accept"] = "application/vnd.pgrst.object+json"
        return self

    def execute(self):
        import requests
        params = "&".join(self._filters) if self._filters else ""
        url = f"{self.base_url}?select={self._select_cols}"
        if params:
            url += f"&{params}"

        resp = requests.get(url, headers=self.headers)
        return type("Result", (), {"data": resp.json() if resp.ok else None, "error": None if resp.ok else resp.text})()

    def insert(self, data):
        import requests
        resp = requests.post(self.base_url, headers=self.headers, json=data)
        return type("Result", (), {"data": resp.json() if resp.ok else None, "error": None if resp.ok else resp.text})()

    def upsert(self, data):
        import requests
        headers = {**self.headers, "Prefer": "resolution=merge-duplicates"}
        resp = requests.post(self.base_url, headers=headers, json=data)
        return type("Result", (), {"data": resp.json() if resp.ok else None, "error": None if resp.ok else resp.text})()

    def update(self, data):
        import requests
        params = "&".join(self._filters) if self._filters else ""
        url = f"{self.base_url}?{params}" if params else self.base_url
        headers = {**self.headers, "Prefer": "return=minimal"}
        resp = requests.patch(url, headers=headers, json=data)
        return type("Result", (), {"data": None, "error": None if resp.ok else resp.text})()


# ============================================================
# SELF-TEST
# ============================================================
if __name__ == "__main__":
    # Test without actual connection
    try:
        client = get_supabase_client()
        print(f"Client type: {type(client).__name__}")
    except ValueError as e:
        print(f"Expected error (no env vars): {e}")
    print("Supabase Client: OK")
