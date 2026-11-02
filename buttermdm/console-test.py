# Drives the ButterMDM console (devicereset page of the React console) in headless Brave through the DevTools protocol
# Needs Python "websockets" and a Chromium-based browser (set CHROMIUM=/path/to/binary; default: Brave).
# Usage: python3 console-test.py [screenshot-dir]  (server at BASE, login admin/admin, device DRTEST1 must exist)
import asyncio, json, base64, subprocess, time, urllib.request, sys, websockets
BASE = __import__("os").environ.get("BASE", "http://127.0.0.1:8090")
OUT = sys.argv[1] if len(sys.argv) > 1 else "."
__import__("os").makedirs(OUT, exist_ok=True)
proc = subprocess.Popen([__import__("os").environ.get("CHROMIUM", "/opt/brave.com/brave/brave"), "--headless=new", "--disable-gpu", "--no-first-run",
                         "--remote-debugging-port=9444", "--user-data-dir=/tmp/buttermdm-console-test-profile",
                         "--window-size=1600,1000", "about:blank"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
for _ in range(150):
    try:
        pages = json.load(urllib.request.urlopen("http://127.0.0.1:9444/json")); break
    except Exception: time.sleep(0.2)
ws_url = [p for p in pages if p["type"] == "page"][0]["webSocketDebuggerUrl"]

async def main():
    async with websockets.connect(ws_url, max_size=50_000_000) as ws:
        n = 0
        async def cmd(method, **params):
            nonlocal n; n += 1; my = n
            await ws.send(json.dumps({"id": my, "method": method, "params": params}))
            while True:
                m = json.loads(await ws.recv())
                if m.get("id") == my: return m.get("result", m.get("error"))
        async def js(expr):
            r = await cmd("Runtime.evaluate", expression=expr, awaitPromise=True, returnByValue=True)
            return r.get("result", {}).get("value")
        async def shot(name):
            r = await cmd("Page.captureScreenshot", format="png")
            open(f"{OUT}/{name}.png", "wb").write(base64.b64decode(r["data"]))
        async def wait(expr, t=20):
            for _ in range(t * 4):
                if await js(expr): return True
                await asyncio.sleep(0.25)
            return False
        def setval(sel, val):
            # React listens to the native value setter + input event
            return f"(()=>{{const e=document.querySelector({json.dumps(sel)}); if(!e) return false; Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(e,{json.dumps(val)}); e.dispatchEvent(new Event('input',{{bubbles:true}})); return true}})()"
        def click_text(text):
            return f"(()=>{{const b=[...document.querySelectorAll('button,a')].find(x=>x.offsetParent && x.textContent.trim()=={json.dumps(text)}); if(!b) return false; b.click(); return true}})()"
        status = "(()=>{const s=[...document.querySelectorAll('.bm-kv')].map(e=>e.innerText.replace(/\\n/g,' ')); return s.join(' | ')})()"
        await cmd("Page.enable"); await cmd("Runtime.enable")
        await cmd("Page.navigate", url=BASE + "/#/login")
        await wait("!!document.querySelector('#username')")
        await asyncio.sleep(1)
        await js(setval("#username", "admin"))
        await js(setval("#password", "admin"))
        await js("document.querySelector('#password').form.querySelector('button[type=submit]').click()")
        await wait("location.hash.indexOf('login')<0 && document.body.innerText.indexOf('Devices')>=0", 30)
        await asyncio.sleep(2)
        print("title:", await js("document.title"), "| brand:", await js("(document.body.innerText.match(/ButterMDM/g)||[]).slice(0,3).join(',')"))
        await shot("01-dashboard")
        # Devices list: the row menu of DRTEST1 must contain the plugin
        await cmd("Page.navigate", url=BASE + "/#/devices")
        await wait("[...document.querySelectorAll('tr')].some(t=>t.innerText.indexOf('DRTEST1')>=0)", 20)
        await asyncio.sleep(1)
        # The row menu (Radix dropdown) opens on pointerdown, not on click
        print("open menu:", await js("(()=>{const r=[...document.querySelectorAll('tr')].find(t=>t.innerText.indexOf('DRTEST1')>=0); if(!r) return 'row not found'; const b=r.querySelector('button[aria-haspopup=menu]'); if(!b) return 'menu button not found'; b.dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,button:0,pointerType:'mouse'})); return true})()"))
        await wait("[...document.querySelectorAll('[role=menuitem]')].some(a=>a.textContent.trim()=='Device reset')", 10)
        print("DRTEST1 menu:", await js("[...document.querySelectorAll('[role=menuitem]')].map(a=>a.textContent.trim()).join('|')"))
        await shot("02-device-menu")
        print("open plugin:", await js("(()=>{const a=[...document.querySelectorAll('[role=menuitem]')].find(x=>x.textContent.trim()=='Device reset'); if(!a) return false; a.click(); return true})()"))
        await wait("location.hash.indexOf('functions/devicereset')>=0 && document.body.innerText.indexOf('Factory reset')>=0", 15)
        await asyncio.sleep(1)
        print("url:", await js("location.hash"))
        print("page:", await js(status))
        await shot("03-devicereset")
        await js(setval("#dr-lock", "Locked from the console"))
        print("click lock:", await js(click_text("Lock")))
        await wait("document.body.innerText.indexOf('The request has been sent')>=0", 10)
        await asyncio.sleep(1)
        print("after lock:", await js(status))
        await shot("04-locked")
        print("click unlock:", await js(click_text("Unlock")))
        await asyncio.sleep(2)
        print("click reset:", await js(click_text("Factory reset")))
        await wait("!!document.querySelector('[role=alertdialog]')", 5)
        print("confirm dialog:", (await js("(document.querySelector('[role=alertdialog]')||{}).innerText")))
        await shot("05-confirm")
        print("confirm:", await js(click_text("Yes")))
        await asyncio.sleep(2)
        print("after reset:", await js(status))
        print("click cancel:", await js(click_text("Cancel pending requests")))
        await asyncio.sleep(2)
        print("after cancel:", await js(status))
        await shot("06-final")
asyncio.run(main())
proc.terminate()
