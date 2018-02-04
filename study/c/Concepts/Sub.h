#pragma once
#include <iostream>
#include <string>
#include "Main2.h"

using namespace std;

class Main2;

class Sub
{
public:
	Sub() = default;
	Sub(Sub& sub) = default;
	Sub& operator=(const Sub& rValue) = default;
	~Sub() = default;

	void func(const Main2& m) {
		m.num = 10;
	}
};