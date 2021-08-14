def perceptron_and(a, b):
    weight1, weight2, threshold = 0.5, 0.5, 0.5
    return a * weight1 + b * weight2 > threshold


def perceptron_nand(a, b):
    weight1, weight2, threshold = 0.5, 0.5, 0.5
    return a * weight1 + b * weight2 <= threshold


def perceptron_or(a, b):
    weight1, weight2, threshold = 0.5, 0.5, 0.5
    return a * weight1 + b * weight2 >= threshold


def perceptron_xor(a, b):
    return perceptron_and(perceptron_nand(a, b), perceptron_or(a, b))


assert perceptron_xor(0, 0) == False
assert perceptron_xor(1, 0) == True
assert perceptron_xor(1, 1) == False
