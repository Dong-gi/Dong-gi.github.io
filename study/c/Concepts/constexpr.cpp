#include <iostream>

using namespace std;

const int getSize1() { return 10; }
constexpr int getSize2() { return 10; }

void cconstexpr(void) {
	// int arr[getSize1()]; // error
	int arr[getSize2()];

	printf("\nPress Enter to Exit");
	getchar();
}