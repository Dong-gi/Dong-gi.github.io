#pragma once
#include <iostream>
#include <string>
#include <initializer_list>

using namespace std;

class Class
{
private:
	int num = 0;

	void printSelf()
	{
		checkNum();
		cout
			<< "name: " << name
			<< "(" << num << ")"
			<< message << " : ";
	}

	void checkNum()
	{
		static int _num = 1;
		if (num < 1)
			num = _num++;
	}

public:
	string message;
	const string name;

	Class()
	{
		printSelf();
		cout << "Basic constructor called" << endl;
	}

	/// 생성자 초기화 리스트
	/// 클래스에 멤버가 정의된 순서대로 초기화되므로 순서에 유의.
	/// 이것을 사용해야만 하는 경우 : const 멤버, 참조 타입 멤버, 기본 생성자가 없는 멤버
	Class(string message) : Class(message, "Donggi") {}

	Class(string message, string name) : message(message), name(name)
	{
		printSelf();
	}

	Class(initializer_list<string> args)
	{
		for (auto s : args)
			message += s;
		printSelf();
	}

	// 복사생성자
	Class(const Class& obj) : Class(obj.message, obj.name)
	{
		printSelf();
		cout << "Copy constructor called" << endl;
	}

	~Class()
	{
		printSelf();
		cout << "Destructor called" << endl;
	}

	Class& operator=(const Class& rValue)
	{
		printSelf();
		cout << "= operation" << endl;
		if (this != &rValue)
			message = rValue.message;
		return *this;
	}


	void printMessage(); // 구현 파일에서 정의

	static void act() /* const : 정적 메서드에서 불가 */
	{
		Class().printMessage();
		Class("Hello").printMessage();
		
		Class obj("Hello");
		obj.printMessage();

		obj = { "(Yes", "No)" };
		obj.printMessage();

		Class obj2{ "(No", "Yes)" };
		obj2.printMessage();

		Class obj3 = obj; // 복사생성자 
		obj3 = obj;
	}
};