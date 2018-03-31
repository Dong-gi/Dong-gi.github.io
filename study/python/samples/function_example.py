def func1():
    'func1() does nothing.'
    pass # return 없이 함수 종료
help(func1)
print(func1() is None)


a = 'Hello World!'
def func2():
    print(a) # 외부 스코프 접근 가능
func2()


def func3(a, b = None, c = None):
    print(a, b, c)
func3(1)


def func4(a, *, b, c): # 키워드 인자 강제
    print(a, b, c)
func4(1, c = 2, b = 3)


def func5(n, l = []): # 기본 매개변수는 함수가 정의될 때 한번만 계산된다.
    l.append(n)
    print(l)
func5(1)
func5((2, 3))


def func6(*args):
    print(args)
func6()
func6(1, 2, 3)
#func6(a = 1, b = 2) 키워드 인자 전달 불가


def func7(**kwargs):
    print(kwargs)
func7()
#func7(1, 2, 3) 키워드 없는 인자 전달 불가
func7(a = 1, b = 2)


def func8(name):
    def print_hello():
        print("Hello " + name)
    return print_hello
f = func8("World")
f()


f = lambda name : func8(name)()
f("dlroW")


def even_gen(max):
    num = 2
    while num <= max:
        yield num
        num += 2

for n in even_gen(10):
    print(n)


def my_deco(f):
    def print_args(*args, **kwargs):
        print(args, kwargs)
        return f(*args, **kwargs) # *, **을 통해 언패킹이 이루어진다.
    return print_args

@my_deco
def print_name(name):
    print(name)
print_name('Name')


f = my_deco(lambda name : print(name))
f(name = "Name")