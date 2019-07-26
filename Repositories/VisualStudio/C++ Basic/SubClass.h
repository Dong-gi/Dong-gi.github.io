#pragma once
#include <iostream>
#include <string>
#include "SuperClass.h"

using namespace std;

class SubClass : public SuperClass
{
private:
	virtual void privateFunc() override {}
	
	virtual void func() override
	{
		cout << "SubClass func" << endl;
		SuperClass::func();
	}

public:
	using SuperClass::SuperClass;

	SubClass() : SubClass("Sub") { }
	
	SubClass(string name) : SuperClass(name) { }
	
	SubClass(const SubClass& sub) : SuperClass(sub) {}
	
	virtual ~SubClass() = default;

	
	void callFunc() { func(); }
	
	virtual SubClass& getInstance(string name) const override final
	{
		return *(new SubClass(name));
	}

	static void act()
	{
		SubClass().getInstance("World").callFunc();
		cout << typeid(SubClass()).name() << endl;
	}
};
