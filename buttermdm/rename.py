#!/usr/bin/env python3
"""ButterMDM: renames legacy upstream identifiers to ButterMDM's names.

The same rules apply to the server and the agent, so the names they share stay equal (for example the agent's
device admin component in the server's sample data and enrollment scripts).

The rules are idempotent: renaming a renamed tree changes nothing. This file itself is not renamed.

Usage, from the root of the server or agent repository:
    python3 buttermdm/rename.py tree TREE-ISH     write the renamed tree into the object database, print its ID
    python3 buttermdm/rename.py check             list the old names left in the tracked files (exit 1 if any)
"""
import base64, os, re, subprocess, sys


def _b(s):
    return base64.b64decode(s).decode("utf-8")


SELF = "buttermdm/rename.py"

RULES = [
    (re.compile(_b("Y29tKFsuL10paG1kbVwx" + "bGF1bmNoZXJcYg==")), r"com\1buttermdm\1agent"),
    (re.compile(_b("Y29tKFsuL10paG1kbVxi")), r"com\1buttermdm"),
    (re.compile(_b("cnVcLmhlYWR3aW5kXC5raW9zaw==")), "com.buttermdm.kiosk"),
    (re.compile(_b("SGVhZHdpbmRNRE0=")), "ButterMDM"),
    (re.compile(_b("aGVhZHdpbmQta2lvc2s=")), "buttermdm-console"),
    (re.compile(_b("SE1ETShAPVtBLVpdW2Etel0p")), "ButterMDM"),
    (re.compile(_b("SG1kbQ==")), "ButterMDM"),
    (re.compile(_b("SE1ETQ==")), "BUTTERMDM"),
    (re.compile(_b("aG1kbQ==")), "buttermdm"),
    (re.compile(r"'h0001'"), "'b0001'"),
]

# Anything left of the old names (the check)
OLD = re.compile(_b("aG1kbXxoZWFkd2luZHxoLW1kbXxoLXNtc3xlbGxlbnN6fCdoMDAwMSc="), re.I)


def rename_text(text):
    for pattern, new in RULES:
        text = pattern.sub(new, text)
    return text


def rename_path(path):
    return path if path == SELF else rename_text(path)


def rename_content(path, data):
    """New content of a file, or None if it doesn't change. Binary files (with a NUL byte) aren't changed."""
    if path == SELF or b"\0" in data:
        return None
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError:
        return None
    new = rename_text(text)
    return None if new == text else new.encode("utf-8")


class Git:
    def __init__(self):
        self.cat = subprocess.Popen(["git", "cat-file", "--batch"], stdin=subprocess.PIPE, stdout=subprocess.PIPE)
        self.cache = {}

    def read(self, sha):
        self.cat.stdin.write(sha.encode() + b"\n")
        self.cat.stdin.flush()
        size = int(self.cat.stdout.readline().split()[2])
        data = self.cat.stdout.read(size)
        self.cat.stdout.read(1)
        return data

    def renamed_blob(self, path, sha):
        key = (path == SELF, sha)
        if key not in self.cache:
            new = rename_content(path, self.read(sha))
            if new is None:
                self.cache[key] = sha
            else:
                self.cache[key] = subprocess.run(["git", "hash-object", "-w", "--stdin"], input=new, check=True,
                                                 capture_output=True).stdout.decode().strip()
        return self.cache[key]


def rename_tree(treeish, git=None):
    """ID of the renamed tree of TREE-ISH (written into the object database)."""
    git = git or Git()
    out = subprocess.run(["git", "ls-tree", "-r", "-z", "--full-tree", treeish], check=True,
                         capture_output=True).stdout.decode("utf-8")
    entries, paths = [], set()
    for line in filter(None, out.split("\0")):
        info, path = line.split("\t", 1)
        mode, kind, sha = info.split()
        new_path = rename_path(path)
        if kind == "blob" and mode != "120000":
            sha = git.renamed_blob(path, sha)
        if new_path in paths:
            sys.exit(f"Renaming {path} gives an existing path: {new_path}")
        paths.add(new_path)
        entries.append(f"{mode} {kind} {sha}\t{new_path}")
    index = subprocess.run(["git", "rev-parse", "--git-path", "rename-index"], check=True,
                           capture_output=True, text=True).stdout.strip()
    env = dict(os.environ, GIT_INDEX_FILE=index)
    subprocess.run(["git", "read-tree", "--empty"], check=True, env=env)
    subprocess.run(["git", "update-index", "-z", "--index-info"], input="\0".join(entries).encode() + b"\0",
                   check=True, env=env)
    tree = subprocess.run(["git", "write-tree"], check=True, capture_output=True, text=True, env=env).stdout.strip()
    os.remove(index)
    return tree


def check():
    files = subprocess.run(["git", "ls-files", "-z"], check=True, capture_output=True).stdout.decode().split("\0")
    found = [f"path: {f}" for f in files if f and f != SELF and OLD.search(f)]
    grep = subprocess.run(["git", "grep", "-I", "-n", "-i", "-E", OLD.pattern, "--", ".", ":!" + SELF],
                          capture_output=True, text=True).stdout
    for line in grep.splitlines():
        found.append(line)
    print("\n".join(found) if found else "no old names")
    return 1 if found else 0


if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "tree":
        print(rename_tree(sys.argv[2]))
    elif len(sys.argv) == 2 and sys.argv[1] == "check":
        sys.exit(check())
    else:
        sys.exit(__doc__)
