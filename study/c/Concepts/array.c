#include <stdio.h>

int* func()
{
	int arr[] = { 4, 5, 6 };
	return arr;
}

void array(void)
{
	int arr[] = { 1, 2, 3 };
	int *p = arr;
	for (int i = 0; i < 3; ++i)
	{
		printf("%d\n", *(p + i));
	}

	p = func();
	for (int i = 0; i < 3; ++i)
	{
		printf("%d\n", p[i]);
	}

	printf("\nPress Enter to Exit");
	getchar();
}