# extractmail — VE pin

- Remote: git@github.com:davidelang/extractmail.git
- Worktree: third_party/extractmail/src (email-connection)
- Pin: see libpin.toml (M2.5 YAML dispatch + goldens export @ 9c22953)
- Goldens: python3 src/python/run_goldens.py
- CLI: src/scripts/extractmail
- Host: /home/dlang/git/extractmail
- Shared remotetable: host may symlink third_party/remotetable → VE pin tree
- Build: ./third_party/extractmail/build (optional goldens + AAR)
