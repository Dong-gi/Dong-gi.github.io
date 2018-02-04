#pragma once
#include <iostream>
#include <string>
#include "Sub.h"

using namespace std;

class Sub2; // Ŭ���� ������ ����

class Main3 {
private:
	string name = "Main";

public:
	Main3(string name) : name(name) {}

	friend class Sub2;
	// �º��� ���� Main �ν��Ͻ��� �ƴ϶� Main �����ڿ� ȣȯ�ȴٸ� �����Ѵ�.
	friend const Main3 operator+(const Main3& left, const Main3& right) {
		return Main(left.name + right.name);
	}

	// �Ʒ��� �º��� Main �ν��Ͻ��� ���� �����Ѵ�.
	const Main3 operator+(const Main3& m) const {
		return Main3(name + m.name);
	}
	Main3& operator+=(const Main3& m) {
		name += m.name;
		return *this;
	}
	/*inline*/ string getName() const { return name; }
};