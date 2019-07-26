#pragma once
#include <iostream>
#include <string>

using namespace std;

class ClassC
{
private:
	string name = "?";

public:
	ClassC(string name) : name(name) {}

	friend class ClassD;
	
	// 좌변이 해당 클래스 인스턴스가 아니라도, 생성자와 호환된다면 동작한다.
	friend const ClassC operator+(const ClassC& left, const ClassC& right)
	{
		return ClassC(left.name + right.name);
	}

	// 아래는 좌변이 클래스 인스턴스일 때만 동작한다.
	const ClassC operator+(const ClassC& c) const
	{
		return ClassC(name + c.name);
	}

	ClassC& operator+=(const ClassC& c)
	{
		name += c.name;
		return *this;
	}
	
	/*inline*/ string getName() const { return name; }
};