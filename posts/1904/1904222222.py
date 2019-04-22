try:
    print(1 / 0)
except:
    print("Exception")

try:
    print(1 / 0)
except Exception as e:
    print(e)

class MyException(Exception):
    pass

try:
    raise MyException("Don't Worry")
except MyException as e:
    print(e)
