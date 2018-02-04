#pragma once
#include <iostream>
#include <string>
#include "Main3.h"

using namespace std;

class Main3;

class Sub2
{
public:
	void func(const Main3& m) {
		cout << m.name << endl;
	}
};