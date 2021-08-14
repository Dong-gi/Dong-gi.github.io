import numpy


def perceptron_and1(a, b):
    weight1, weight2, threshold = 0.5, 0.5, 0.5
    return a * weight1 + b * weight2 > threshold


assert perceptron_and1(0, 0) == False
assert perceptron_and1(1, 0) == False
assert perceptron_and1(1, 1) == True


def perceptron_and2(a, b):
    input = numpy.array([a, b])
    weight = numpy.array([0.5, 0.5])
    threshold = 0.5
    return numpy.sum(input * weight) > threshold


assert perceptron_and2(0, 0) == False
assert perceptron_and2(1, 0) == False
assert perceptron_and2(1, 1) == True
