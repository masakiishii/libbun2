//void _exit(int) __attribute__((noreturn));
int getpagesize(void) __attribute__((const)) ;
int atexit_b(void (^)(void)) __attribute__((availability(macosx,introduced=10.6)));
