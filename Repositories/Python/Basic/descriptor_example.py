class NonNegativeInteger:
    def __get__(self, instance, owner):
        # 기본 동작 그대로 이용한다면 굳이 __get__()을 구현하지 않아도 된다.
        return instance.__dict__[self.name]
    
    def __set__(self, instance, value):
        if isinstance(value, int) and value >= 0:
            instance.__dict__[self.name] = value
        else:
            raise ValueError(f"value must be non-negative integer but '{value}'")
    
    def __set_name__(self, owner, name):
        self.name = name

class IUserItem:
    userId = NonNegativeInteger()
    itemId = NonNegativeInteger()
    num = NonNegativeInteger()

    def __init__(self, userId, itemId, num):
        self.userId, self.itemId, self.num = userId, itemId, num
    
    def __str__(self):
        return f"{IUserItem.__name__} : {str(self.__dict__)}"

print(IUserItem(1, 2, 3)) # IUserItem : {'userId': 1, 'itemId': 2, 'num': 3}
try:
    print(IUserItem(1, 2, -1))
except ValueError as e:
    print(e) # ValueError: value must be non-negative integer but '-1'