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
	Main(string message) /* const ������, �Ҹ��ڿ��� �Ұ� */ {
		str = message;
	}
	/** ������ �ʱ�ȭ ����Ʈ
	* Ŭ������ ����� ���ǵ� ������� �ʱ�ȭ�ǹǷ� ������ ����.
	* �̰��� ����ؾ߸� �ϴ� ��� : const ���, ���� Ÿ�� ���, �⺻ �����ڰ� ���� ���
	*/
	Main(const char c) : Main("char : ") {
		str.append(1, c);
	}
	Main(initializer_list<string> args) {
		for (auto s : args)
			str = s;
	}
	// ���������
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

	static void doSomething() /* const << ���� �޼��忡���� �Ұ� */ {
		Main().method();

		Main m("World");
		m.method();

		Main('H').method();

		Main m2 = { "Yes", "No" };
		m2.method();

		Main m3{ "No", "Yes" };
		m3.method();

		Main m4 = m3; // ��������� ȣ��

		printf("\nPress Enter to Exit");
		getchar();
	}

	void doSomething2(string str) const {
		cout << str << endl;
	}
};