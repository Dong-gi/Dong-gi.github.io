#include <iostream>

using namespace std;

void eexception(void)
{
	try {
		throw exception("Hello1");
	}
	catch (const exception& e) {
		cout << e.what();
	}

	try {
		throw new exception("Hello2");
	}
	catch (const exception* e) {
		cout << e->what();
		delete e;
	}

	printf("\nPress Enter to Exit");
	getchar();
}