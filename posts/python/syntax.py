print("Hello \
World!") # Hello World!

i = 1
if(i < 0):
    print('< 0')
elif(i > 0):
    print('> 0')
else:
    print(0)

i = 1
while i < 10:
    print(i, end=', ')
    i += 2
    if(i%2 == 0):
        print('found an even number')
        break
else: # break 호출 확인
    print('no even number') # 1, 3, 5, 7, 9, no even number

for i in range(1, 5):
    if(i == 11):
        break
    print(i, end=', ')
else: # break 호출 확인
    print("didn't meet 11") # 1, 2, 3, 4, didn't meet 11

def fibo(limit):
    a, b = 0, 1
    while b < limit:
        print(b, end=', ')
        a, b = b, a+b
fibo(100) # 1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89,
print()

def gcd_lcm(a, b):
    if a <= 0 or b <= 0:
        raise ValueError("a, b both most > 0")
    if b > a:
        a, b = b, a
    m = a * b
    while b > 0:
        a, b = b, a % b
    return a, m // a
print(gcd_lcm(30, 50)) # (10, 150)

for i in zip((1, 2, 3), (4, 5, 6, 7)):
    print(i, end=', ') # (1, 4), (2, 5), (3, 6),
print()

# List Comprehension
print('even nums : ' + str([2*n for n in range(1, 10)]))
# select 2*n | where n in range(1, 10)
# result : even nums : [2, 4, 6, 8, 10, 12, 14, 16, 18]

print('even nums : ' + str([n for n in range(1, 20) if n%2 == 0]))
# select n | where n in range(1, 20) and n%2 == 0
# result : even nums : [2, 4, 6, 8, 10, 12, 14, 16, 18]

# Dictionary Comprehension
string = "qoiwnefoqi wenfoiq"
print({key: string.count(key) for key in set(string)}) # {key: value for key, value in KEY_VALUE_SEQUENCE}
# result : {'o': 3, 'q': 3, 'w': 2, 'e': 2, ' ': 1, 'f': 2, 'i': 3, 'n': 2}

# Set Comprehension
print({key for key in string})
# result : {'o', 'q', 'w', 'e', ' ', 'f', 'i', 'n'}

# Generator Comprehension
# 이터레이터에 데이터를 제공하는 한 방법. 데이터를 하나씩 소모하며, 저장하지 않아 재사용할 수 없다.
generator = (key for key in string)
print(generator) # <generator object <genexpr> at 0x04B52BF0>
for k in generator:
    print(k, end=' ') # q o i w n e f o q i   w e n f o i q
print()

# Advanced List Comprehension
print('(x, y) : ' + str([(x, 2*y) for x in range(1, 3) for y in range(1, 4) if x != y]))
# select (x, 2*y) | where x in range(1, 3) | where y in range(1, 4) and x != y
# result : (x, y) : [(1, 4), (1, 6), (2, 2), (2, 6)]

print('(x, y) : ' + str([[(x, 2*y) for x in range(1, 3)] for y in range(1, 4)]))
# select [select (x, 2*y) | where x in range(1, 3)] | where y in range(1, 4)
# (x, y) : [[(1, 2), (2, 2)], [(1, 4), (2, 4)], [(1, 6), (2, 6)]]

print('flatten a list : ' + str([n for l in [[1, 2], [3, 4, 5]] for n in l]))
# select n | where l in [[1, 2], [3, 4, 5]] | where n in l
# result : flatten a list : [1, 2, 3, 4, 5]

def func1():
    'func1() does nothing.'
    pass # return 없이 함수 종료
help(func1)
#Help on function func1 in module __main__:
#
#func1()
#    func1() does nothing.
#
print(func1() is None) # True

a = 'Hello World!'
def func2():
    print(a) # 외부 스코프 접근 가능
func2() # Hello World!

def func3(a, b = None, c : str = None):
    print(a, b, c)
func3(1) # 1 None None

def func4(a, *, b, c): # 키워드 강제
    print(a, b, c)
func4(1, c = 2, b = 3) # 1 3 2

def func5(n, l = []): # 기본 매개변수는 함수가 정의될 때 한번만 계산된다.
    l.append(n)
    print(l)
func5(1) # [1]
func5((2, 3)) # [1, (2, 3)]

def func6(*args):
    print(args)
func6() # ()
func6(1, 2, 3) # (1, 2, 3)
#func6(a = 1, b = 2) 키워드 인자 전달 불가

def func7(**kwargs):
    print(kwargs)
func7() # {}
#func7(1, 2, 3) 키워드 없는 인자 전달 불가
func7(a = 1, b = 2) # {'a': 1, 'b': 2}

def func8(name):
    def print_hello():
        print("Hello " + name)
    return print_hello
f = func8("World")
f() # Hello World

f = lambda name : func8(name)()
f("dlroW") # Hello dlroW

def even_gen(max):
    num = 2
    while num <= max:
        yield num
        num += 2

for n in even_gen(10):
    print(n, end = ', ') # 2, 4, 6, 8, 10, 
print()

def my_deco(f):
    def print_args(*args, **kwargs):
        print(args, kwargs)
        return f(*args, **kwargs) # * : 인자 리스트 언패킹, ** : 키워드 인자 딕셔너리 언패킹
    return print_args

@my_deco
def print_name(name):
    print(name)
print_name('Name')
# ('Name',) {}
# Name

f = my_deco(lambda name : print(name))
f(name = "Name")
# () {'name': 'Name'}
# Name

def bar(cls):
    for name, value in cls.__dict__.items():
        if not name.startswith('__'):
            print(f'Class : {cls.__name__}, Attr name : {name}, Attr value : {value}')
    return cls

@bar
class Foo:
    foo = 'foo'
    bar = 'bar'
'''
Class : Foo, Attr name : foo, Attr value : foo
Class : Foo, Attr name : bar, Attr value : bar
'''