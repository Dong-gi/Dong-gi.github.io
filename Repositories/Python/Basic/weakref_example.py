import weakref

class Clazz:
    def __init__(self):
        self.value = 'hello'

    def say(self):
        print(self.value)

obj = Clazz()
wref = weakref.ref(obj)

print(obj == wref.__call__())   # True
wref().say()                    # hello