#pragma once
#include <iostream>
#include <string>
#include "ClassB.h"

using namespace std;

class ClassB; // 클래스 포워드 선언

class ClassA
{
private:
	string name = "A";
	ClassB& b;

public:
	mutable int num;

	ClassA() = delete;
	ClassA(string& name, ClassB& b) : name(name), b(b) {}
	ClassA(ClassA& main) : name(main.name), b(main.b) {}

	void print()
	{
		cout << name;
	}
};