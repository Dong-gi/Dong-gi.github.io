Test = type('Test', (), dict())
t = Test()
t.a = 1
t.b = 'hello'
print("{t.a}, {t.b}".format(t=t))   # 1, hello
print("{0}, {1}".format(t.a, t.b))  # 1, hello
print("{}, {}".format(t.a, t.b))    # 1, hello

