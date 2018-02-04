#include <stdio.h>
#include <errno.h>
#include <string.h>

extern int errno;

void error(void)
{
	FILE* fp = NULL;
	fopen_s(&fp, "not_exist_file", "r");

	if (fp == NULL)
	{
		fprintf(stderr, "%d\n", errno);
		perror("");
		char buf[1000];
		strerror_s(buf, 1000, errno);
		fprintf(stderr, "%s", buf);
	}

	printf("\nPress Enter to Exit");
	getchar();
}