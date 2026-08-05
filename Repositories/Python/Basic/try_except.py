try:
    print(1 / 0)
except:
    print("Exception")
# Exception

try:
    print(1 / 0)
except Exception as e:
    print(e)
# division by zero

class MyException(Exception):
    pass

try:
    raise MyException("Don't Worry")
except MyException as e:
    print(e)
# Don't Worry