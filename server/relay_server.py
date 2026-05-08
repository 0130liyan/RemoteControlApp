#!/usr/bin/env python3
"""远程控制中继服务器"""
import asyncio, json, websockets
from websockets.server import serve
from collections import defaultdict

rooms = defaultdict(dict)
conn_info = {}

async def handle_connection(websocket):
    try:
        msg = json.loads(await asyncio.wait_for(websocket.recv(), timeout=10))
        if msg.get("type") != "register": return
        role, room_id = msg.get("role"), msg.get("room_id", "default")
        if role not in ("controller", "controlled"): return
        
        conn_info[websocket] = {"role": role, "room_id": room_id}
        rooms[room_id][role] = websocket
        
        peer_role = "controlled" if role == "controller" else "controller"
        peer = rooms[room_id].get(peer_role)
        if peer:
            await websocket.send(json.dumps({"type": "info", "message": "配对成功"}))
            await peer.send(json.dumps({"type": "info", "message": "配对成功"}))
        else:
            await websocket.send(json.dumps({"type": "info", "message": f"等待对端连接..."}))
        
        async for message in websocket:
            data = json.loads(message)
            if data.get("type") == "ping":
                await websocket.send(json.dumps({"type": "pong"}))
            elif peer := rooms[room_id].get(peer_role):
                await peer.send(message)
    except: pass
    finally:
        if info := conn_info.pop(websocket, None):
            rooms[info["room_id"]].pop(info["role"], None)

async def main():
    print("服务器启动: ws://0.0.0.0:8765")
    async with serve(handle_connection, "0.0.0.0", 8765):
        await asyncio.Future()

if __name__ == "__main__":
    asyncio.run(main())
