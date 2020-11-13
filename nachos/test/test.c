#include "stdio.h"
#include "stdlib.h"

int main(int argc, char** argv)
{
    int i, j, num;

    char buf[30];

    printf("Hello world\n");

    printf("Enter a number: ");
    readline(buf, 10);
    num = atoi(buf);
    for(i = 0; i < num; i++) {
        for(j = 0; j < i; j++) {
            printf("*");
        }
        printf("\n");
    }
    printf("\n");


    return 0;
}