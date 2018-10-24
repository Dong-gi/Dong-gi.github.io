#include <stdlib.h>
#include <iostream>
#include "Start.h"
#include "Class.h"
#include "SubClass.h"

using std::cout;

int main(int argc, char *argv[])
{
	//basicType();
	//enumType();
	//arrayType();
	//exceptionExample();
	//Class::act();
	SubClass::act();

	std::cout << "\nPress Enter to Exit";
	getchar();
	return EXIT_SUCCESS;
}