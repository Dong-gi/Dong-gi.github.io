#pragma once
#include <iostream>
#include <string>

using namespace std;

class SuperClass
{
private:
	string name = "SuperClass";

	virtual void privateFunc() {}

public:
	SuperClass() = default;
	
	SuperClass(string name) : name(name) {}
	
	virtual ~SuperClass() = default;


	virtual void func()
	{
		cout << "SuperClass func" << endl;
		cout << "My name : " << name << endl;
	}
	
	virtual SuperClass& getInstance(string name) const
	{
		return *(new SuperClass(name));
	}
};