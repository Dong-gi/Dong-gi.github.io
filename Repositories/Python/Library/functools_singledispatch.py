import functools

@functools.singledispatch
def test(x):
    print('default call')
@test.register
def _(x: int):
    print('int arg call')
@test.register
def _(x: list):
    print('list arg call')
@test.register(float)
@test.register(complex)
def _(x):
    print('float or complex call')
test.register(type(None), lambda x: print('None arg call'))

test('')    # default call
test(0)     # int arg call
test(0.)    # float or complex call
test(0j)    # float or complex call
test([])    # list arg call
test(None)  # None arg call