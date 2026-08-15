/* Tiny loader: dlopen libpaddle_ocr_core.so (which NEEDs paddle) under qemu-user.
 * Linking paddle into the main executable crashes in Bionic constructors under QEMU;
 * delayed dlopen is reliable (same as a successful bare dlopen of light_api).
 */
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>

typedef int (*run_fn)(int, char**);

int main(int argc, char** argv) {
  fprintf(stderr, "loader: start\n");
  void* h = dlopen("libpaddle_ocr_core.so", RTLD_NOW | RTLD_GLOBAL);
  if (!h) {
    fprintf(stderr, "loader: dlopen core failed: %s\n", dlerror());
    return 127;
  }
  run_fn fn = (run_fn)dlsym(h, "paddle_ocr_functional_run");
  if (!fn) {
    fprintf(stderr, "loader: dlsym failed: %s\n", dlerror());
    return 127;
  }
  fprintf(stderr, "loader: invoking core\n");
  return fn(argc, argv);
}
