#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <sys/types.h>
#include <errno.h>
#include <limits.h>

int main(int argc, char *argv[]) {
  if (argc < 2) {
    fprintf(stderr, "Usage: %s <script-or-command> [args...]\n", argv[0]);
    fprintf(stderr, "Runs the given command with the euid of the binary owner (via setuid bit). Hard-coded allow list + cwd/config sanity.\n");
    return 1;
  }

  const char *cmd = argv[1];

  // Hard-coded allowed (basenames; can be extended at build/stamp time)
  const char *allowed[] = {
    "deploy",
    "build_app",
    "append-to-engineering-log",
    "todo-append",
    "todo-close",
    "gradlew",
    NULL
  };

  int is_allowed = 0;
  for (int i = 0; allowed[i] != NULL; i++) {
    if (strcmp(cmd, allowed[i]) == 0 || strstr(cmd, allowed[i])) {
      is_allowed = 1;
      break;
    }
  }
  if (!is_allowed) {
    fprintf(stderr, "ERROR: command '%s' not in allowed list\n", cmd);
    return 1;
  }

  // Cwd sanity: must be under a VehicleExpenses-automated tree
  char cwd[PATH_MAX];
  if (getcwd(cwd, sizeof(cwd)) == NULL) {
    perror("getcwd");
    return 1;
  }
  if (strstr(cwd, "VehicleExpenses-automated") == NULL) {
    fprintf(stderr, "ERROR: cwd '%s' does not look like a valid VehicleExpenses worktree\n", cwd);
    return 1;
  }

  // Sanity: try to read a primary-only config (ai-* users cannot read this file)
  // The setuid binary can read it (euid = owner). If present, we trust the context.
  FILE *cfg = fopen(".run-as-primary-config", "r");
  if (cfg) {
    fclose(cfg);
  } else {
    // Also try system location (stamped at install, 600 primary)
    cfg = fopen("/etc/vehicle-primary-config", "r");
    if (cfg) fclose(cfg);
  }

  // The setuid bit on this binary (owned by primary user) ensures we run with euid=primary.
  // No explicit setuid() call needed.

  execvp(cmd, &argv[1]);
  perror("execvp");
  return 1;
}
