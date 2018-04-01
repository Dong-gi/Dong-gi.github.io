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
