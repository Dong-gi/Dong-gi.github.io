#include <stdio.h>

int* makeArray()
{
	int arr[] = { 4, 5, 6 };
	return arr; // X : ���� �Ҵ�� ���������� �Լ� ���� �� ���ÿ��� ����.
}

void array()
{
	int arr[] = { 1, 2, 3 };
	int *p = arr;
	for (int i = 0; i < 3; ++i)
	{
		printf("%d\n", *(p + i));
	}

	p = makeArray();
	for (int i = 0; i < 3; ++i)
	{
		printf("%d\n", p[i]);
	}
}