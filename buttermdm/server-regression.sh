#!/bin/bash
# ButterMDM server regression test: devicereset plugin API, sync hook, signatures, permissions.
# Runs against a live server with a disposable database. It creates the devices DRTEST1/DRTEST2
# and the user drlimited (role "User"), and leaves them in place.
#
# Usage: BASE=http://127.0.0.1:8090 SECRET=<hash.secret> PSQL="psql -h 127.0.0.1 -p 5432 -U buttermdm buttermdm" \
#        ADMIN_USER=admin ADMIN_PASS=admin ./server-regression.sh
set -u
BASE=${BASE:-http://127.0.0.1:8090}
SECRET=${SECRET:?set SECRET to the hash.secret of the server}
PSQL=${PSQL:?set PSQL to a psql command line for the server database}
ADMIN_USER=${ADMIN_USER:-admin}; ADMIN_PASS=${ADMIN_PASS:-admin}
FAILS=0
ok()   { echo "PASS  $1"; }
fail() { echo "FAIL  $1"; FAILS=$((FAILS+1)); }
check() { if [ "$2" = "$3" ]; then ok "$1"; else fail "$1 (expected '$3', got '$2')"; fi; }
md5u() { echo -n "$1" | md5sum | cut -d' ' -f1 | tr a-f A-F; }
sha1() { echo -n "$1" | sha1sum | cut -d' ' -f1; }
token() { curl -s -X POST -H 'Content-Type: application/json' -d "{\"login\":\"$1\",\"password\":\"$(md5u "$2")\"}" \
    "$BASE/rest/public/jwt/login" | python3 -c 'import sys,json;print(json.load(sys.stdin)["id_token"])'; }
api() { # api TOKEN METHOD PATH [BODY] -> prints the JSON "status" and "message"
    curl -s -X "$2" -H "Authorization: Bearer $1" -H 'Content-Type: application/json' ${4:+-d "$4"} "$BASE$3" |
    python3 -c 'import sys,json;d=json.load(sys.stdin);print(d.get("status"),d.get("message") or "")'; }
sql() { $PSQL -At -c "$1"; }
confirm() { # confirm PATH NUMBER SIGNATURE -> HTTP status
    curl -s -o /dev/null -w "%{http_code}" -X POST -H 'Content-Type: application/json' \
    ${3:+-H "X-Request-Signature: $3"} -d "{\"deviceId\":\"$2\"}" "$BASE/rest/plugins/devicereset/public/$1$2"; }
sync() { # sync NUMBER -> prints "<signature check> <json of the added fields>"
    python3 - "$BASE" "$1" "$SECRET" <<'EOF'
import sys, re, hashlib, json, urllib.request
base, number, secret = sys.argv[1:4]
req = urllib.request.Request(f"{base}/rest/public/sync/configuration/{number}",
                             headers={"X-Request-Signature": hashlib.sha1((secret + number).encode()).hexdigest()})
with urllib.request.urlopen(req) as r:
    body = r.read().decode(); sig = r.headers.get("X-Response-Signature") or ""
data = body[body.index('"data":') + 7:len(body) - 1]
calc = hashlib.sha1((secret + re.sub(r"\s", "", data)).encode()).hexdigest()
d = json.loads(data)
added = {k: d[k] for k in ("factoryReset", "reboot", "lock", "lockMessage", "passwordReset") if k in d}
print("SIGOK" if calc.lower() == sig.lower() else "SIGBAD", json.dumps(added, sort_keys=True))
EOF
}

T=$(token "$ADMIN_USER" "$ADMIN_PASS") || { echo "login failed"; exit 1; }
for n in DRTEST1 DRTEST2; do
    [ -z "$(sql "select id from devices where number='$n'")" ] && api "$T" PUT /rest/private/devices "{\"number\":\"$n\",\"configurationId\":1}" >/dev/null
done
ID1=$(sql "select id from devices where number='DRTEST1'"); ID2=$(sql "select id from devices where number='DRTEST2'")
[ -n "$ID1" ] && [ -n "$ID2" ] || { echo "cannot create test devices"; exit 1; }
sql "delete from plugin_devicereset_status where deviceid in ($ID1,$ID2)" >/dev/null

echo "== plugin registered"
check "devicereset in the plugin list" "$(curl -s $BASE/rest/plugin/main/public/registered | grep -c '"devicereset"')" "1"
check "4 permissions" "$(sql "select count(*) from permissions where name like 'plugin_devicereset_%'")" "4"

echo "== nothing pending: response unchanged, signature valid"
check "sync without pending requests" "$(sync DRTEST1)" "SIGOK {}"

echo "== requests"
check "lock" "$(api "$T" POST /rest/plugins/devicereset/private/$ID1/lock '{"message":"Regression lock"}')" "OK "
check "reboot" "$(api "$T" POST /rest/plugins/devicereset/private/$ID1/reboot)" "OK "
check "password" "$(api "$T" POST /rest/plugins/devicereset/private/$ID1/password '{"password":"4321"}')" "OK "
check "password missing -> error" "$(api "$T" POST /rest/plugins/devicereset/private/$ID1/password '{}' | cut -d' ' -f1)" "ERROR"
check "unknown device" "$(api "$T" POST /rest/plugins/devicereset/private/999999/reboot)" "ERROR error.notfound.device"
check "sync delivers the requests" "$(sync DRTEST1)" 'SIGOK {"lock": true, "lockMessage": "Regression lock", "passwordReset": "4321", "reboot": true}'
check "other device unaffected" "$(sync DRTEST2)" "SIGOK {}"
check "status never returns the password" "$(curl -s -H "Authorization: Bearer $T" $BASE/rest/plugins/devicereset/private/$ID1 | grep -c 4321)" "0"

echo "== device confirmations"
S1=$(sha1 "$SECRET""DRTEST1")
check "unsigned -> 403" "$(confirm reboot/ DRTEST1 "")" "403"
check "wrong signature -> 403" "$(confirm reboot/ DRTEST1 deadbeef)" "403"
check "signature of another device -> 403" "$(confirm reboot/ DRTEST1 "$(sha1 "$SECRET""DRTEST2")")" "403"
check "rejected confirmations change nothing" "$(sql "select reboot from plugin_devicereset_status where deviceid=$ID1")" "t"
check "reboot confirm -> 200" "$(confirm reboot/ DRTEST1 "$S1")" "200"
check "reboot cleared, time set" "$(sql "select reboot, rebootconfirmedat is not null from plugin_devicereset_status where deviceid=$ID1")" "f|t"
BEFORE=$(sql "select rebootconfirmedat from plugin_devicereset_status where deviceid=$ID1")
check "repeated confirm -> 200" "$(confirm reboot/ DRTEST1 "$S1")" "200"
check "repeated confirm changes nothing" "$(sql "select rebootconfirmedat from plugin_devicereset_status where deviceid=$ID1")" "$BEFORE"
check "password confirm -> 200" "$(confirm password/ DRTEST1 "$S1")" "200"
check "password removed" "$(sql "select passwordreset is null from plugin_devicereset_status where deviceid=$ID1")" "t"
check "unknown device -> 404" "$(confirm reboot/ NOSUCHDEVICE "$(sha1 "$SECRET""NOSUCHDEVICE")")" "404"
check "lock stays until unlock" "$(sync DRTEST1)" 'SIGOK {"lock": true, "lockMessage": "Regression lock"}'
check "unlock" "$(api "$T" POST /rest/plugins/devicereset/private/$ID1/unlock)" "OK "
check "reset" "$(api "$T" POST /rest/plugins/devicereset/private/$ID1/reset)" "OK "
check "cancel" "$(api "$T" POST /rest/plugins/devicereset/private/$ID1/cancel)" "OK "
check "cancel cleared the reset" "$(sync DRTEST1)" "SIGOK {}"
check "reset" "$(api "$T" POST /rest/plugins/devicereset/private/$ID1/reset)" "OK "
check "reset confirm -> 200" "$(confirm "" DRTEST1 "$S1")" "200"
check "after the reset confirmation nothing is sent" "$(sync DRTEST1)" "SIGOK {}"

echo "== audit log and secrets"
check "requests and confirmations audited" "$(sql "select count(*) from plugin_audit_log where payload like '%Device: DRTEST1%' and action like 'plugin.audit.action.%'" | awk '{print ($1>=8)?"yes":"no"}')" "yes"
check "password not in the audit log" "$(sql "select count(*) from plugin_audit_log where payload like '%4321%'")" "0"

echo "== permissions"
if [ -z "$(sql "select id from users where login='drlimited'")" ]; then
    sql "insert into users (login,email,name,password,customerid,userroleid,alldevicesavailable,allconfigavailable,passwordreset)
         select 'drlimited','drlimited@example.com','Regression',password,customerid,3,true,true,false from users where login='$ADMIN_USER'" >/dev/null
fi
TL=$(token drlimited "$ADMIN_PASS")
check "user without permissions: reboot denied" "$(api "$TL" POST /rest/plugins/devicereset/private/$ID1/reboot)" "ERROR error.permission.denied"
check "user without permissions: status denied" "$(api "$TL" GET /rest/plugins/devicereset/private/$ID1)" "ERROR error.permission.denied"
check "unauthenticated private call rejected" "$(curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/rest/plugins/devicereset/private/$ID1/reboot)" "403"

echo
[ $FAILS -eq 0 ] && echo "ALL PASSED" || echo "$FAILS FAILED"
exit $FAILS
