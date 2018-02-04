#pragma once
#include <iostream>
#include <string>
#include <initializer_list>

using namespace std;

class Main {
public:
	string str = "Hello";

	Main() {
		cout << "Constructor Called" << endl;
	}
	Main(string message) /* const 생성자, 소멸자에선 불가 */ {
		str = message;
	}
	/** 생성자 초기화 리스트
	* 클래스에 멤버가 정의된 순서대로 초기화되므로 순서에 유의.
	* 이것을 사용해야만 하는 경우 : const 멤버, 참조 타입 멤버, 기본 생성자가 없는 멤버
	*/
	Main(const char c) : Main("char : ") {
		str.append(1, c);
	}
	Main(initializer_list<string> args) {
		for (auto s : args)
			str = s;
	}
	// 복사생성자
	Main(const Main& main) : str(main.str) {}
	~Main() {
		cout << "Distructor Called" << endl;
	}

	Main& operator=(const Main& rValue) {
		if (this != &rValue) {
			str = rValue.str;
		}
		return *this;
	}


	void method();

	static void doSomething() /* const << 정적 메서드에서도 불가 */ {
		Main().method();

		Main m("World");
		m.method();

		Main('H').method();

		Main m2 = { "Yes", "No" };
		m2.method();

		Main m3{ "No", "Yes" };
		m3.method();

		Main m4 = m3; // 복사생성자 호출

		printf("\nPress Enter to Exit");
		getchar();
	}

	void doSomething2(string str) const {
		cout << str << endl;
	}
};