#include <stdio.h>

void not_operator(void)
{
	unsigned int a = 1;
	a = ~a;
	printf("%u", a);

	printf("\nPress Enter to Exit");
	getchar();
}