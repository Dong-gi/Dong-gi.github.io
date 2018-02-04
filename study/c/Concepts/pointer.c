#include <stdio.h>

void pointer(void)
{
	int *arr[3];
	int a = 1, b = 2, c = 3;
	arr[0] = &a, arr[1] = &b, arr[2] = &c;
	for (int i = 0; i < 3; )
	{
		printf("%d ", **(arr + i++));
	}
	printf("\n");

	int(*arr2)[];
	int arr3[] = { 4, 5, 6 };
	arr2 = &arr3;
	for (int i = 0; i < 3; ++i)
	{
		printf("%d ", (*arr2)[i]);
	}

	printf("\nPress Enter to Exit");
	getchar();
}