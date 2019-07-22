class Parrot:
    def __init__(self):
        self._voltage = 100000

    @property
    def voltage(self):
        """Get the current voltage."""
        return self._voltage

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

print(Parrot().voltage)
try:
    Parrot().voltage = 99999
except Exception as e:
    print(e)

c = C()
c.x = 1234567
print(c.x)
print(c._x)
try:
    del c.x
    print(c._x)
except Exception as e:
    print(e)