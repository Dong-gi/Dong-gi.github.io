#include <iostream>

using namespace std;

void exceptionExample()
{
	try
	{
		throw exception("Hello1");
	}
	catch (const exception& e)
	{
		cout << e.what();
	}

	try
	{
		throw new exception("Hello2");
	}
	catch (const exception* e)
	{
		cout << e->what();
		delete e;
	}
}