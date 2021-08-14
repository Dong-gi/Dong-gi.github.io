import numpy
import matplotlib.pyplot as plot


def step(arr):
    return numpy.array(arr > 0, dtype=numpy.integer)


x = numpy.arange(-5, 5, 0.1)
y = step(x)
plot.plot(x, y, label="Step function")
plot.xlabel('x')
plot.ylabel('y')
plot.show()
