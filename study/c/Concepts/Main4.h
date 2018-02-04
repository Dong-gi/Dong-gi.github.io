#pragma once
#include <iostream>
#include <string>

using namespace std;

class Main4 {
private:
	string name = "Main";

	virtual void privateFunc() {}

public:
	Main4() = default;
	Main4(string name) : name(name) {}
	virtual ~Main4() = default;

	virtual void func() {
		cout << "Main func" << endl;
		cout << "My name : " << name << endl;
	}
	virtual Main4& getInstance(string name) const {
		return *(new Main4(name));
	}
};