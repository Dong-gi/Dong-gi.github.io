import numpy as np
import numbers







arr = np.array([
    [[ 1, 2, 3], [ 4, 5, 6], [ 7, 8, 9]],
    [[10,11,12], [13,14,15], [16,17,18]],
    [[19,20,21], [22,23,24], [25,26,27]],
])

print(arr)
print(arr[:, :, :])
print(arr[...])
'''
[[[ 1  2  3]
  [ 4  5  6]
  [ 7  8  9]]

 [[10 11 12]
  [13 14 15]
  [16 17 18]]

 [[19 20 21]
  [22 23 24]
  [25 26 27]]]
'''

print(arr[:1, :, :])
print(arr[:1, ...])
'''
[[[1 2 3]
  [4 5 6]
  [7 8 9]]]
'''


print(isinstance(True, numbers.Number)) # True
print(isinstance(True, numbers.Integral)) # True
print(isinstance(True, int)) # True
print(isinstance(True, bool)) # True
print((True + True) * (True + True)) # 4
print(False == 0) # True