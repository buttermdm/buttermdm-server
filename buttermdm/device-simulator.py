#!/usr/bin/env python3
# Simulates the remote support side of a Butter device, for tests without a device: polls the device's Push
# messages like the agent, joins the remote support sessions requested for it, streams generated screen frames
# (JPEG, drawn with ImageMagick) and draws the taps it receives into the next frames.
#
# Usage: BASE=http://127.0.0.1:8090 SECRET=<hash.secret> python3 buttermdm/device-simulator.py <device number>
#        [--once] [--fps N] [--decline] [--quiet]
#   --once     exit after the first session (used by remotesupport-regression.py)
#   --decline  decline sessions that need the acceptance of the person at the device
import asyncio, hashlib, json, os, subprocess, sys, time, urllib.request
import websockets

BASE = os.environ.get("BASE", "http://127.0.0.1:8090")
SECRET = os.environ["SECRET"]
args = [a for a in sys.argv[1:] if not a.startswith("--")]
NUMBER = args[0] if args else sys.exit("usage: device-simulator.py <device number> [--once] [--fps N] [--decline]")
ONCE, DECLINE, QUIET = "--once" in sys.argv, "--decline" in sys.argv, "--quiet" in sys.argv
FPS = float(sys.argv[sys.argv.index("--fps") + 1]) if "--fps" in sys.argv else 4
WIDTH, HEIGHT = 360, 800
SIGNATURE = hashlib.sha1((SECRET + NUMBER).encode()).hexdigest()


def log(*parts):
    if not QUIET:
        print("[device " + NUMBER + "]", *parts, flush=True)


def poll_push():
    request = urllib.request.Request(f"{BASE}/rest/notifications/device/{NUMBER}",
                                     headers={"X-Request-Signature": SIGNATURE})
    with urllib.request.urlopen(request, timeout=10) as response:
        return json.loads(response.read().decode()).get("data") or []


def frame(number, taps, text):
    draw = []
    for x, y in taps[-5:]:
        px, py = int(x * WIDTH), int(y * HEIGHT)
        draw += ["-fill", "#d9534f", "-draw", f"circle {px},{py} {px + 12},{py}"]
    command = ["magick", "-size", f"{WIDTH}x{HEIGHT}", "xc:#f7e08a", "-fill", "#333333", "-gravity", "north",
               "-pointsize", "26", "-annotate", "+0+40", f"Simulated device\n{NUMBER}",
               "-pointsize", "20", "-annotate", "+0+160", f"frame {number}\n{text}"] + draw + ["-quality", "70", "jpeg:-"]
    return subprocess.run(command, check=True, capture_output=True).stdout


async def run_session(payload):
    url = BASE.replace("http", "ws", 1) + payload["path"] + f"?session={payload['sessionId']}&token={payload['token']}"
    log("joining session", payload["sessionId"], "(view only)" if payload.get("viewOnly") else "")
    taps, events = [], []
    async with websockets.connect(url, max_size=8_000_000) as ws:
        await ws.send(json.dumps({"t": "hello", "w": WIDTH, "h": HEIGHT, "sdk": 29, "capture": "simulated",
                                  "input": True}))
        if payload.get("requireAcceptance"):
            await ws.send(json.dumps({"t": "status", "state": "waitingAcceptance"}))
            await asyncio.sleep(0.5)
            if DECLINE:
                log("declining the session")
                await ws.send(json.dumps({"t": "end", "reason": "declined on the device"}))
                return
        await ws.send(json.dumps({"t": "status", "state": "streaming"}))
        done = asyncio.Event()

        async def receive():
            try:
                async for message in ws:
                    if isinstance(message, bytes):
                        continue
                    data = json.loads(message)
                    if data.get("t") == "tap":
                        taps.append((data["x"], data["y"]))
                    if data.get("t") in ("tap", "swipe", "key", "text"):
                        events.append(data)
                        log("input", message)
                        # Echo the input as a status message so tests can check the round trip
                        await ws.send(json.dumps({"t": "status", "state": "input", "received": data}))
                    elif data.get("t") == "end":
                        log("session ended:", data.get("reason"))
            finally:
                done.set()

        receiver = asyncio.create_task(receive())
        number = 0
        while not done.is_set():
            last = events[-1] if events else {}
            text = f"last input: {last.get('t', '-')}" + (f" {last.get('key') or last.get('text') or ''}" if last else "")
            try:
                await ws.send(frame(number, taps, text))
            except websockets.ConnectionClosed:
                break
            number += 1
            try:
                await asyncio.wait_for(done.wait(), timeout=1 / FPS)
            except asyncio.TimeoutError:
                pass
        await receiver
    log(f"session finished after {number} frames")


async def main():
    log("polling Push messages at", BASE)
    while True:
        try:
            messages = await asyncio.get_running_loop().run_in_executor(None, poll_push)
        except Exception as e:
            log("poll failed:", e)
            messages = []
        for message in messages:
            if message.get("messageType") == "remoteSession":
                await run_session(json.loads(message["payload"]))
                if ONCE:
                    return
        await asyncio.sleep(1)


asyncio.run(main())
