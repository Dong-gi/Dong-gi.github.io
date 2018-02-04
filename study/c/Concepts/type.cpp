#include <iostream>
#include <array>

using namespace std;

enum Enum1 { A, B, C };
enum class Enum2 { A, B, C };

struct Struct1 {
	int field1;
	string field2;
};

void type(void)
{
	if (Enum1::A < 3)
		cout << "True" << endl;

	// if(Enum2::A < 3) // error
	if (Enum2::A < Enum2::C)
		cout << "True" << endl;

	int arr[10] = { 2 };
	for (auto i : arr) {
		cout << i << " ";
		i += 2;
	}
	cout << endl;

	for (auto& i : arr) {
		cout << i << " ";
		i += 2;
	}
	cout << endl;

	for (auto i : arr)
		cout << i << " ";
	cout << endl;

	array<int, 10> arr2 = { 2 };
	for (auto i : arr2)
		cout << i << " ";

	printf("\nPress Enter to Exit");
	getchar();
}