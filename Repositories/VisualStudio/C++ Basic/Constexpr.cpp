
const int getSize1() { return 10; }
constexpr int getSize2() { return 10; }

void constexprExample()
{
	// int arr[getSize1()]; // error
	int arr[getSize2()];
}