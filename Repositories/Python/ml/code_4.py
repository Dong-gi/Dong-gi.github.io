import numpy
import matplotlib.pyplot as plot


def sigmoid(arr):
    return 1 / (1 + numpy.exp(-arr))


x = numpy.arange(-10, 10, 0.1)
y = sigmoid(x)
plot.plot(x, y, label="Sigmoid function")
plot.xlabel('x')
plot.ylabel('y')
plot.show()
