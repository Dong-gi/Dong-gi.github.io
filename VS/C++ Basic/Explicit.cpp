#include <iostream>

using namespace std;

class INT32
{
private:
	int val;

public:
	INT32() : INT32(0) {}
	INT32(int val) : val(val) {}
	explicit operator int() const { return val; }
};

void explicitExample()
{
	cout << 123 + (int)INT32(123) << endl;
}