#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <sys/types.h>
#include <pwd.h>
#include <errno.h>
#include <limits.h>

int main(int argc, char *argv[]) {
  if (argc < 2) {
    fprintf(stderr, "Usage: %s <script-or-command> [args...]\n", argv[0]);
    fprintf(stderr, "Runs the given command as dlang (setuid). Hard-coded allow list and cwd sanity.\n");
    return 1;
  }

  const char *cmd = argv[1];

  // Hard-coded allowed commands/scripts (basenames or paths; expand as needed)
  const char *allowed[] = {
    "deploy",
    "build_app",
    "append-to-engineering-log",
    "todo-append",
    "todo-close",
    "gradlew",  // though we discourage direct
    "/bin/bash", // for rare cases?
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
    fprintf(stderr, "ERROR: command '%s' not in allowed list for run-as-dlang\n", cmd);
    return 1;
  }

  // Sanity check cwd: must be under the VehicleExpenses tree
  char cwd[PATH_MAX];
  if (getcwd(cwd, sizeof(cwd)) == NULL) {
    perror("getcwd");
    return 1;
  }
  if (strstr(cwd, "VehicleExpenses-automated") == NULL) {
    fprintf(stderr, "ERROR: cwd '%s' does not look like a valid VehicleExpenses worktree\n", cwd);
    return 1;
  }

  // Optional: try to read a dlang-only config (ai-* can't read or write this file)
  // If the file exists and we can read, ok. This is to verify we have dlang context.
  FILE *cfg = fopen("/home/dlang/git/VehicleExpenses-automated/.run-as-dlang-config", "r");
  if (cfg) {
    fclose(cfg);
  } else {
    // Not fatal; allow if not present. In practice, create a 600 file owned dlang.
  }

  // Get dlang uid
  struct passwd *pw = getpwnam("dlang");
  if (!pw) {
    perror("getpwnam dlang");
    return 1;
  }

  // Switch to dlang (setuid binary will have started with euid=root or effective, but we force)
  if (setuid(pw->pw_uid) != 0) {
    perror("setuid");
    return 1;
  }

  // Exec the rest as dlang
  execvp(cmd, &argv[1]);
  perror("execvp");
  return 1;
}
