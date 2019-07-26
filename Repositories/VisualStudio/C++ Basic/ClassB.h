#pragma once
#include <iostream>
#include <string>
#include "ClassA.h"

using namespace std;

class ClassA;

class ClassB
{
public:
	ClassB() = default;

	ClassB(ClassB& b) = default;

	ClassB& operator=(const ClassB& rValue) = default;

	~ClassB() = default;


	void func(const ClassA& a)
	{
		a.num = 10;
	}
};