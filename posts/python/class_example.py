class Person:
    """A simple class for describing a person"""
    region = 'KR' # class variable shared by all instances

    def __init__(self, name = ""):
        self.name = name # instance variable unique to each instance
    def show(self):
        print(self.name)

print('region' in dir(Person)) # True
del Person.region
print('region' in dir(Person)) # False


p = Person("DK")
p.show() # DK
p2 = Person("wiz")
f = p2.show
f() # wiz
print(f.__self__ == p2) # True
print(f) # <bound method Person.show of <__main__.Person object at 0x04301F50>>
print(f.__func__) # <function Person.show at 0x043045D0>


Person.height = 175
p.weight = 70
print('weight' in dir(p)) # True
print('weight' in dir(p2))# False
print('height' in dir(p)) # True
print('height' in dir(p2))# True


class Main():
    def __init__(self):
        self.f()
    def f(self):
        print('main')

class Sub(Main):
    def f(self, name = 'anonymous'):
        print('sub', name)

Main().f()
# main
# main
Sub().f('Name')
# sub anonymous
# sub Name


class Main2():
    def __init__(self):
        self.__f()
    def f(self):
        print('main')
    __f = f

class Sub2(Main2):
    def f(self, name = 'anonymous'):
        print('sub', name)

Main2().f()
# main
# main
Sub2().f('Name')
# main
# sub Name