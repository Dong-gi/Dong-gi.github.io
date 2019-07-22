import collections

Point = collections.namedtuple("Point", "x, y")
print(Point._make([1, 2]))
print(Point(x=2, y=4)._asdict())
print(Point(3, 3)._replace(x=4))

source = Point._source
del Point
try:
    print(Point(1, 2))
except Exception as e:
    print(e)

exec(compile(source, "<string>", "exec"))
print(Point(1, 2))
