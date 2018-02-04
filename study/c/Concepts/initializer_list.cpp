#include <iostream>
#include <initializer_list>

using namespace std;

int sum(initializer_list<int> nums) {
	auto sum = 0;
	auto iter = nums.begin();
	while (iter != nums.end()) {
		sum += (*iter++);
	}
	return sum;
}

void iinitializer_list(void) {
	cout << sum({ 1, 2, 3, 4 }) << endl;

	printf("\nPress Enter to Exit");
	getchar();
}