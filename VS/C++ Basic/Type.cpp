#include <iostream>
#include <array>

using namespace std;


void basicType()
{
	int i = 8;
	auto i2 = i;
	decltype(i2) i3 = i2;

	cout << (bool)i << bool(i) << static_cast<bool>(i);
}

enum Enum1 { A, B, C };
enum class Enum2 { A, B, C };

void enumType()
{
	if (Enum1::A < 3)
		cout << "True" << endl;

	// if(Enum2::A < 3) // error
	if (Enum2::A < Enum2::C)
		cout << "True" << endl;
}

struct Struct1
{
	int field1;
	string field2;
};

void arrayType()
{
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
}