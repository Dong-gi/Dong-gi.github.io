class Person:
    """A simple class for describing a person"""
    region = 'KR' # class variable shared by all instances

    def __init__(self, name = ""):
        self.name = name # instance variable unique to each instance
    def show(self):
        print(self.name)

print('region' in dir(Person))
del Person.region
print('region' in dir(Person))
'''
True
False
'''

p = Person("DK")
p.show()
p2 = Person("wiz")
f = p2.show
f()
print(f.__self__ == p2)
print(f)
print(f.__func__)
'''
DK
wiz
True
<bound method Person.show of <__main__.Person object at 0x04301F50>>
<function Person.show at 0x043045D0>
'''

Person.height = 175
p.weight = 70
print('weight' in dir(p))
print('weight' in dir(p2))
print('height' in dir(p))
print('height' in dir(p2))
'''
True
False
True
True
'''

class Main():
    def __init__(self):
        self.f()
    def f(self):
        print('main')

class Sub(Main):
    def f(self, name):
        print('sub', name)

try:
    Main().f()
    Sub().f('Name')
except Exception as e:
    print(e)
'''
main
main
f() missing 1 required positional argument: 'name'
'''

class Main2():
    def __init__(self):
        self.__f()
    def f(self):
        print('main')
    __f = f

class Sub2(Main2):
    def f(self, name):
        print('sub', name)

Main2().f()
Sub2().f('Name')
'''
main
main
main
sub Name
'''