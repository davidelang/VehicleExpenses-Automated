/*
 * ve-refresh-shell — re-exec the caller's shell with a full NSS group list.
 *
 * Problem: Linux freezes supplementary groups at login. If you are added to
 * ai-code / ai-shared / ai-sandbox later (or use a long-lived desktop session),
 * `id` without a username is missing those groups even though `id $USER` shows
 * them. umask tools cannot fix that; only re-initializing credentials can.
 *
 * This binary must be installed setuid root (chmod 4755, owner root). It:
 *   1. Takes the real UID (never trusts argv for identity)
 *   2. initgroups() for that account only
 *   3. Drops back to the real UID/GID
 *   4. exec's SHELL (or passwd shell) in VE_ENV_CWD if set
 *
 * It cannot become another user. newgrp is the wrong tool for multi-group restore.
 *
 * Build/install (once, as root via fix-perms or):
 *   gcc -O2 -Wall -o ve-refresh-shell ve-refresh-shell.c
 *   sudo chown root:root ve-refresh-shell && sudo chmod 4755 ve-refresh-shell
 */

#include <errno.h>
#include <grp.h>
#include <limits.h>
#include <pwd.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

static void exec_user_shell(struct passwd *pw) {
  const char *cwd = getenv("VE_ENV_CWD");
  if (cwd != NULL && cwd[0] != '\0') {
    if (chdir(cwd) != 0) {
      fprintf(stderr, "ve-refresh-shell: chdir %s: %s\n", cwd, strerror(errno));
      /* continue anyway */
    }
  }

  umask(002);

  const char *shell = getenv("SHELL");
  if (shell == NULL || shell[0] == '\0') {
    shell = pw->pw_shell;
  }
  if (shell == NULL || shell[0] == '\0') {
    shell = "/bin/bash";
  }

  /* Interactive shell, not a full login (-l). */
  execl(shell, shell, (char *)NULL);
  fprintf(stderr, "ve-refresh-shell: exec %s: %s\n", shell, strerror(errno));
}

int main(int argc, char **argv) {
  (void)argc;
  (void)argv;

  uid_t ruid = getuid();
  struct passwd *pw = getpwuid(ruid);
  if (pw == NULL) {
    perror("ve-refresh-shell: getpwuid");
    return 1;
  }

  /*
   * Must be installed setuid *root*. A common mis-install is chmod 4755 while
   * still owned by ai-coder/dlang — then euid is that user, initgroups fails,
   * and a bare return closes the terminal that exec'd us. Always recover into
   * an interactive shell so the session is not destroyed.
   */
  if (geteuid() != 0) {
    fprintf(stderr,
            "ve-refresh-shell: not running as root (euid=%d). "
            "Binary must be: sudo chown root:root ve-refresh-shell && sudo chmod 4755 ve-refresh-shell\n",
            (int)geteuid());
    fprintf(stderr, "ve-refresh-shell: starting a normal shell (groups NOT refreshed).\n");
    exec_user_shell(pw);
    return 1;
  }

  /* Full supplementary list from group database for this account only. */
  if (initgroups(pw->pw_name, pw->pw_gid) != 0) {
    perror("ve-refresh-shell: initgroups");
    fprintf(stderr, "ve-refresh-shell: starting a normal shell (groups NOT refreshed).\n");
    /* still root — drop to user before shell */
    if (setgid(pw->pw_gid) == 0) {
      (void)setuid(ruid);
    }
    exec_user_shell(pw);
    return 1;
  }
  if (setgid(pw->pw_gid) != 0) {
    perror("ve-refresh-shell: setgid");
    return 1;
  }
  if (setuid(ruid) != 0) {
    perror("ve-refresh-shell: setuid");
    return 1;
  }

  exec_user_shell(pw);
  return 1;
}
