import functools

def deco1(f):
    def x(*args, **kwargs):
        return f(*args, **kwargs)
    return x

@deco1
def test1(x):
    '''test1 docstring'''
    return x

print(test1)         # <function deco1.<locals>.x at 0x7fd36689b940>
print(test1(100))    # 100
print(test1.__doc__) # None


def deco2(f):
    @functools.wraps(f)
    def x(*args, **kwargs):
        return f(*args, **kwargs)
    return x

@deco2
def test2(x):
    '''test2 docstring'''
    return x

print(test2)         # <function test2 at 0x7fd36689ba60>
print(test2(100))    # 100
print(test2.__doc__) # test2 docstring