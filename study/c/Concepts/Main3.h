#pragma once
#include <iostream>
#include <string>
#include "Sub.h"

using namespace std;

class Sub2; // 클래스 포워드 선언

class Main3 {
private:
	string name = "Main";

public:
	Main3(string name) : name(name) {}

	friend class Sub2;
	// 좌변이 굳이 Main 인스턴스가 아니라도 Main 생성자와 호환된다면 동작한다.
	friend const Main3 operator+(const Main3& left, const Main3& right) {
		return Main(left.name + right.name);
	}

	// 아래는 좌변이 Main 인스턴스일 때만 동작한다.
	const Main3 operator+(const Main3& m) const {
		return Main3(name + m.name);
	}
	Main3& operator+=(const Main3& m) {
		name += m.name;
		return *this;
	}
	/*inline*/ string getName() const { return name; }
};