# ZeroTrace Demo Runbook

Use this document tomorrow to bring ZeroTrace online quickly for the demo.

## What must be running

You need 3 things:

1. The `auth-server` Spring Boot backend
2. The Cloudflare quick tunnel
3. The Windows client app

Keep the server terminal open and the Cloudflare terminal open the whole time.

## Before you start

1. Plug in your laptop charger
2. Turn off sleep while plugged in
3. Make sure internet is stable

## Terminal 1: Start the backend server

Open PowerShell and run:

```powershell
cd C:\Users\kashm\OneDrive\Desktop\ZEROTRACE\auth-server
.\mvnw.cmd clean package -DskipTests
java -jar target\auth-server-0.0.1-SNAPSHOT.jar
```

Wait until you see output like:

```text
Tomcat started on port 8080
Started AuthServerApplication
```

Do not close this window.

## Terminal 2: Start the Cloudflare tunnel

Open a second PowerShell window and run:

```powershell
cd "C:\Program Files\cloudfare"
.\cloudflared-windows-amd64.exe tunnel --url http://localhost:8080
```

Wait until Cloudflare prints a URL like:

```text
https://something.trycloudflare.com
```

That URL is your public demo backend URL.

Do not close this window.

## Terminal 3: Test that the public backend is alive

Open a third PowerShell window and replace `<TUNNEL_URL>` with the URL from Terminal 2:

```powershell
Invoke-WebRequest <TUNNEL_URL>/message/ping -UseBasicParsing
```

Expected result:

```text
Message relay working
```

If this fails:

1. Check the server in Terminal 1 is still running
2. Check the Cloudflare tunnel in Terminal 2 is still running
3. Make sure the tunnel URL is copied exactly

## Start the client app

Open the packaged Windows app here:

```text
C:\Users\kashm\OneDrive\Desktop\ZEROTRACE\ZeroTrace_Core\dist\ZeroTrace\ZeroTrace.exe
```

Or open the zipped distribution here:

```text
C:\Users\kashm\OneDrive\Desktop\ZEROTRACE\ZeroTrace_Core\dist\ZeroTrace-Windows.zip
```

## What to enter in the client

On the login screen:

1. In the server field, paste the Cloudflare tunnel URL from Terminal 2
2. Register or log in

Example:

```text
https://vanilla-trial-privacy-trailer.trycloudflare.com
```

## If the app still shows localhost

If the client is restoring an old session and still points to `http://localhost:8080`:

1. Click `Logout` in the app
2. Log in again with the tunnel URL

If needed, delete the saved session file:

```powershell
Remove-Item "$env:USERPROFILE\.zerotrace\session.properties" -Force
```

Then reopen the app.

## What to tell teammates

Tell them:

1. Run `ZeroTrace.exe`
2. Enter the current Cloudflare tunnel URL in the server field
3. Register/login

## Important demo warnings

This setup is temporary.

If any of these happen, the app stops working:

1. Your laptop sleeps
2. Your laptop shuts down
3. The server terminal closes
4. The Cloudflare tunnel terminal closes

The Cloudflare quick tunnel URL may change every time you restart it.

## Fast recovery steps

If the demo setup breaks:

1. Restart the backend:

```powershell
cd C:\Users\kashm\OneDrive\Desktop\ZEROTRACE\auth-server
java -jar target\auth-server-0.0.1-SNAPSHOT.jar
```

2. Restart the tunnel:

```powershell
cd "C:\Program Files\cloudfare"
.\cloudflared-windows-amd64.exe tunnel --url http://localhost:8080
```

3. Copy the new tunnel URL
4. Re-enter that new URL in the client login screen

## Quick demo checklist

1. Start backend
2. Start tunnel
3. Test `/message/ping`
4. Open `ZeroTrace.exe`
5. Paste tunnel URL into server field
6. Log in
7. Demo messaging
8. Keep both terminals open
