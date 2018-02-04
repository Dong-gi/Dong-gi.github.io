#include <stdio.h>
#include <stdarg.h>

double sum(int size, ...) {
	va_list valist;
	va_start(valist, size);

	double sum = 0.0;
	for (int i = 0; i < size; i++) {
		sum += va_arg(valist, double);
	}

	va_end(valist);
	return sum;
}

void variable_arguments(void) {
	printf("Sum : %.2f\n", sum(4, 1.23, 2.34, 3.45, 4.56));
	
	printf("\nPress Enter to Exit");
	getchar();
}