a = 1

def func():
    b = 2
    global a
    a += 4
    print(locals())
    print(globals())

func()