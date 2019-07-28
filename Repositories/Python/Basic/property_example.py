class ReadOnly:
    def __init__(self, value):
        self._value = value

    @property
    def value(self):
        return self._value

readOnly = ReadOnly(10)
print(readOnly.value) # 10

try:
    readOnly.value = 20
except AttributeError as e:
    print(e) # can't set attribute

print(readOnly.value) # 10


class C:
    def __init__(self):
        self._x = None

    @property
    def x(self):
        """I'm the 'x' property."""
        return self._x

    @x.setter
    def x(self, value):
        self._x = value

    @x.deleter
    def x(self):
        del self._x