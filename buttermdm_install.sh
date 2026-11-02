#!/usr/bin/env bash
#
# ButterMDM server installer
#
# Supported systems (detected automatically from /etc/os-release):
#   Debian 10, 11, 12, 13 and Ubuntu 18.04 - 24.04 (and other apt-based derivatives)
#
# ButterMDM is a Java EE (javax.*) web application and runs on Apache Tomcat 9.
# Tomcat 10 and later (Jakarta EE) cannot run it, so the installer uses:
#   - the tomcat9 package where the distribution has one (Debian 10-12, Ubuntu 20.04 / 22.04),
#   - otherwise the official Apache Tomcat 9 binary release from apache.org (checksum verified),
#     installed in the same layout: /opt/tomcat9, /var/lib/tomcat9, service tomcat9, user tomcat.
#     This is the case on Debian 13, which packages only tomcat10 and tomcat11.
#
# The installer is a bootstrapper: it compiles ButterMDM from source before deploying it.
#   - server: this directory when the installer runs from a source tree, otherwise the tag
#     v<SERVER_VERSION> of the buttermdm-server repository is downloaded
#   - web console: the tag v<CONSOLE_VERSION> of the buttermdm-console repository
#     (or a local copy given with --console DIR)
#   Run with --help for the options.
#
# Optional environment overrides for non-standard setups:
#   TOMCAT_HOME=/path/to/catalina_base   TOMCAT_SERVICE=<systemd service>   TOMCAT_USER=<user>
#   TOMCAT9_HOME / TOMCAT9_BASE: where the Apache Tomcat 9 release is installed
#                                (default /opt/tomcat9 and /var/lib/tomcat9)
#   BUTTERMDM_SERVER_REPO / BUTTERMDM_CONSOLE_REPO: git repositories of the source code
#   BUTTERMDM_BUILD_DIR: where the source is compiled (default /usr/local/src/buttermdm)
#
SERVER_VERSION=0.1.0
CONSOLE_VERSION=0.1.0
CLIENT_VERSION=0.1.0
CLIENT_VARIANT="os"
CLIENT_APK="buttermdm-$CLIENT_VERSION-$CLIENT_VARIANT.apk"

DEFAULT_SQL_HOST=localhost
DEFAULT_SQL_PORT=5432
DEFAULT_SQL_BASE=buttermdm
DEFAULT_SQL_USER=buttermdm
DEFAULT_LOCATION="/opt/buttermdm"
DEFAULT_SCRIPT_LOCATION="/opt/buttermdm"
DEFAULT_PROTOCOL=https
DEFAULT_BASE_PATH="ROOT"
TOMCAT_ENGINE="Catalina"
TOMCAT_HOST="localhost"
TOMCAT_HTTP_PORT=8080
TOMCAT_HTTPS_PORT=8443
SSL_KEYSTORE_PASSWORD=123456          # must match PASSWORD in letsencrypt-ssl.sh
DEPLOY_TIMEOUT=180                    # seconds to wait for the web application to start
LOG_FILE="/var/log/buttermdm-install.log"

SERVER_REPO="${BUTTERMDM_SERVER_REPO:-https://github.com/buttermdm/buttermdm-server.git}"
SERVER_TAG="v$SERVER_VERSION"
CONSOLE_REPO="${BUTTERMDM_CONSOLE_REPO:-https://github.com/buttermdm/buttermdm-console.git}"
CONSOLE_TAG="v$CONSOLE_VERSION"
BUILD_DIR="${BUTTERMDM_BUILD_DIR:-/usr/local/src/buttermdm}"
BUILD_MIN_FREE_MB=2048                # Maven dependencies, Node.js and npm packages
SERVER_WAR=""                         # --war FILE: skip the build and install this web application
CONSOLE_DIR=""                        # --console DIR: local web console source instead of the git tag

TOMCAT_HOME="${TOMCAT_HOME:-}"
TOMCAT_SERVICE="${TOMCAT_SERVICE:-}"
TOMCAT_USER="${TOMCAT_USER:-}"
TOMCAT_MAJOR=""
TOMCAT9_FALLBACK_VERSION=9.0.123   # used when the latest Tomcat 9 release cannot be looked up
TOMCAT9_HOME="${TOMCAT9_HOME:-/opt/tomcat9}"      # CATALINA_HOME of an Apache Tomcat 9 installed by this script
TOMCAT9_BASE="${TOMCAT9_BASE:-/var/lib/tomcat9}"  # CATALINA_BASE (same as the Debian/Ubuntu package)
JAKARTA_TOMCATS=()
TOMCAT_VERSION_FULL=""
DB_NEEDS_ERASE=0

ADMIN_EMAIL=
SMTP_HOST=
SMTP_PORT=
SMTP_SSL=0
SMTP_STARTTLS=0
SMTP_USERNAME=
SMTP_PASSWORD=
SMTP_FROM=

# ---------------------------------------------------------------------------
# Terminal output
# ---------------------------------------------------------------------------

setup_ui() {
    if [[ -t 1 && -z "${NO_COLOR:-}" && "${TERM:-dumb}" != "dumb" ]]; then
        C_RESET=$'\e[0m'; C_BOLD=$'\e[1m'; C_DIM=$'\e[2m'
        C_RED=$'\e[31m'; C_GREEN=$'\e[32m'; C_YELLOW=$'\e[33m'; C_BLUE=$'\e[34m'; C_CYAN=$'\e[36m'
        C_BUTTER=$'\e[38;5;221m'
    else
        C_RESET=; C_BOLD=; C_DIM=; C_RED=; C_GREEN=; C_YELLOW=; C_BLUE=; C_CYAN=; C_BUTTER=
    fi
    # readline needs non-printing sequences in prompts wrapped in \001 ... \002
    P_CYAN=${C_CYAN:+$'\001'$C_CYAN$'\002'}
    P_BOLD=${C_BOLD:+$'\001'$C_BOLD$'\002'}
    P_RESET=${C_RESET:+$'\001'$C_RESET$'\002'}

    if [[ "$(locale charmap 2>/dev/null)" == "UTF-8" ]]; then
        S_OK="✔"; S_ERR="✖"; S_WARN="!"; S_INFO="•"; S_ASK="?"; S_LINE="─"
        S_TL="╭"; S_TR="╮"; S_BL="╰"; S_BR="╯"; S_V="│"; SPINNER=(⠋ ⠙ ⠹ ⠸ ⠼ ⠴ ⠦ ⠧ ⠇ ⠏)
    else
        S_OK="OK"; S_ERR="X"; S_WARN="!"; S_INFO="*"; S_ASK="?"; S_LINE="-"
        S_TL="+"; S_TR="+"; S_BL="+"; S_BR="+"; S_V="|"; SPINNER=('|' '/' '-' "\\")
    fi
    STEP_TOTAL=11
    STEP_CURRENT=0
}

repeat_char() { local s; printf -v s '%*s' "$2" ''; printf '%s' "${s// /$1}"; }

banner() {
    local width=58 line
    line=$(repeat_char "$S_LINE" "$width")
    echo
    printf '  %s%s%s%s%s\n' "$C_BUTTER" "$S_TL" "$line" "$S_TR" "$C_RESET"
    printf '  %s%s%s %-*s %s%s%s\n' "$C_BUTTER" "$S_V" "$C_RESET$C_BOLD" $((width - 2)) "ButterMDM Server Installer  v$SERVER_VERSION" "$C_RESET$C_BUTTER" "$S_V" "$C_RESET"
    printf '  %s%s%s %-*s %s%s%s\n' "$C_BUTTER" "$S_V" "$C_RESET$C_DIM" $((width - 2)) "Open Source Mobile Device Management" "$C_RESET$C_BUTTER" "$S_V" "$C_RESET"
    printf '  %s%s%s%s%s\n' "$C_BUTTER" "$S_BL" "$line" "$S_BR" "$C_RESET"
    echo
}

section() {
    STEP_CURRENT=$((STEP_CURRENT + 1))
    echo
    printf '  %s[%d/%d]%s %s%s%s\n' "$C_BUTTER$C_BOLD" "$STEP_CURRENT" "$STEP_TOTAL" "$C_RESET" "$C_BOLD" "$1" "$C_RESET"
    printf '  %s%s%s\n' "$C_DIM" "$(repeat_char "$S_LINE" 58)" "$C_RESET"
}

info()  { printf '  %s%s%s %s\n' "$C_BLUE" "$S_INFO" "$C_RESET" "$*"; }
ok()    { printf '  %s%s%s %s\n' "$C_GREEN" "$S_OK" "$C_RESET" "$*"; }
warn()  { printf '  %s%s%s %s\n' "$C_YELLOW" "$S_WARN" "$C_RESET" "$*"; }
err()   { printf '  %s%s %s%s\n' "$C_RED" "$S_ERR" "$*" "$C_RESET" >&2; }
note()  { printf '    %s%s%s\n' "$C_DIM" "$*" "$C_RESET"; }
die()   { err "$*"; echo; exit 1; }

# kv "Label" "value": aligned summary line
kv() { printf '    %s%-22s%s %s\n' "$C_DIM" "$1" "$C_RESET" "$2"; }

# ask VAR "Question" "default": editable answer, the default is pre-filled
ask() {
    local __var=$1 __question=$2 __default=${3:-} __reply
    IFS= read -r -e -i "$__default" -p "  ${P_CYAN}${S_ASK}${P_RESET} ${P_BOLD}${__question}${P_RESET} " __reply \
        || die "Input closed, installation aborted."
    printf -v "$__var" '%s' "$__reply"
}

# ask_required VAR "Question" "default": like ask, repeats until the answer is not empty
ask_required() {
    while true; do
        ask "$@"
        [[ -n "${!1}" ]] && return 0
        warn "A value is required."
    done
}

# ask_secret VAR "Question": hidden input
ask_secret() {
    local __var=$1 __question=$2 __reply
    IFS= read -r -s -p "  ${C_CYAN}${S_ASK}${C_RESET} ${C_BOLD}${__question}${C_RESET} " __reply \
        || die "Input closed, installation aborted."
    echo
    printf -v "$__var" '%s' "$__reply"
}

# confirm "Question" [Y|N]: returns 0 for yes; Enter picks the default
confirm() {
    local question=$1 default=${2:-Y} hint reply
    [[ "$default" == "Y" ]] && hint="Y/n" || hint="y/N"
    while true; do
        IFS= read -r -p "  ${C_CYAN}${S_ASK}${C_RESET} ${C_BOLD}${question}${C_RESET} ${C_DIM}[${hint}]${C_RESET} " reply \
            || die "Input closed, installation aborted."
        reply=${reply:-$default}
        case "$reply" in
            [Yy]|[Yy][Ee][Ss]) return 0 ;;
            [Nn]|[Nn][Oo]) return 1 ;;
        esac
        warn "Please answer y or n."
    done
}

# run "Message" command args...: runs a command with a spinner, output goes to the log file
run() {
    local message=$1; shift
    local pid rc i=0 started=$SECONDS elapsed
    {
        echo
        echo "=== $(date '+%F %T') $message"
        echo "\$ $*"
    } >>"$LOG_FILE"
    "$@" >>"$LOG_FILE" 2>&1 </dev/null &
    pid=$!
    if [[ -t 1 ]]; then
        while kill -0 "$pid" 2>/dev/null; do
            elapsed=$((SECONDS - started))
            if [[ $elapsed -ge 5 ]]; then
                printf '\r  %s%s%s %s %s(%dm %02ds)%s' "$C_CYAN" "${SPINNER[i % ${#SPINNER[@]}]}" "$C_RESET" \
                    "$message" "$C_DIM" $((elapsed / 60)) $((elapsed % 60)) "$C_RESET"
            else
                printf '\r  %s%s%s %s' "$C_CYAN" "${SPINNER[i % ${#SPINNER[@]}]}" "$C_RESET" "$message"
            fi
            i=$((i + 1))
            sleep 0.1
        done
    fi
    wait "$pid"
    rc=$?
    if [[ -t 1 ]]; then printf '\r\e[K'; fi
    if [[ $rc -eq 0 ]]; then
        ok "$message"
    else
        err "$message (failed, exit code $rc)"
        note "Last lines of $LOG_FILE:"
        tail -n 15 "$LOG_FILE" | sed 's/^/      /' >&2
    fi
    return $rc
}

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

# Escapes a value for the replacement part of a sed s|...|...| command
sed_escape() { printf '%s' "$1" | sed -e 's/[\\|&]/\\&/g'; }

# Escapes a value for an XML attribute
xml_escape() {
    local s=$1
    # quoted replacements: with patsub_replacement (bash 5.2+) a bare & means the matched text
    s=${s//&/"&amp;"}; s=${s//</"&lt;"}; s=${s//>/"&gt;"}; s=${s//\"/"&quot;"}; s=${s//\'/"&apos;"}
    printf '%s' "$s"
}

# Escapes a value for a single-quoted SQL string
sql_escape() { printf '%s' "${1//\'/\'\'}"; }

have() { command -v "$1" >/dev/null 2>&1; }

has_systemd() { [[ -d /run/systemd/system ]] && have systemctl; }

service_ctl() { # service_ctl <start|restart|stop> <name>
    if has_systemd; then
        systemctl "$1" "$2"
    else
        service "$2" "$1"
    fi
}

service_active() {
    if has_systemd; then
        systemctl is-active --quiet "$1"
    else
        service "$1" status >/dev/null 2>&1
    fi
}

# Runs psql against the ButterMDM database with the entered credentials
# shellcheck disable=SC2153  # SQL_* are set by step_database through printf -v
db_psql() {
    PGPASSWORD="$SQL_PASS" PGCONNECT_TIMEOUT=10 psql -X -q -v ON_ERROR_STOP=1 \
        -h "$SQL_HOST" -p "$SQL_PORT" -U "$SQL_USER" -d "$SQL_BASE" "$@"
}

is_local_db_host() { [[ "$SQL_HOST" == "localhost" || "$SQL_HOST" == "127.0.0.1" || "$SQL_HOST" == "::1" ]]; }

add_cron_line() { # add_cron_line "<pattern to detect>" "<line>"
    if crontab -l 2>/dev/null | grep -qF "$1"; then
        return 0
    fi
    { crontab -l 2>/dev/null; echo "$2"; } | crontab -
}

# ---------------------------------------------------------------------------
# System detection
# ---------------------------------------------------------------------------

detect_distro() {
    DISTRO_ID=unknown; DISTRO_VERSION=""; DISTRO_LIKE=""; DISTRO_NAME="Unknown Linux"
    if [[ -r /etc/os-release ]]; then
        local data
        # shellcheck disable=SC1091
        data=$(. /etc/os-release; printf '%s\n' "${ID:-unknown}" "${VERSION_ID:-}" "${ID_LIKE:-}" "${PRETTY_NAME:-${NAME:-Linux}}")
        { read -r DISTRO_ID; read -r DISTRO_VERSION; read -r DISTRO_LIKE; read -r DISTRO_NAME; } <<<"$data"
    fi

    PKG_MANAGER=none
    DISTRO_SUPPORT=unsupported
    case "$DISTRO_ID" in
        debian)
            PKG_MANAGER=apt
            case "${DISTRO_VERSION%%.*}" in
                10|11|12|13) DISTRO_SUPPORT=tested ;;
                *) DISTRO_SUPPORT=untested ;;
            esac
            ;;
        ubuntu)
            PKG_MANAGER=apt
            case "$DISTRO_VERSION" in
                18.04|20.04|22.04|24.04) DISTRO_SUPPORT=tested ;;
                *) DISTRO_SUPPORT=untested ;;
            esac
            ;;
        *)
            if [[ " $DISTRO_LIKE " == *" debian "* || " $DISTRO_LIKE " == *" ubuntu "* ]] && have apt-get; then
                PKG_MANAGER=apt
                DISTRO_SUPPORT=derivative
            fi
            ;;
    esac
}

# Prints the candidate version of an apt package, empty when it is not available
apt_candidate() {
    local c
    c=$(LC_ALL=C apt-cache policy "$1" 2>/dev/null | awk '/Candidate:/ {print $2; exit}')
    [[ "$c" == "(none)" ]] && c=""
    printf '%s' "$c"
}

# Finds an installed Tomcat that can run ButterMDM (Debian/Ubuntu layout: /var/lib/tomcatN/webapps).
# ButterMDM uses the Java EE (javax.*) API, so only Tomcat 9 (or 8.5) qualifies.
detect_installed_tomcat() {
    if [[ -n "$TOMCAT_HOME" ]]; then
        [[ -d "$TOMCAT_HOME/webapps" ]] || return 1
    else
        local dir
        for dir in "$TOMCAT9_BASE" /var/lib/tomcat9 /var/lib/tomcat8; do
            if [[ -d "$dir/webapps" ]]; then
                TOMCAT_HOME=$dir
                break
            fi
        done
        [[ -n "$TOMCAT_HOME" ]] || return 1
    fi
    if [[ -z "$TOMCAT_SERVICE" ]]; then
        TOMCAT_SERVICE=$(basename "$TOMCAT_HOME")
        [[ "$TOMCAT_HOME" == "$TOMCAT9_BASE" ]] && TOMCAT_SERVICE=tomcat9
    fi
    [[ -n "$TOMCAT_USER" ]] || TOMCAT_USER=$(stat -c '%U' "$TOMCAT_HOME/webapps")
    detect_tomcat_major
    detect_tomcat_http_port
    return 0
}

# Tomcat 10 and later implement Jakarta EE (jakarta.*) and cannot run ButterMDM
detect_jakarta_tomcats() {
    JAKARTA_TOMCATS=()
    local v
    for v in 10 11 12; do
        [[ -d "/var/lib/tomcat$v/webapps" ]] && JAKARTA_TOMCATS+=("tomcat$v")
    done
}

detect_tomcat_major() {
    TOMCAT_MAJOR=""
    TOMCAT_VERSION_FULL=""
    local catalina_home number
    for catalina_home in "/usr/share/$TOMCAT_SERVICE" "$TOMCAT9_HOME" "/opt/$TOMCAT_SERVICE"; do
        [[ "$catalina_home" == "$TOMCAT9_HOME" && "$TOMCAT_SERVICE" != "tomcat9" ]] && continue
        [[ -f "$catalina_home/bin/version.sh" ]] || continue
        number=$(CATALINA_HOME="$catalina_home" CATALINA_BASE="$TOMCAT_HOME" sh "$catalina_home/bin/version.sh" 2>/dev/null \
            | awk -F': *' '/Server number/ {print $2; exit}')
        if [[ -n "$number" ]]; then
            TOMCAT_MAJOR=${number%%.*}
            TOMCAT_VERSION_FULL=$number
            break
        fi
    done
    if [[ -z "$TOMCAT_MAJOR" && "$TOMCAT_SERVICE" =~ ^tomcat([0-9]+)$ ]]; then
        TOMCAT_MAJOR=${BASH_REMATCH[1]}
    fi
}

# Reads the HTTP port of Tomcat from server.xml (first plain HTTP connector, comments ignored)
detect_tomcat_http_port() {
    local port
    port=$(perl -0777 -ne 's/<!--.*?-->//gs;
        while (/<Connector\b([^>]*)>/g) { my $a = $1; next if $a =~ /SSLEnabled="true"|AJP/i;
            if ($a =~ /\bport="(\d+)"/) { print $1; last } }' "$TOMCAT_HOME/conf/server.xml" 2>/dev/null)
    [[ "$port" =~ ^[0-9]+$ ]] && TOMCAT_HTTP_PORT=$port
}

port_in_use() { ss -ltn 2>/dev/null | awk 'NR > 1 {print $4}' | grep -qE "[:.]$1\$"; }

# Makes sure the HTTP port of the new Tomcat 9 is free (Debian's tomcat10 also uses 8080)
ensure_port_free() {
    local port=$1 svc
    port_in_use "$port" || return 0
    for svc in tomcat10 tomcat11; do
        if systemctl is-active --quiet "$svc" 2>/dev/null; then
            warn "$svc is running on port $port, which Tomcat 9 needs; $svc cannot run ButterMDM."
            if confirm "Stop and disable $svc?" Y; then
                run "Stopping and disabling $svc" systemctl disable --now "$svc"
                sleep 2
            fi
        fi
    done
    if port_in_use "$port"; then
        err "Port $port is used by another program."
        note "Free the port, or change the Connector port in $TOMCAT_HOME/conf/server.xml,"
        note "then run the installer again."
        exit 1
    fi
}

# Installs the official Apache Tomcat 9 binary distribution (for systems without a tomcat9 package)
# in the same layout as the Debian package: CATALINA_BASE /var/lib/tomcat9, service tomcat9, user tomcat.
install_apache_tomcat9() {
    has_systemd || die "Installing Apache Tomcat 9 needs systemd, which is not running on this system."
    local mirror="https://dlcdn.apache.org/tomcat/tomcat-9"
    local checksums="https://downloads.apache.org/tomcat/tomcat-9"
    local archive="https://archive.apache.org/dist/tomcat/tomcat-9"
    local version work tarball name expected actual dest

    version=$(curl -fsSL --max-time 30 "$mirror/" 2>/dev/null | grep -oE 'v9\.0\.[0-9]+' | sort -uV | tail -n1)
    version=${version#v}
    if [[ -z "$version" ]]; then
        warn "Cannot look up the latest Tomcat 9 release, using $TOMCAT9_FALLBACK_VERSION."
        version=$TOMCAT9_FALLBACK_VERSION
    fi
    name="apache-tomcat-$version.tar.gz"
    work=$(mktemp -d) || return 1
    tarball="$work/$name"
    if ! run "Downloading Apache Tomcat $version" curl -fsSL --retry 3 -o "$tarball" "$mirror/v$version/bin/$name"; then
        run "Downloading Apache Tomcat $version from the Apache archive" \
            curl -fsSL --retry 3 -o "$tarball" "$archive/v$version/bin/$name" || { rm -rf "$work"; return 1; }
    fi
    expected=$({ curl -fsSL --retry 3 "$checksums/v$version/bin/$name.sha512" \
        || curl -fsSL --retry 3 "$archive/v$version/bin/$name.sha512"; } 2>/dev/null | awk '{print $1; exit}')
    actual=$(sha512sum "$tarball" | awk '{print $1}')
    if [[ -z "$expected" || "$expected" != "$actual" ]]; then
        rm -rf "$work"
        err "The SHA-512 checksum of $name does not match the one published by Apache."
        return 1
    fi
    ok "Checksum verified (SHA-512, published by apache.org)"

    dest="$(dirname "$TOMCAT9_HOME")/apache-tomcat-$version"
    rm -rf "$dest"
    mkdir -p "$(dirname "$TOMCAT9_HOME")"
    if ! tar -xzf "$tarball" --no-same-owner -C "$(dirname "$TOMCAT9_HOME")"; then
        rm -rf "$work"
        err "Cannot extract $name"
        return 1
    fi
    rm -rf "$work"
    ln -sfn "$dest" "$TOMCAT9_HOME"

    # Service account
    getent group tomcat >/dev/null || groupadd --system tomcat || return 1
    getent passwd tomcat >/dev/null \
        || useradd --system --gid tomcat --home-dir "$TOMCAT9_BASE" --no-create-home --shell /usr/sbin/nologin tomcat \
        || return 1

    # CATALINA_BASE: configuration and applications, separate from the Tomcat binaries
    mkdir -p "$TOMCAT9_BASE"/{conf,lib,webapps,logs,work,temp} || return 1
    if [[ ! -f "$TOMCAT9_BASE/conf/server.xml" ]]; then
        cp -r "$dest/conf/." "$TOMCAT9_BASE/conf/" || return 1
    fi
    chown -R root:tomcat "$TOMCAT9_BASE/conf"
    chmod 750 "$TOMCAT9_BASE/conf"
    find "$TOMCAT9_BASE/conf" -type f -exec chmod 640 {} +
    chown tomcat:tomcat "$TOMCAT9_BASE"/{webapps,logs,work,temp}
    chmod 750 "$TOMCAT9_BASE"/{webapps,logs,work,temp}

    cat >/etc/systemd/system/tomcat9.service <<EOF
# Apache Tomcat 9 for ButterMDM, created by buttermdm_install.sh
[Unit]
Description=Apache Tomcat 9 Web Application Server (ButterMDM)
After=network.target postgresql.service

[Service]
Type=simple
User=tomcat
Group=tomcat
Environment=CATALINA_HOME=$TOMCAT9_HOME
Environment=CATALINA_BASE=$TOMCAT9_BASE
Environment=CATALINA_TMPDIR=$TOMCAT9_BASE/temp
Environment="JAVA_OPTS=-Djava.awt.headless=true"
ExecStart=$TOMCAT9_HOME/bin/catalina.sh run
SuccessExitStatus=143
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF
    systemctl daemon-reload
    ok "Apache Tomcat $version installed in $dest (service tomcat9)"

    TOMCAT_HOME=$TOMCAT9_BASE
    detect_tomcat_http_port
    ensure_port_free "$TOMCAT_HTTP_PORT"
    run "Starting Tomcat 9" systemctl enable --now tomcat9 || return 1
}

# ---------------------------------------------------------------------------
# Steps
# ---------------------------------------------------------------------------

ensure_root() {
    if [[ "$EUID" -eq 0 ]]; then
        return 0
    fi
    warn "The installer must run as root: it installs packages, deploys to Tomcat and writes system files."
    if have sudo && confirm "Restart the installer with sudo?" Y; then
        exec sudo -- bash "$SCRIPT_PATH" "$@"
    fi
    die "Please run: sudo $SCRIPT_PATH"
}

step_system() {
    section "System check"

    detect_distro
    case "$DISTRO_SUPPORT" in
        tested)     ok "Operating system: $DISTRO_NAME" ;;
        untested)   warn "Operating system: $DISTRO_NAME (not tested yet, continuing with the $DISTRO_ID defaults)" ;;
        derivative) warn "Operating system: $DISTRO_NAME (based on ${DISTRO_LIKE}, continuing with apt)" ;;
        *)          warn "Operating system: $DISTRO_NAME is not supported by the automatic setup"
                    note "Supported: Debian 10-13, Ubuntu 18.04-24.04 and apt-based derivatives."
                    note "You can still continue if Tomcat, PostgreSQL and aapt are already installed." ;;
    esac

    if is_server_source "$PWD"; then
        ok "Server source: $PWD"
    else
        info "Server source: tag $SERVER_TAG of $SERVER_REPO (downloaded in the build step)"
    fi
    if [[ -n "$SERVER_WAR" ]]; then
        [[ -f "$SERVER_WAR" ]] || die "The web application given with --war does not exist: $SERVER_WAR"
        ok "Web application: $SERVER_WAR (--war, the build is skipped)"
    elif [[ -n "$CONSOLE_DIR" ]]; then
        [[ -f "$CONSOLE_DIR/package.json" ]] || die "$CONSOLE_DIR is not a web console source directory (package.json not found)."
        ok "Web console source: $CONSOLE_DIR"
    else
        info "Web console source: tag $CONSOLE_TAG of $CONSOLE_REPO (downloaded in the build step)"
    fi
}

step_components() {
    section "Required components"

    local -a missing_desc=() packages=()
    local tomcat_found=0 tomcat_plan=""

    if have aapt; then ok "Android Asset Packaging Tool (aapt)"; else
        missing_desc+=("Android Asset Packaging Tool (aapt)"); packages+=(aapt); fi

    if have psql; then ok "PostgreSQL client ($(psql --version | awk '{print $3}'))"; else
        missing_desc+=("PostgreSQL database server and client (postgresql)"); packages+=(postgresql); fi

    detect_jakarta_tomcats
    if detect_installed_tomcat; then
        if [[ -n "$TOMCAT_MAJOR" && "$TOMCAT_MAJOR" -ge 10 ]]; then
            err "Tomcat $TOMCAT_MAJOR in $TOMCAT_HOME cannot run ButterMDM."
            note "ButterMDM is a Java EE (javax.*) application and needs Tomcat 9."
            exit 1
        fi
        tomcat_found=1
        ok "Apache Tomcat ${TOMCAT_VERSION_FULL:-$TOMCAT_MAJOR} ($TOMCAT_HOME, service $TOMCAT_SERVICE, user $TOMCAT_USER)"
    else
        if [[ ${#JAKARTA_TOMCATS[@]} -gt 0 ]]; then
            warn "Found ${JAKARTA_TOMCATS[*]}: Tomcat 10 and later cannot run ButterMDM (it needs the javax.* API of Tomcat 9)."
        fi
        if [[ "$PKG_MANAGER" == "apt" && -n "$(apt_candidate tomcat9)" ]]; then
            tomcat_plan=package
            missing_desc+=("Apache Tomcat 9 (tomcat9)")
            packages+=(tomcat9)
        else
            tomcat_plan=apache
            missing_desc+=("Apache Tomcat 9 from apache.org, as $DISTRO_NAME has no tomcat9 package")
            if ! have java; then missing_desc+=("Java runtime (default-jre-headless)"); packages+=(default-jre-headless); fi
            if ! have curl; then missing_desc+=("curl, to download Tomcat"); packages+=(curl ca-certificates); fi
        fi
    fi

    # Build tools: git for the source download, Maven and a JDK to compile
    local need_git=0
    if [[ -z "$SERVER_WAR" && -z "$CONSOLE_DIR" ]] || ! is_server_source "$PWD"; then need_git=1; fi
    if [[ $need_git -eq 1 ]]; then
        if have git; then ok "Git ($(git --version | awk '{print $3}'))"; else
            missing_desc+=("Git, to download the source code (git)"); packages+=(git ca-certificates); fi
    fi
    if [[ -z "$SERVER_WAR" ]]; then
        if have mvn; then ok "Apache Maven ($(mvn -v 2>/dev/null | awk 'NR == 1 {print $3}'))"; else
            missing_desc+=("Apache Maven, to compile ButterMDM (maven)"); packages+=(maven); fi
        if have javac; then ok "Java compiler ($(javac -version 2>&1 | awk '{print $2}'))"; else
            missing_desc+=("Java development kit, to compile ButterMDM (default-jdk-headless)")
            packages+=(default-jdk-headless)
            # The JDK includes the runtime needed by Apache Tomcat 9
            local i
            for i in "${!packages[@]}"; do
                [[ "${packages[$i]}" == "default-jre-headless" ]] && unset 'packages[i]'
            done
            packages=("${packages[@]}")
            for i in "${!missing_desc[@]}"; do
                [[ "${missing_desc[$i]}" == "Java runtime (default-jre-headless)" ]] && unset 'missing_desc[i]'
            done
            missing_desc=("${missing_desc[@]}")
        fi
    fi

    if [[ ${#missing_desc[@]} -eq 0 ]]; then
        return 0
    fi

    echo
    warn "The following components are missing:"
    local d
    for d in "${missing_desc[@]}"; do note "- $d"; done
    echo

    if [[ ${#packages[@]} -gt 0 && "$PKG_MANAGER" != "apt" ]]; then
        err "Automatic installation of packages is not available on $DISTRO_NAME."
        note "Install them with your package manager, then run the installer again."
        note "For a custom Tomcat 9 location set TOMCAT_HOME, TOMCAT_SERVICE and TOMCAT_USER."
        exit 1
    fi

    if ! confirm "Install all missing components now?" Y; then
        err "Installation aborted: the components above are required."
        note "Install them manually and run the installer again."
        exit 1
    fi

    if [[ ${#packages[@]} -gt 0 ]]; then
        run "Refreshing the package lists" env DEBIAN_FRONTEND=noninteractive apt-get update \
            || die "apt-get update failed, check the network and the APT sources."

        # A fresh system may have had empty package lists: prefer the distribution's tomcat9 when it exists
        if [[ "$tomcat_plan" == "apache" && -n "$(apt_candidate tomcat9)" ]]; then
            tomcat_plan=package
            packages+=(tomcat9)
            info "The tomcat9 package is available on $DISTRO_NAME, using it."
        fi

        local p
        for p in "${packages[@]}"; do
            if [[ -z "$(apt_candidate "$p")" ]]; then
                err "Package $p is not available on $DISTRO_NAME."
                if [[ "$DISTRO_ID" == "ubuntu" && "$p" == "aapt" ]]; then
                    note "aapt is in the Ubuntu 'universe' repository: add-apt-repository universe"
                fi
                exit 1
            fi
        done

        run "Installing ${packages[*]}" env DEBIAN_FRONTEND=noninteractive apt-get install -y "${packages[@]}" \
            || die "Package installation failed, see $LOG_FILE."
    fi

    if [[ "$tomcat_plan" == "apache" ]]; then
        install_apache_tomcat9 || die "Apache Tomcat 9 could not be installed, see $LOG_FILE."
    fi

    if [[ $tomcat_found -eq 0 ]]; then
        TOMCAT_HOME=""; TOMCAT_SERVICE=""; TOMCAT_USER=""
        detect_installed_tomcat || die "Tomcat 9 was installed but $TOMCAT9_BASE/webapps was not found."
        ok "Apache Tomcat ${TOMCAT_VERSION_FULL:-$TOMCAT_MAJOR} ($TOMCAT_HOME, service $TOMCAT_SERVICE, user $TOMCAT_USER)"
    fi
}

# ---------------------------------------------------------------------------
# Source code and build
# ---------------------------------------------------------------------------

# A ButterMDM server source tree (this repository): Maven root, server module and installer data
is_server_source() {
    [[ -f "$1/pom.xml" && -f "$1/server/pom.xml" && -f "$1/install/context_template.xml" ]]
}

# git_fetch_tag <repository> <tag> <destination>
git_fetch_tag() {
    local repo=$1 tag=$2 dest=$3
    # No credential prompt: a missing or private repository fails instead of asking for a user name
    if ! GIT_TERMINAL_PROMPT=0 git ls-remote --exit-code "$repo" HEAD >>"$LOG_FILE" 2>&1; then
        err "Cannot reach the repository $repo"
        note "Check the network connection and that the repository exists and is public."
        return 1
    fi
    if ! GIT_TERMINAL_PROMPT=0 git ls-remote --exit-code --tags "$repo" "refs/tags/$tag" >>"$LOG_FILE" 2>&1; then
        err "The repository $repo has no tag $tag"
        return 1
    fi
    rm -rf "$dest"
    run "Downloading $(basename "$repo" .git) $tag" \
        env GIT_TERMINAL_PROMPT=0 git -c advice.detachedHead=false clone --quiet --depth 1 --branch "$tag" "$repo" "$dest"
}

# copy_tree <source> <destination>: copies a source tree without build output
copy_tree() {
    rm -rf "$2" && mkdir -p "$2" \
        && tar -C "$1" --exclude=./.git --exclude=target --exclude=node_modules --exclude=./dist -cf - . | tar -C "$2" -xf -
}

step_build() {
    section "Build"
    local server_src console_src free_mb

    mkdir -p "$BUILD_DIR" || die "Cannot create the build directory $BUILD_DIR"
    chmod 755 "$BUILD_DIR"

    # Server source
    if is_server_source "$PWD"; then
        if [[ -n "$SERVER_WAR" || "$PWD/" == "$BUILD_DIR/"* ]]; then
            server_src=$PWD                        # nothing to compile, or already in the build directory
        else
            server_src="$BUILD_DIR/buttermdm-server-$SERVER_VERSION"
            run "Copying the server source to $server_src" copy_tree "$PWD" "$server_src" \
                || die "Cannot copy the source code to $server_src"
        fi
    else
        server_src="$BUILD_DIR/buttermdm-server-$SERVER_VERSION"
        git_fetch_tag "$SERVER_REPO" "$SERVER_TAG" "$server_src" || die "The server source code could not be downloaded."
        is_server_source "$server_src" || die "The downloaded tag $SERVER_TAG is not a ButterMDM server source tree."
    fi
    # The rest of the installation uses the installer data and scripts of this source tree
    cd "$server_src" || die "Cannot enter $server_src"
    [[ -f ./install/context_template.xml && -f ./install/log4j_template.xml ]] \
        || die "The installation data is incomplete (install/context_template.xml, install/log4j_template.xml)."

    if [[ -n "$SERVER_WAR" ]]; then
        ok "Using the web application $SERVER_WAR, nothing to compile"
        return 0
    fi

    # Web console source
    if [[ -n "$CONSOLE_DIR" ]]; then
        console_src="$BUILD_DIR/buttermdm-console-local"
        run "Copying the web console source to $console_src" copy_tree "$CONSOLE_DIR" "$console_src" \
            || die "Cannot copy the web console source to $console_src"
    else
        console_src="$BUILD_DIR/buttermdm-console-$CONSOLE_VERSION"
        git_fetch_tag "$CONSOLE_REPO" "$CONSOLE_TAG" "$console_src" || die "The web console source code could not be downloaded."
    fi
    [[ -f "$console_src/package.json" ]] || die "$console_src is not a web console source tree (package.json not found)."

    free_mb=$(df -Pm "$BUILD_DIR" | awk 'NR == 2 {print $4}')
    if [[ -n "$free_mb" && "$free_mb" -lt "$BUILD_MIN_FREE_MB" ]]; then
        warn "Only ${free_mb} MB are free in $BUILD_DIR; the build needs about $BUILD_MIN_FREE_MB MB."
        confirm "Try to build anyway?" N || die "Free some disk space or set BUTTERMDM_BUILD_DIR, then run the installer again."
    fi

    # The build reads its defaults from server/build.properties (not kept in git)
    if [[ ! -f server/build.properties ]]; then
        cp server/build.properties.example server/build.properties || die "Cannot create server/build.properties"
    fi

    note "The first build downloads the Java libraries, Node.js and the npm packages."
    note "It needs Internet access and takes several minutes; later builds are faster."
    note "Build output: $LOG_FILE"
    run "Compiling ButterMDM $SERVER_VERSION with web console $CONSOLE_VERSION" \
        mvn -B install -DskipTests \
            -Dmaven.repo.local="$BUILD_DIR/maven-repository" \
            -Dconsole.dir="$console_src" \
        || die "The build failed, see $LOG_FILE."

    SERVER_WAR="$server_src/server/target/launcher.war"
    [[ -f "$SERVER_WAR" ]] || die "The build finished but $SERVER_WAR was not created, see $LOG_FILE."
    ok "Web application: $SERVER_WAR ($(du -h "$SERVER_WAR" | cut -f1))"
}

# Ubuntu 20.04 ships Tomcat 9.0.31, which is too old for ButterMDM
step_outdated_tomcat() {
    [[ "$TOMCAT_SERVICE" == "tomcat9" && -d /usr/share/tomcat9/bin && "${TOMCAT_VERSION_FULL:-}" == "9.0.31"* ]] || return 0
    local version=9.0.40 work
    warn "Tomcat $TOMCAT_VERSION_FULL is outdated and has to be updated to $version."
    confirm "Update Tomcat now?" Y || return 0
    work=$(mktemp -d)
    (
        set -e
        cd "$work"
        if have curl; then
            curl -fsSLO "https://archive.apache.org/dist/tomcat/tomcat-9/v${version}/bin/apache-tomcat-${version}.tar.gz"
        else
            wget -q "https://archive.apache.org/dist/tomcat/tomcat-9/v${version}/bin/apache-tomcat-${version}.tar.gz"
        fi
        tar -zxf "apache-tomcat-${version}.tar.gz"
        cd "apache-tomcat-${version}"
        chmod a+x bin lib
        chmod -R a+r bin lib
        chmod a+x bin/*.sh
        mv /usr/share/tomcat9/bin /usr/share/tomcat9/bin~
        mv /usr/share/tomcat9/lib /usr/share/tomcat9/lib~
        cp -r bin lib /usr/share/tomcat9
    ) >>"$LOG_FILE" 2>&1
    local rc=$?
    rm -rf "$work"
    [[ $rc -eq 0 ]] || die "Tomcat update failed, see $LOG_FILE."
    run "Restarting Tomcat" service_ctl restart tomcat9
    apt-mark hold tomcat9 >>"$LOG_FILE" 2>&1
    ok "Tomcat updated to $version (package tomcat9 is put on hold)"
}

step_language() {
    section "Language"
    local -a langs=()
    local f
    for f in ./install/sql/buttermdm_init.*.sql; do
        f=${f##*/buttermdm_init.}; langs+=("${f%.sql}")
    done
    while true; do
        ask LANGUAGE "Language of the sample data ($(IFS=/; echo "${langs[*]}")):" "en"
        [[ -f "./install/sql/buttermdm_init.$LANGUAGE.sql" ]] && break
        warn "Unknown language '$LANGUAGE'."
    done
}

# Creates the role and the database as the local postgres superuser (existing ones are kept)
db_admin_create() {
    cd / && runuser -u postgres -- psql -X -q -v ON_ERROR_STOP=1 -p "$SQL_PORT" \
        -v u="$SQL_USER" -v p="$SQL_PASS" -v d="$SQL_BASE" <<'SQL'
SELECT NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'u') AS create_role \gset
\if :create_role
CREATE ROLE :"u" LOGIN PASSWORD :'p';
\endif
SELECT NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'d') AS create_db \gset
\if :create_db
CREATE DATABASE :"d" OWNER :"u";
\endif
SQL
}

db_create_local() {
    local generated=0
    if [[ -z "$SQL_PASS" ]]; then
        SQL_PASS=$(tr -dc 'A-Za-z0-9' </dev/urandom 2>/dev/null | head -c 20)
        generated=1
    fi
    if has_systemd && systemctl cat postgresql.service >/dev/null 2>&1 && ! systemctl is-active --quiet postgresql; then
        run "Starting PostgreSQL" systemctl start postgresql
    fi
    run "Creating database '$SQL_BASE' and user '$SQL_USER'" db_admin_create || return 1
    if [[ $generated -eq 1 ]]; then
        ok "Generated a password for '$SQL_USER': $SQL_PASS"
        note "It is saved in the Tomcat context file, keep it if you need direct database access."
    fi
}

step_database() {
    section "PostgreSQL database"
    note "ButterMDM stores its data in PostgreSQL. A local database and user can be created for you."
    echo
    while true; do
        ask_required SQL_HOST "Host:" "${SQL_HOST:-$DEFAULT_SQL_HOST}"
        ask_required SQL_PORT "Port:" "${SQL_PORT:-$DEFAULT_SQL_PORT}"
        ask_required SQL_BASE "Database name:" "${SQL_BASE:-$DEFAULT_SQL_BASE}"
        ask_required SQL_USER "User:" "${SQL_USER:-$DEFAULT_SQL_USER}"
        ask_secret SQL_PASS "Password (hidden, leave empty to generate one for a new user):"

        if [[ -n "$SQL_PASS" ]] && echo "SELECT 1" | db_psql >/dev/null 2>&1; then
            ok "Connected to $SQL_BASE on $SQL_HOST:$SQL_PORT as $SQL_USER"
            break
        fi

        if [[ -n "$SQL_PASS" ]]; then
            warn "Cannot connect to database '$SQL_BASE' on $SQL_HOST:$SQL_PORT as '$SQL_USER'."
        fi
        if is_local_db_host && getent passwd postgres >/dev/null; then
            if confirm "Create the database '$SQL_BASE' and user '$SQL_USER' on this server?" Y; then
                if db_create_local && echo "SELECT 1" | db_psql >/dev/null 2>&1; then
                    ok "Connected to $SQL_BASE on $SQL_HOST:$SQL_PORT as $SQL_USER"
                    break
                fi
                err "Still cannot connect. If the user '$SQL_USER' already existed, its password was not changed."
            fi
        elif [[ -z "$SQL_PASS" ]]; then
            warn "Please enter the password of the database user."
            continue
        else
            note "Create them on the database server first:"
            note "  CREATE USER $SQL_USER WITH PASSWORD '...';"
            note "  CREATE DATABASE $SQL_BASE WITH OWNER=$SQL_USER;"
        fi
        confirm "Enter the database settings again?" Y || die "Installation aborted."
    done

    local tables
    tables=$(echo "SELECT count(*) FROM pg_tables WHERE schemaname = 'public'" | db_psql -At 2>/dev/null)
    if [[ "${tables:-0}" -gt 0 ]]; then
        echo
        warn "The database '$SQL_BASE' already contains $tables tables (an earlier installation)."
        note "To deploy ButterMDM again the database has to be cleared. ALL DATA WILL BE LOST!"
        local response
        ask response "Type \"erase\" to clear the database, or press Enter to abort:" ""
        [[ "$response" == "erase" ]] || die "Installation aborted, the database was not changed."
        DB_NEEDS_ERASE=1
        info "The database will be cleared when the installation starts."
    fi
}

step_storage() {
    section "File storage"
    if [[ -d "$TOMCAT_HOME/work" ]]; then
        # Debian/Ubuntu Tomcat runs in a systemd sandbox where its work directory is writable
        DEFAULT_LOCATION="$TOMCAT_HOME/work"
    fi
    note "Uploaded files (applications, content), plugin data and logs are stored here."
    note "The directory is created if it does not exist."
    echo
    ask_required LOCATION "Storage directory:" "$DEFAULT_LOCATION"
    LOCATION=${LOCATION%/}
    ask_required SCRIPT_LOCATION "Directory for maintenance scripts (certificates, firewall):" "$DEFAULT_SCRIPT_LOCATION"
    SCRIPT_LOCATION=${SCRIPT_LOCATION%/}
}

step_web() {
    section "Web address"
    note "Devices connect to ButterMDM over the Internet, so the server needs a public domain name or IP."
    echo
    while true; do
        ask PROTOCOL "Protocol (http/https):" "${PROTOCOL:-$DEFAULT_PROTOCOL}"
        [[ "$PROTOCOL" == "http" || "$PROTOCOL" == "https" ]] && break
        warn "Please enter http or https."
    done
    while true; do
        ask_required BASE_DOMAIN "Domain name or public IP (e.g. mdm.example.com):" "${BASE_DOMAIN:-}"
        [[ "$BASE_DOMAIN" =~ ^[A-Za-z0-9.-]+$ ]] && break
        warn "Enter only the host name, without protocol, port or path."
    done
    while true; do
        ask PORT "Port (e.g. $TOMCAT_HTTP_PORT, empty for the default ports 80/443):" "${PORT:-}"
        [[ -z "$PORT" || ( "$PORT" =~ ^[0-9]+$ && "$PORT" -ge 1 && "$PORT" -le 65535 ) ]] && break
        warn "Enter a port number between 1 and 65535, or leave it empty."
    done
    while true; do
        ask_required BASE_PATH "Path on the server (e.g. /buttermdm) or ROOT:" "${BASE_PATH:-$DEFAULT_BASE_PATH}"
        if [[ "$BASE_PATH" == "ROOT" || "$BASE_PATH" == "/" ]]; then
            TOMCAT_DEPLOY_PATH=ROOT
            BASE_PATH=""
            break
        fi
        BASE_PATH="/${BASE_PATH#/}"; BASE_PATH=${BASE_PATH%/}
        if [[ "$BASE_PATH" =~ ^(/[A-Za-z0-9._-]+)+$ ]]; then
            TOMCAT_DEPLOY_PATH=${BASE_PATH#/}
            TOMCAT_DEPLOY_PATH=${TOMCAT_DEPLOY_PATH//\//#}   # Tomcat names nested paths a#b
            break
        fi
        warn "Use letters, digits, '.', '_' and '-' only, e.g. /buttermdm."
    done
    if [[ -n "$PORT" ]]; then BASE_HOST="$BASE_DOMAIN:$PORT"; else BASE_HOST="$BASE_DOMAIN"; fi
    BASE_URL="$PROTOCOL://$BASE_HOST$BASE_PATH"
}

step_smtp() {
    section "E-mail (optional)"
    note "With SMTP settings, administrators can recover lost passwords by e-mail."
    echo
    if confirm "Set up SMTP now?" Y; then
        ask ADMIN_EMAIL "E-mail of the admin account:" ""
        ask SMTP_HOST "SMTP host (e.g. smtp.gmail.com):" ""
        ask SMTP_PORT "SMTP port (25, 465 or 587):" "587"
        if confirm "Use SSL?" N; then SMTP_SSL=1; else SMTP_SSL=0; fi
        if confirm "Use STARTTLS?" Y; then SMTP_STARTTLS=1; else SMTP_STARTTLS=0; fi
        ask SMTP_USERNAME "SMTP user name (empty if no authentication):" ""
        if [[ -n "$SMTP_USERNAME" ]]; then
            ask_secret SMTP_PASSWORD "SMTP password (hidden):"
        fi
        ask SMTP_FROM "Sender e-mail address:" "$ADMIN_EMAIL"
    else
        info "Skipped, SMTP can be configured later in the Tomcat context file."
    fi
}

step_summary() {
    section "Review"
    kv "Operating system" "$DISTRO_NAME"
    kv "Tomcat" "${TOMCAT_VERSION_FULL:-$TOMCAT_MAJOR} ($TOMCAT_HOME)"
    kv "Database" "$SQL_USER@$SQL_HOST:$SQL_PORT/$SQL_BASE"
    kv "Sample data language" "$LANGUAGE"
    kv "Storage directory" "$LOCATION"
    kv "Scripts directory" "$SCRIPT_LOCATION"
    kv "Web panel URL" "$BASE_URL"
    kv "SMTP" "${SMTP_HOST:-not configured}"
    echo
    confirm "Install ButterMDM with these settings?" Y || die "Installation aborted before deployment."
}

# Lets the sandboxed Tomcat service write to the storage directory
configure_service_sandbox() {
    has_systemd || return 0
    local protect dropin_dir dropin real content
    protect=$(systemctl show -p ProtectSystem --value "$TOMCAT_SERVICE" 2>/dev/null)
    [[ -n "$protect" && "$protect" != "no" ]] || return 0
    real=$(readlink -f "$LOCATION")
    dropin_dir="/etc/systemd/system/$TOMCAT_SERVICE.service.d"
    dropin="$dropin_dir/buttermdm.conf"
    content=$(printf '# Created by the ButterMDM installer: storage directory of ButterMDM\n[Service]\nReadWritePaths=%s\n' "$real")
    if [[ -f "$dropin" && "$(cat "$dropin")" == "$content" ]]; then
        return 0
    fi
    if ! mkdir -p "$dropin_dir" || ! printf '%s\n' "$content" >"$dropin"; then
        die "Cannot write $dropin"
    fi
    systemctl daemon-reload
    ok "Allowed $TOMCAT_SERVICE to write to $real ($dropin)"
    TOMCAT_NEEDS_RESTART=1
}

render_context() {
    local -a pairs=(
        _SQL_HOST_ "$SQL_HOST" _SQL_PORT_ "$SQL_PORT" _SQL_BASE_ "$SQL_BASE" _SQL_USER_ "$SQL_USER" _SQL_PASS_ "$SQL_PASS"
        _BASE_DIRECTORY_ "$LOCATION" _PROTOCOL_ "$PROTOCOL" _BASE_HOST_ "$BASE_HOST" _BASE_DOMAIN_ "$BASE_DOMAIN"
        _BASE_PATH_ "$BASE_PATH" _INSTALL_FLAG_ "$INSTALL_FLAG_FILE"
        _SMTP_HOST_ "$SMTP_HOST" _SMTP_PORT_ "$SMTP_PORT" _SMTP_SSL_ "$SMTP_SSL" _SMTP_STARTTLS_ "$SMTP_STARTTLS"
        _SMTP_USERNAME_ "$SMTP_USERNAME" _SMTP_PASSWORD_ "$SMTP_PASSWORD" _SMTP_FROM_ "$SMTP_FROM"
    )
    local -a script=()
    local i
    for ((i = 0; i < ${#pairs[@]}; i += 2)); do
        script+=(-e "s|${pairs[i]}|$(sed_escape "$(xml_escape "${pairs[i+1]}")")|g")
    done
    sed "${script[@]}" ./install/context_template.xml
}

step_deploy() {
    section "Installation"
    TOMCAT_NEEDS_RESTART=0
    INSTALL_FLAG_FILE="$LOCATION/buttermdm_install_flag"
    WORK_DIR=$(mktemp -d /tmp/buttermdm-install.XXXXXX) || die "Cannot create a temporary directory."
    trap 'rm -rf "$WORK_DIR"' EXIT

    if [[ "$DB_NEEDS_ERASE" -eq 1 ]]; then
        run "Clearing the database" db_psql -c "
            DO \$\$ DECLARE r record; BEGIN
                FOR r IN SELECT tablename FROM pg_tables WHERE schemaname = 'public' LOOP
                    EXECUTE 'DROP TABLE IF EXISTS public.' || quote_ident(r.tablename) || ' CASCADE';
                END LOOP;
            END \$\$;" || die "Failed to clear the database, see $LOG_FILE."
    fi

    # Storage directories
    local d
    for d in "$LOCATION" "$LOCATION/files" "$LOCATION/plugins" "$LOCATION/logs" "$LOCATION/emails"; do
        if [[ ! -d "$d" ]]; then
            mkdir -p "$d" || die "Cannot create $d"
        fi
        chown "$TOMCAT_USER:$TOMCAT_USER" "$d" || die "Cannot change the owner of $d to $TOMCAT_USER"
    done
    cp -r ./install/emails/. "$LOCATION/emails/" && chown -R "$TOMCAT_USER:$TOMCAT_USER" "$LOCATION/emails"
    sed -e "s|_BASE_DIRECTORY_|$(sed_escape "$(xml_escape "$LOCATION")")|g" ./install/log4j_template.xml \
        >"$LOCATION/log4j-buttermdm.xml" || die "Cannot write $LOCATION/log4j-buttermdm.xml"
    chown "$TOMCAT_USER:$TOMCAT_USER" "$LOCATION/log4j-buttermdm.xml"
    mkdir -p "$SCRIPT_LOCATION" || die "Cannot create $SCRIPT_LOCATION"
    ok "Storage prepared in $LOCATION"

    configure_service_sandbox

    # Remove the previous deployment
    local webapps="$TOMCAT_HOME/webapps"
    if [[ -e "$webapps/$TOMCAT_DEPLOY_PATH" || -e "$webapps/$TOMCAT_DEPLOY_PATH.war" ]]; then
        rm -rf "${webapps:?}/$TOMCAT_DEPLOY_PATH" "${webapps:?}/$TOMCAT_DEPLOY_PATH.war"
        local i
        for i in $(seq 10 -1 1); do
            [[ -t 1 ]] && printf '\r  %s%s%s Waiting for Tomcat to undeploy the previous version (%ds) ' "$C_CYAN" "${SPINNER[i % ${#SPINNER[@]}]}" "$C_RESET" "$i"
            sleep 1
        done
        [[ -t 1 ]] && printf '\r\e[K'
        ok "Previous version removed"
    fi

    # Tomcat must be running (and see the storage directory) before the new context is added
    if [[ "$TOMCAT_NEEDS_RESTART" -eq 1 ]] || ! service_active "$TOMCAT_SERVICE"; then
        if has_systemd; then
            service_active "$TOMCAT_SERVICE" || ensure_port_free "$TOMCAT_HTTP_PORT"
            systemctl enable "$TOMCAT_SERVICE" >>"$LOG_FILE" 2>&1
        fi
        run "Starting Tomcat ($TOMCAT_SERVICE)" service_ctl restart "$TOMCAT_SERVICE" || die "Tomcat does not start, see $LOG_FILE."
    fi

    # Tomcat context with the ButterMDM settings
    local config_dir="$TOMCAT_HOME/conf/$TOMCAT_ENGINE/$TOMCAT_HOST"
    local config_file="$config_dir/$TOMCAT_DEPLOY_PATH.xml"
    if [[ ! -d "$config_dir" ]]; then
        mkdir -p "$config_dir" || die "Cannot create $config_dir"
        chown "root:$TOMCAT_USER" "$config_dir"
        chmod 755 "$config_dir"
    fi
    render_context >"$config_file" || die "Failed to create the Tomcat context $config_file"
    chown "root:$TOMCAT_USER" "$config_file"
    chmod 640 "$config_file"
    cp -p "$config_file" "$config_file~"
    ok "Tomcat context created: $config_file"

    rm -f "$INSTALL_FLAG_FILE"
    install -m 644 "$SERVER_WAR" "$webapps/$TOMCAT_DEPLOY_PATH.war" || die "Cannot copy the WAR to $webapps"
    chown "$TOMCAT_USER:$TOMCAT_USER" "$webapps/$TOMCAT_DEPLOY_PATH.war"

    local elapsed=0 result=""
    while [[ $elapsed -lt $DEPLOY_TIMEOUT ]]; do
        if [[ -f "$INSTALL_FLAG_FILE" ]]; then
            result=$(<"$INSTALL_FLAG_FILE")
            break
        fi
        if [[ -t 1 ]]; then
            printf '\r  %s%s%s Deploying the web application (%ds) ' "$C_CYAN" "${SPINNER[elapsed % ${#SPINNER[@]}]}" "$C_RESET" "$elapsed"
        fi
        sleep 1
        elapsed=$((elapsed + 1))
    done
    [[ -t 1 ]] && printf '\r\e[K'
    rm -f "$INSTALL_FLAG_FILE"
    if [[ "$result" != "OK" ]]; then
        err "The web application failed to start."
        if [[ -n "$result" ]]; then
            printf '%s\n' "$result" | head -n 20 | sed 's/^/      /' >&2
        else
            note "No response within $DEPLOY_TIMEOUT seconds."
        fi
        note "Tomcat log: $TOMCAT_HOME/logs/catalina.out (or: journalctl -u $TOMCAT_SERVICE)"
        exit 1
    fi
    ok "Web application deployed in ${elapsed}s"

    # Sample data
    local sql_file="$WORK_DIR/buttermdm_init.sql"
    sed -e "s|_BUTTERMDM_BASE_|$(sed_escape "$(sql_escape "$LOCATION")")|g" \
        -e "s|_BUTTERMDM_VERSION_|$(sed_escape "$CLIENT_VERSION")|g" \
        -e "s|_BUTTERMDM_APK_|$(sed_escape "$CLIENT_APK")|g" \
        -e "s|_ADMIN_EMAIL_|$(sed_escape "$(sql_escape "$ADMIN_EMAIL")")|g" \
        "./install/sql/buttermdm_init.$LANGUAGE.sql" >"$sql_file"
    run "Loading the initial data" db_psql -f "$sql_file" \
        || die "Failed to load the initial data, see $LOG_FILE."
}

configure_https_connector() {
    local server_xml="$TOMCAT_HOME/conf/server.xml" keystore="$TOMCAT_HOME/ssl/$BASE_DOMAIN.jks"
    local key_type=RSA certbot_major block tmp
    certbot_major=$(certbot --version 2>&1 | awk '{print $2}' | cut -d. -f1)
    # certbot 2 and later issue ECDSA certificates by default
    if [[ "$certbot_major" =~ ^[0-9]+$ && "$certbot_major" -ge 2 ]]; then key_type=EC; fi

    if [[ ! -f "$keystore" ]]; then
        err "The certificate keystore $keystore was not created, Tomcat was not changed."
        return 1
    fi
    if ! grep -q "BEGIN ButterMDM HTTPS" "$server_xml" \
        && perl -0777 -pe 's/<!--.*?-->//gs' "$server_xml" | grep -q "port=\"$TOMCAT_HTTPS_PORT\""; then
        warn "server.xml already has a connector on port $TOMCAT_HTTPS_PORT, it was not changed."
        note "Point its certificate to $keystore (type $key_type, password $SSL_KEYSTORE_PASSWORD)."
        return 0
    fi

    block="    <!-- BEGIN ButterMDM HTTPS connector (managed by buttermdm_install.sh) -->
    <Connector port=\"$TOMCAT_HTTPS_PORT\" protocol=\"org.apache.coyote.http11.Http11NioProtocol\"
               maxThreads=\"150\" SSLEnabled=\"true\">
        <UpgradeProtocol className=\"org.apache.coyote.http2.Http2Protocol\" />
        <SSLHostConfig>
            <Certificate certificateKeystoreFile=\"$(xml_escape "$keystore")\"
                         certificateKeystorePassword=\"$SSL_KEYSTORE_PASSWORD\" type=\"$key_type\" />
        </SSLHostConfig>
    </Connector>
    <!-- END ButterMDM HTTPS connector -->"

    cp -p "$server_xml" "$server_xml~"
    tmp=$(mktemp)
    sed '/BEGIN ButterMDM HTTPS connector/,/END ButterMDM HTTPS connector/d' "$server_xml~" \
        | BLOCK="$block" awk '!done && /<\/Service>/ { print ENVIRON["BLOCK"]; done = 1 } { print }' >"$tmp"
    cat "$tmp" >"$server_xml"   # keeps owner and permissions of server.xml
    rm -f "$tmp"

    run "Restarting Tomcat with HTTPS on port $TOMCAT_HTTPS_PORT" service_ctl restart "$TOMCAT_SERVICE"
    local i
    for i in $(seq 1 60); do
        if ss -ltn 2>/dev/null | grep -q ":$TOMCAT_HTTPS_PORT\b"; then
            ok "Tomcat listens for HTTPS on port $TOMCAT_HTTPS_PORT"
            return 0
        fi
        sleep 1
    done
    err "Tomcat does not listen on port $TOMCAT_HTTPS_PORT, restoring the previous server.xml."
    cat "$server_xml~" >"$server_xml"
    run "Restarting Tomcat" service_ctl restart "$TOMCAT_SERVICE"
    return 1
}

step_https() {
    section "HTTPS and firewall (optional)"
    local default=N
    [[ "$PROTOCOL" == "https" ]] && default=Y
    note "Let's Encrypt issues a free certificate; port 80 of $BASE_DOMAIN must be reachable from the Internet."
    echo
    local https_done=0
    if confirm "Set up HTTPS with a Let's Encrypt certificate?" "$default"; then
        local -a pkgs=()
        have certbot || pkgs+=(certbot)
        have openssl || pkgs+=(openssl)
        have crontab || pkgs+=(cron)
        if [[ ${#pkgs[@]} -gt 0 ]]; then
            [[ "$PKG_MANAGER" == "apt" ]] || die "Please install ${pkgs[*]} first."
            run "Installing ${pkgs[*]}" env DEBIAN_FRONTEND=noninteractive apt-get install -y "${pkgs[@]}" \
                || die "Package installation failed, see $LOG_FILE."
        fi
        sed -e "s|^DOMAIN=your-domain.com|DOMAIN=$(sed_escape "$BASE_DOMAIN")|" \
            -e "s|^TOMCAT_HOME=.*|TOMCAT_HOME=$(sed_escape "$TOMCAT_HOME")|" \
            ./letsencrypt-ssl.sh >"$WORK_DIR/letsencrypt-ssl.sh" \
            || die "Cannot prepare letsencrypt-ssl.sh"
        # Written through a temporary file: the scripts directory may be this release directory
        install -m 755 "$WORK_DIR/letsencrypt-ssl.sh" "$SCRIPT_LOCATION/letsencrypt-ssl.sh" \
            || die "Cannot write $SCRIPT_LOCATION/letsencrypt-ssl.sh"
        local marker="$WORK_DIR/certificate-request"
        touch "$marker"
        info "Requesting a certificate for $BASE_DOMAIN (certbot may ask for an e-mail address)"
        echo
        "$SCRIPT_LOCATION/letsencrypt-ssl.sh" 2>&1 | tee -a "$LOG_FILE"
        echo
        if [[ "$TOMCAT_HOME/ssl/$BASE_DOMAIN.jks" -nt "$marker" ]]; then
            ok "Certificate keystore: $TOMCAT_HOME/ssl/$BASE_DOMAIN.jks"
            echo
            note "The installer can add the HTTPS connector to $TOMCAT_HOME/conf/server.xml."
            note "A backup is kept as server.xml~ and restored automatically if Tomcat fails to start."
            if confirm "Configure Tomcat for HTTPS automatically?" Y; then
                configure_https_connector && https_done=1
            fi
            if confirm "Renew the certificate automatically every week?" Y; then
                add_cron_line "letsencrypt-ssl.sh" "0 5 * * 1 $SCRIPT_LOCATION/letsencrypt-ssl.sh" \
                    && ok "Weekly renewal added to the root crontab"
            fi
        else
            warn "The certificate was not issued; HTTPS can be set up later with $SCRIPT_LOCATION/letsencrypt-ssl.sh"
        fi
    fi

    # Port 443 -> Tomcat HTTPS port
    if [[ "$PROTOCOL" == "https" && ( -z "$PORT" || "$PORT" == "443" ) ]]; then
        if have iptables && iptables -t nat -S 2>/dev/null | grep -q -- "--to-ports $TOMCAT_HTTPS_PORT"; then
            ok "Port 443 is already redirected to $TOMCAT_HTTPS_PORT"
        elif confirm "Redirect port 443 to Tomcat ($TOMCAT_HTTPS_PORT) with iptables?" Y; then
            local -a pkgs=()
            have iptables || pkgs+=(iptables)
            have crontab || pkgs+=(cron)
            if [[ ${#pkgs[@]} -gt 0 ]]; then
                run "Installing ${pkgs[*]}" env DEBIAN_FRONTEND=noninteractive apt-get install -y "${pkgs[@]}" \
                    || die "Package installation failed, see $LOG_FILE."
            fi
            if [[ ! "./iptables-tomcat.sh" -ef "$SCRIPT_LOCATION/iptables-tomcat.sh" ]]; then
                install -m 755 ./iptables-tomcat.sh "$SCRIPT_LOCATION/iptables-tomcat.sh"
            fi
            chmod 755 "$SCRIPT_LOCATION/iptables-tomcat.sh"
            run "Redirecting port 443 to $TOMCAT_HTTPS_PORT" bash "$SCRIPT_LOCATION/iptables-tomcat.sh" \
                && add_cron_line "iptables-tomcat.sh" "@reboot $SCRIPT_LOCATION/iptables-tomcat.sh" \
                && ok "The redirect is restored at every boot (root crontab)"
        fi
    fi
    HTTPS_DONE=$https_done
}

step_finish() {
    local width=58 line
    line=$(repeat_char "$S_LINE" "$width")
    echo
    printf '  %s%s%s%s%s\n' "$C_GREEN" "$S_TL" "$line" "$S_TR" "$C_RESET"
    printf '  %s%s%s %-*s %s%s%s\n' "$C_GREEN" "$S_V" "$C_RESET$C_BOLD" $((width - 2)) "ButterMDM $SERVER_VERSION is installed!" "$C_RESET$C_GREEN" "$S_V" "$C_RESET"
    printf '  %s%s%s%s%s\n' "$C_GREEN" "$S_BL" "$line" "$S_BR" "$C_RESET"
    echo
    kv "Web panel" "$BASE_URL"
    kv "Direct Tomcat access" "http://$BASE_DOMAIN:$TOMCAT_HTTP_PORT$BASE_PATH"
    if [[ "${HTTPS_DONE:-0}" -eq 1 ]]; then
        kv "Direct HTTPS access" "https://$BASE_DOMAIN:$TOMCAT_HTTPS_PORT$BASE_PATH"
    fi
    kv "Login" "admin / admin"
    kv "Tomcat context" "$TOMCAT_HOME/conf/$TOMCAT_ENGINE/$TOMCAT_HOST/$TOMCAT_DEPLOY_PATH.xml"
    kv "Installer log" "$LOG_FILE"
    echo
    printf '  %sNext steps%s\n' "$C_BOLD" "$C_RESET"
    note "1. Log in and change the administrator password."
    note "2. In the Tomcat context, set a random hash.secret and secure.enrollment=1, then restart $TOMCAT_SERVICE."
    note "3. Replace the sample configuration password 12345678 in every configuration."
    note "4. Build the Butter agent with your server URL and hash.secret (see README.md)."
    echo
}

usage() {
    cat <<USAGE
Usage: $0 [--console DIR] [--war FILE]

Compiles ButterMDM $SERVER_VERSION from source and installs it.

  --console DIR  build with the web console source in DIR instead of
                 downloading tag $CONSOLE_TAG of $CONSOLE_REPO
  --war FILE     skip the build and install this ButterMDM web application
  -h, --help     show this help

When the installer is not inside a ButterMDM server source tree, tag $SERVER_TAG of
$SERVER_REPO is downloaded. The build runs in $BUILD_DIR.
USAGE
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --war|--console)
                     [[ -n "${2:-}" ]] || { usage >&2; exit 2; }
                     set -- "$1=$2" "${@:3}" ;;
            --war=*) SERVER_WAR=$(readlink -f -- "${1#--war=}"); shift ;;
            --console=*) CONSOLE_DIR=$(readlink -f -- "${1#--console=}"); shift ;;
            -h|--help) usage; exit 0 ;;
            *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
        esac
    done
}

main() {
    parse_args "$@"
    SCRIPT_PATH=$(readlink -f "${BASH_SOURCE[0]}")
    cd "$(dirname "$SCRIPT_PATH")" || exit 1
    setup_ui
    banner
    local -a args=()
    [[ -n "$SERVER_WAR" ]] && args+=(--war "$SERVER_WAR")
    [[ -n "$CONSOLE_DIR" ]] && args+=(--console "$CONSOLE_DIR")
    ensure_root "${args[@]}"
    { : >>"$LOG_FILE"; } 2>/dev/null || LOG_FILE=$(mktemp /tmp/buttermdm-install.XXXXXX.log)
    chmod 600 "$LOG_FILE" 2>/dev/null
    echo "=== ButterMDM $SERVER_VERSION installer started $(date '+%F %T')" >>"$LOG_FILE"

    step_system
    step_components
    step_outdated_tomcat
    step_build
    step_language
    step_database
    step_storage
    step_web
    step_smtp
    step_summary
    step_deploy
    step_https
    step_finish
}

# The functions can be sourced for testing without running the installer
if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    main "$@"
fi
