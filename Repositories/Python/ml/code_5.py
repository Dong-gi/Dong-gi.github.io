import numpy
import matplotlib.pyplot as plot


def relu(arr):
    return numpy.maximum(0, arr)


x = numpy.arange(-10, 10, 0.1)
y = relu(x)
plot.plot(x, y, label="Sigmoid function")
plot.xlabel('x')
plot.ylabel('y')
plot.show()
