#pragma once
#include <iostream>
#include <string>
#include "Sub.h"

using namespace std;

class Sub; // 클래스 포워드 선언

class Main2 {
private:
	string name = "Main";
	Sub& sub;

public:
	mutable int num;

	Main2() = delete;
	Main2(string& name, Sub& sub) : name(name), sub(sub) {}
	Main2(Main2& main) : name(main.name), sub(main.sub) {}
	void print() {
		cout << name;
	}
};