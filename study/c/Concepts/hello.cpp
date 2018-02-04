#include <iostream>

using std::cout;

void hello(void)
{
	cout << "Hello World\n";

	int i = 8;
	auto i2 = i;
	decltype(i2) i3 = i2;

	cout << (bool)i << bool(i) << static_cast<bool>(i);

	printf("\nPress Enter to Exit");
	getchar();
}