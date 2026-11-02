#!/usr/bin/env python3
# Re-applies the mechanical ButterMDM branding and launcher changes of the server.
# Idempotent: running it again changes nothing. Run from the repository root.
#
#   buttermdm/reapply.py branding [--check]   user-visible texts, NOTICE
#   buttermdm/reapply.py launcher [--check]   launcher application of the sample data: Butter, no download URL
#   buttermdm/reapply.py all      [--check]
#
# --check: change nothing, list what would change, exit 1 if anything would change.
import base64, glob, os, re, sys


def _b(s):
    return base64.b64decode(s).decode("utf-8")


BRAND_OLD = _b("SGVhZHdpbmQgTURN")
BRAND_LAUNCHER_OLD = _b("SGVhZHdpbmQgTURNIGxhdW5jaGVy")
BRAND_REMOTE_OLD = _b("SGVhZHdpbmQgUmVtb3Rl")
DOMAIN_OLD = _b("aC1tZG0uY29t")
BRAND_NEW = "ButterMDM"
AGENT_PKG = "com.buttermdm.agent"
COMMENT = re.compile(r"^\s*(\*|/\*|//|~|<!--|#)")
changes = []


def update(path, text):
    old = open(path, encoding="utf-8").read()
    if old != text:
        changes.append(path)
        if not CHECK:
            open(path, "w", encoding="utf-8").write(text)


def replace_in_lines(path, old, new):
    lines = open(path, encoding="utf-8").read().split("\n")
    update(path, "\n".join(l if COMMENT.match(l) else l.replace(old, new) for l in lines))


def replace_exact(path, old, new):
    text = open(path, encoding="utf-8").read()
    if new in text and old not in text:
        return
    if old not in text:
        sys.exit(f"{path}: expected text not found, update the rule: {old!r}")
    update(path, text.replace(old, new))


def branding():
    # The console itself (console/<version>, React/TypeScript) is ButterMDM code and needs no re-branding;
    # the plugin i18n bundles are imported into it by console/<version>/scripts/import-legacy-i18n.mjs
    for f in sorted(glob.glob("plugins/*/src/main/webapp/i18n/*.json")):
        replace_in_lines(f, BRAND_OLD, BRAND_NEW)
    for f in sorted(glob.glob("**/src/main/java/**/*.java", recursive=True)):
        text = open(f, encoding="utf-8").read()
        new = "\n".join(l.replace(BRAND_LAUNCHER_OLD, "Butter agent").replace(BRAND_OLD, "Butter")
                        if "@ApiModelProperty" in l else l for l in text.split("\n"))
        if new != text:
            update(f, new)
    replace_exact("swagger/ui/src/main/webapp/WEB-INF/web.xml",
                  f"<display-name>{BRAND_OLD} Server API Swagger UI</display-name>",
                  "<display-name>ButterMDM Server API Swagger UI</display-name>")
    replace_exact("pom.xml", f"<name>{BRAND_OLD}</name>", "<name>ButterMDM</name>")
    for f in ("buttermdm_install.sh", "cpu_monitor.sh", "letsencrypt-ssl.sh", "init-plugin.sh", "init-plugin.bat",
              "BUILD.txt", "INSTALL.txt"):
        update(f, open(f, encoding="utf-8").read().replace(BRAND_OLD, BRAND_NEW))
    for sql in sorted(glob.glob("install/sql/buttermdm_init.*.sql")):
        lines = open(sql, encoding="utf-8").read().split("\n")
        update(sql, "\n".join(l if l.lstrip().startswith("(46, '") else l.replace(BRAND_OLD, "Butter") for l in lines))
    replace_exact("common/src/main/java/com/buttermdm/service/EmailService.java",
                  f'appName = "{BRAND_OLD}";', 'appName = "ButterMDM";')
    replace_exact("server/src/main/java/com/buttermdm/ButterMDMApplication.java",
                  f'beanConfig.setTitle("{BRAND_OLD} API");', 'beanConfig.setTitle("ButterMDM API");')
    replace_exact("server/src/main/java/com/buttermdm/task/CustomerStatusTask.java",
                  f'String adminSubj = "{BRAND_OLD} customer status changed";',
                  'String adminSubj = "ButterMDM customer status changed";')
    replace_exact("server/src/main/webapp/WEB-INF/web.xml",
                  f"<display-name>{BRAND_OLD}</display-name>", "<display-name>ButterMDM</display-name>")
    notice = open("NOTICE", encoding="utf-8").read()
    if "ButterMDM: Open Source Android Mobile Device Management Software" not in notice:
        sys.exit("NOTICE: unexpected format, update NOTICE with ButterMDM header")


def launcher():
    for sql in sorted(glob.glob("install/sql/buttermdm_init.*.sql")):
        text = open(sql, encoding="utf-8").read()
        text = re.sub(r"\(46, '" + re.escape(AGENT_PKG) + r"', '[^']*',", f"(46, '{AGENT_PKG}', 'Butter',", text)
        text = re.sub(r"\(10045, 46, '_BUTTERMDM_VERSION_', '[^']*'\)", "(10045, 46, '_BUTTERMDM_VERSION_', NULL)", text)
        update(sql, text)


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if a != "--check"]
    CHECK = "--check" in sys.argv
    if len(args) != 1 or args[0] not in ("branding", "launcher", "all") or not os.path.isdir("server/src/main/webapp"):
        sys.exit("usage (from the repository root): buttermdm/reapply.py branding|launcher|all [--check]")
    if args[0] in ("branding", "all"):
        branding()
    if args[0] in ("launcher", "all"):
        launcher()
    for c in changes:
        print(("would change: " if CHECK else "changed: ") + c)
    if not changes:
        print("nothing to change")
    sys.exit(1 if CHECK and changes else 0)
