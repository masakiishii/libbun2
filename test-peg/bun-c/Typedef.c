typedef struct A {
    int X;
} *StructA, **StructAPtr;

StructA value;

typedef int (fptr_t)(float, char);

fptr_t func_ptr;
