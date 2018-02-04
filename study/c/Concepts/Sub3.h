#pragma once
#include <iostream>
#include <string>
#include "Main4.h"

using namespace std;

class Sub3 : public Main4
{
private:
	virtual void privateFunc() override {}
	virtual void func() override {
		cout << "Sub func" << endl;
		Main4::func();
	}

public:
	using Main4::Main4;
	Sub3() : Sub3("Sub") { }
	Sub3(string name) : Main4(name) { }
	Sub3(const Sub3& sub) : Main4(sub) {}
	virtual ~Sub3() = default;

	void callFunc() { func(); }
	virtual Sub3& getInstance(string name) const override final {
		return *(new Sub3(name));
	}

	static void doSomething() {
		Sub3().getInstance("World").callFunc();
		cout << typeid(Sub3()).name() << endl;

		printf("\nPress Enter to Exit");
		getchar();
	}
};
