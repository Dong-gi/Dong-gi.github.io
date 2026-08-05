class LowerMetaclass(type):
    def __new__(cls, clsname, bases, dct):
        lowercase_attr = {}
        for name, val in dct.items():
            if not name.startswith('__'):
                lowercase_attr[name.lower()] = val
            else:
                lowercase_attr[name] = val
        print('meta new'.ljust(15), lowercase_attr)
        return super(LowerMetaclass, cls).__new__(cls, clsname, bases, lowercase_attr)

    def __init__(cls, name, bases, dct):
        print('meta init'.ljust(15), dct)
        super().__init__(name, bases, dct)


class _RememberBefore():
    def __init__(self, cls, size=3):
        print('deco init'.ljust(15), cls, size)
        self.cls, self.size = cls, size
    def __call__(self):
        print('deco call'.ljust(15), self.cls, self.size)
        return self.cls

def RememberBefore(cls=None, size=3):
    print('deco wrapper'.ljust(15), cls, size)
    if cls:
        return _RememberBefore(cls)
    else:
        def wrapper(cls):
            return _RememberBefore(cls, size)
        return wrapper


@RememberBefore(size=10)
class IUser(metaclass=LowerMetaclass):
    uSeR_id = 0
    nAmE = ''

print('object dict'.ljust(15), {k : v for k, v in IUser().__dict__.items() if not k.startswith('__')})
'''
deco wrapper    None 10
meta new        {'__module__': '__main__', '__qualname__': 'IUser', 'user_id': 0, 'name': ''}
meta init       {'__module__': '__main__', '__qualname__': 'IUser', 'uSeR_id': 0, 'nAmE': ''}
deco init       <class '__main__.IUser'> 10
deco call       <class '__main__.IUser'> 10
object dict     {'user_id': 0, 'name': ''}
'''

class SingletonMetaclass(type):
    instance = None
    def __call__(cls, *args, **kwargs):
        if cls.instance is None:
            cls.instance = super(SingletonMetaclass, cls).__call__(*args, **kwargs)
        return cls.instance

class Foo(metaclass=SingletonMetaclass):
    pass

a = Foo()
b = Foo()
print(a is b) # True